import { DataTableSkeleton, InlineNotification } from '@carbon/react';
import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { ClaudeReviewPanel } from '../../components/ClaudeReviewPanel';
import { ReauditPanel } from '../../components/ReauditPanel';
import { FindingsTable, type FindingOrigin } from '../../components/FindingsTable';
import { useAudit } from '../../hooks/useAuditRuns';
import { useFindings, useFindingsOverview } from '../../hooks/useFindings';
import { useDiff } from '../../hooks/useLifecycle';
import { useSystems } from '../../hooks/useSystems';
import { useAuditStore } from '../../store/useAuditStore';
import type { Category, FindingResponse, Severity } from '../../types';
import {
  DEFAULT_SEVERITY,
  DEFAULT_SITUATION,
  activeFilterChips,
  footerText,
  groupBySystem,
  severityChoices,
  situationChoices,
  type SeverityChoice,
  type SituationChoice,
} from '../../utils/findingsView';
import { startLabel } from '../../utils/timeAgo';
import { stackShort } from '../Systems/systemForm';

const categoryOptions: Array<{ value: Category | ''; label: string }> = [
  { value: '', label: 'Todas' },
  { value: 'SECURITY', label: 'Segurança' },
  { value: 'QUALITY', label: 'Qualidade' },
  { value: 'ARCHITECTURE', label: 'Arquitetura' },
  { value: 'DEPENDENCY', label: 'Dependências' },
  { value: 'OBSERVABILITY', label: 'Observabilidade' },
  { value: 'PERFORMANCE', label: 'Desempenho' },
];

const ABAS = [
  { id: 'achados', nome: 'Achados e nota', ativa: true },
  { id: 'revisao', nome: 'Revisão do Claude', ativa: true },
  { id: 'reauditoria', nome: 'Reauditoria', ativa: true },
];

type AbaId = 'achados' | 'revisao' | 'reauditoria';

const label = { fontSize: 12, color: 'var(--s-text-3)' } as const;
const select = { height: 40, padding: '0 8px', border: 0, borderBottom: '1px solid var(--s-border-strong)', background: 'var(--s-layer)', fontSize: 14 } as const;
const mono = "'IBM Plex Mono', ui-monospace, monospace";

/** Cor da borda do cartão do sistema: vermelho com críticos (ou muitos altos), laranja só com altos, verde sem nenhum. */
const edgeColor = (critical: number, high: number) => (critical > 0 || high >= 50 ? '#da1e28' : high > 0 ? '#ff832b' : '#198038');

/**
 * Achados. Sem auditoria na URL, mostra os de todos os sistemas (última auditoria concluída de cada um) e deixa
 * filtrar por sistema; com `?auditId=`, mostra os daquela auditoria.
 */
export function FindingsPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const auditId = Number(searchParams.get('auditId') ?? '0');
  const setSelectedAudit = useAuditStore((s) => s.setSelectedAudit);
  const auditMode = auditId > 0;

  // Com ?aba=reauditoria&systemId=ID (faixa do Claude na execução da auditoria), abre direto nos pedidos desse sistema.
  const linkedSystem = Number(searchParams.get('systemId') ?? '0');
  const [systemId, setSystemId] = useState<number | undefined>(linkedSystem > 0 ? linkedSystem : undefined);
  const [severity, setSeverity] = useState<SeverityChoice>(DEFAULT_SEVERITY);
  const [category, setCategory] = useState<Category | undefined>(undefined);
  const [situation, setSituation] = useState<SituationChoice>(searchParams.get('onlyNew') === '1' ? 'new' : DEFAULT_SITUATION);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [grouped, setGrouped] = useState(false);
  const [showResolved, setShowResolved] = useState(false);
  const [aba, setAba] = useState<AbaId>(searchParams.get('aba') === 'reauditoria' ? 'reauditoria' : 'achados');

  const { data: systems } = useSystems();
  const { data: audit } = useAudit(auditId);
  // Os resolvidos não estão na lista da auditoria (já não existem nela): vêm da comparação com a anterior.
  const { data: diff } = useDiff(auditMode ? auditId : undefined);
  const resolved = (diff?.resolvedFindings ?? []).map((f) => ({ ...f, status: 'FIXED' as const }));

  const sev = severityChoices.find((c) => c.id === severity)?.severities ?? [];
  const sit = situationChoices.find((c) => c.id === situation) ?? situationChoices[0];
  const single = useFindings(auditId, {
    severity: sev.length === 1 ? sev[0] : undefined,
    category,
    status: sit.status,
    onlyNew: sit.onlyNew,
    includeSuppressed: sit.includeSuppressed,
  });
  const overview = useFindingsOverview(
    {
      severity: sev.length > 0 ? sev : undefined,
      category,
      status: sit.status,
      onlyNew: sit.onlyNew,
      includeSuppressed: sit.includeSuppressed,
      systemId,
      limit: pageSize,
      offset: (page - 1) * pageSize,
    },
    !auditMode,
  );

  const loading = auditMode ? single.isLoading : overview.isLoading;
  const error = auditMode ? single.error : overview.error;

  // Uma auditoria só: várias severidades (ex.: crítica e alta) são filtradas aqui, a API recebe uma por vez.
  const singleRows = (single.data ?? []).filter((f) => sev.length === 0 || sev.includes(f.severity as Severity));
  const rows: FindingResponse[] = auditMode
    ? singleRows.slice((page - 1) * pageSize, page * pageSize)
    : (overview.data?.items.map((i) => i.finding) ?? []);
  const total = auditMode ? singleRows.length : (overview.data?.total ?? 0);
  const pages = Math.max(1, Math.ceil(total / pageSize));

  const itemByFinding = new Map(overview.data?.items.map((i) => [i.finding.id, i]) ?? []);
  const summary = overview.data?.bySystem ?? [];
  const stackByName = new Map(summary.map((s) => [s.systemId, s.stack]));

  const reset = () => setPage(1);
  const systemName = systemId !== undefined ? (summary.find((b) => b.systemId === systemId)?.systemName ?? '') : '';

  const chips = activeFilterChips({ systemName, showSystem: !auditMode, severity, category, situation });
  const isDefault = severity === DEFAULT_SEVERITY && situation === DEFAULT_SITUATION && category === undefined && systemId === undefined;

  const systemOf = auditMode
    ? undefined
    : (f: FindingResponse): FindingOrigin | undefined => {
        const hit = itemByFinding.get(f.id);
        if (!hit) return undefined;
        const stack = stackByName.get(hit.systemId);
        return {
          name: hit.systemName,
          stack: stack ? (stackShort[stack as keyof typeof stackShort] ?? stack) : undefined,
          auditId: hit.auditId,
          onClick: () => {
            setSelectedAudit(hit.auditId);
            navigate(`/systems/${hit.systemId}`);
          },
        };
      };

  const pickSystem = (value: number | undefined) => {
    // Escolher um sistema sai do modo "uma auditoria" e volta a olhar a última de cada sistema.
    if (auditMode) setSearchParams({});
    setSystemId(value);
    reset();
  };

  const restoreDefaults = () => {
    setSeverity(DEFAULT_SEVERITY);
    setSituation(DEFAULT_SITUATION);
    setCategory(undefined);
    setSystemId(undefined);
    reset();
  };

  // Sistema em foco: o da auditoria aberta (vindo de "Ver todos os achados") ou o escolhido no filtro.
  // As três abas partem dele, então quem escolheu o sistema não precisa escolher de novo.
  const auditSystemId = auditMode
    ? (audit?.systemId ?? (systems ?? []).find((s) => s.name === audit?.systemName)?.id)
    : undefined;
  const activeSystemId = auditMode ? auditSystemId : systemId;
  const currentSystemValue = activeSystemId !== undefined ? String(activeSystemId) : '';
  const sysCount = auditMode ? 1 : summary.length;

  return (
    <div style={{ padding: '1.5rem 2rem 3rem', maxWidth: 1440, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 20 }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        <h1 style={{ margin: 0, fontSize: 28, fontWeight: 400 }}>
          Informações de Auditoria
          {auditMode && (
            <span style={{ fontSize: 16, color: 'var(--s-text-4)', marginLeft: 12 }}>
              — {audit?.systemName ? `${audit.systemName} · ` : ''}Auditoria #{auditId}
            </span>
          )}
        </h1>
        <span style={{ color: 'var(--s-text-3)' }}>
          {auditMode
            ? 'Achados desta auditoria.'
            : 'Da última auditoria concluída de cada sistema. Escolha um sistema para focar, ou veja todos juntos e saiba de onde cada achado vem.'}
        </span>
      </div>

      <div role="tablist" aria-label="Seções de Informações de Auditoria" style={{ display: 'flex', borderBottom: '1px solid var(--s-border-subtle)' }}>
        {ABAS.map((a) => (
          <button
            key={a.id}
            type="button"
            role="tab"
            id={`aba-${a.id}`}
            aria-selected={a.id === aba}
            aria-disabled={!a.ativa}
            onClick={() => a.ativa && setAba(a.id as AbaId)}
            disabled={!a.ativa}
            title={a.ativa ? undefined : 'Chega em uma próxima fase'}
            style={{
              background: 'none',
              border: 0,
              borderBottom: a.id === aba ? '3px solid #0f62fe' : '3px solid transparent',
              padding: '10px 16px',
              fontSize: 14,
              fontWeight: a.id === aba ? 600 : 400,
              color: a.ativa ? 'var(--s-text)' : 'var(--s-disabled)',
              cursor: a.ativa ? 'pointer' : 'not-allowed',
            }}
          >
            {a.nome}
          </button>
        ))}
      </div>

      {aba === 'revisao' ? (
        <ClaudeReviewPanel key={activeSystemId ?? 'sem-sistema'} systems={systems ?? []} initialSystemId={activeSystemId} />
      ) : aba === 'reauditoria' ? (
        <ReauditPanel key={activeSystemId ?? 'sem-sistema'} systems={systems ?? []} initialSystemId={activeSystemId} />
      ) : (
        <>
      <section aria-label="Filtros" style={{ background: 'var(--s-surface)', border: '1px solid var(--s-border)', padding: 16, display: 'flex', flexDirection: 'column', gap: 14 }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))', gap: 14, alignItems: 'end' }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <label htmlFor="f-sis" style={{ ...label, fontWeight: 600 }}>Sistema</label>
            <select
              id="f-sis"
              value={currentSystemValue}
              onChange={(e) => pickSystem(e.target.value === '' ? undefined : Number(e.target.value))}
              style={{ ...select, borderBottom: '2px solid #0f62fe' }}
            >
              <option value="">Todos os sistemas{systems ? ` (${systems.length})` : ''}</option>
              {(systems ?? []).map((s) => (
                <option key={s.id} value={s.id}>{s.name}</option>
              ))}
            </select>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <label htmlFor="f-sev" style={label}>Severidade</label>
            <select id="f-sev" value={severity} onChange={(e) => { setSeverity(e.target.value as SeverityChoice); reset(); }} style={select}>
              {severityChoices.map((c) => (
                <option key={c.id} value={c.id}>{c.label}</option>
              ))}
            </select>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <label htmlFor="f-cat" style={label}>Categoria</label>
            <select id="f-cat" value={category ?? ''} onChange={(e) => { setCategory((e.target.value || undefined) as Category | undefined); reset(); }} style={select}>
              {categoryOptions.map((c) => (
                <option key={c.value} value={c.value}>{c.label}</option>
              ))}
            </select>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <label htmlFor="f-st" style={label}>Situação</label>
            <select id="f-st" value={situation} onChange={(e) => { setSituation(e.target.value as SituationChoice); reset(); }} style={select}>
              {situationChoices.map((c) => (
                <option key={c.id} value={c.id}>{c.label}</option>
              ))}
            </select>
          </div>
          {!auditMode && (
            <label style={{ display: 'flex', alignItems: 'center', gap: 10, height: 40 }}>
              <input type="checkbox" checked={grouped} onChange={(e) => setGrouped(e.target.checked)} style={{ width: 18, height: 18, accentColor: '#0f62fe' }} />
              Agrupar por sistema
            </label>
          )}
        </div>
        <div aria-label="Filtros ativos" style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center', fontSize: 13, color: 'var(--s-text-3)' }}>
          Filtros ativos:
          {chips.map((c) => (
            <span key={c.key} style={{ background: c.tone === 'blue' ? '#d0e2ff' : 'var(--s-border)', color: c.tone === 'blue' ? '#0043ce' : 'var(--s-text)', padding: '3px 10px', borderRadius: 12 }}>
              {c.label}
            </span>
          ))}
          {!isDefault && (
            <button type="button" onClick={restoreDefaults} style={{ background: 'none', border: 0, color: 'var(--s-link)', cursor: 'pointer', fontSize: 13, padding: '0 4px' }}>
              Restaurar padrão
            </button>
          )}
        </div>
      </section>

      {!auditMode && summary.length > 0 && (
        <section aria-label="Resumo por sistema" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 12 }}>
          {summary.map((s) => (
            <Link
              key={s.systemId}
              to={`/systems/${s.systemId}`}
              style={{ textDecoration: 'none', color: 'inherit', background: 'var(--s-surface)', border: '1px solid var(--s-border)', borderLeft: `4px solid ${edgeColor(s.critical ?? 0, s.high ?? 0)}`, padding: '12px 16px', display: 'flex', flexDirection: 'column', gap: 6, minWidth: 0 }}
            >
              <span style={{ fontWeight: 600, color: 'var(--s-text)' }}>{s.systemName}</span>
              <span style={{ fontSize: 12, color: 'var(--s-text-3)' }}>
                Auditoria #{s.auditId}{s.auditedAt ? ` · ${startLabel(s.auditedAt)}` : ''}
              </span>
              <span style={{ display: 'flex', gap: 14, fontSize: 13, color: 'var(--s-text)' }}>
                <span><strong style={{ fontFamily: mono, color: 'var(--s-crit-text)' }}>{s.critical ?? 0}</strong> críticos</span>
                <span><strong style={{ fontFamily: mono, color: 'var(--s-high-text)' }}>{s.high ?? 0}</strong> altos</span>
              </span>
            </Link>
          ))}
        </section>
      )}

      <section aria-label="Lista de findings" style={{ background: 'var(--s-surface)', border: '1px solid var(--s-border)' }}>
        {loading ? (
          <div style={{ padding: 16 }}><DataTableSkeleton columnCount={6} rowCount={8} /></div>
        ) : error ? (
          <div style={{ padding: 16 }}>
            <InlineNotification kind="error" title="Erro ao carregar findings" subtitle={error instanceof Error ? error.message : 'Erro desconhecido'} />
          </div>
        ) : total === 0 ? (
          <div style={{ padding: 16 }}>
            <InlineNotification
              kind="info"
              title="Nenhum achado com esses filtros"
              subtitle={auditMode ? 'Tente remover algum filtro.' : 'Pode ser que nenhum sistema tenha auditoria concluída ainda, ou que os filtros sejam restritivos.'}
              hideCloseButton
            />
          </div>
        ) : grouped && !auditMode ? (
          <div style={{ display: 'grid', gap: 24, padding: '16px 0 0' }}>
            {groupBySystem(overview.data?.items ?? []).map((g) => (
              <section key={g.systemId} aria-label={`Achados de ${g.systemName}`}>
                <h2 style={{ fontSize: 16, fontWeight: 600, margin: '0 16px 8px' }}>
                  {g.systemName}
                  <span style={{ fontWeight: 400, color: 'var(--s-text-3)', marginLeft: 8 }}>
                    {g.findings.length} {g.findings.length === 1 ? 'achado nesta página' : 'achados nesta página'}
                  </span>
                </h2>
                <FindingsTable findings={g.findings} systemOf={systemOf} />
              </section>
            ))}
          </div>
        ) : (
          <FindingsTable findings={rows} systemName={auditMode ? audit?.systemName : undefined} auditId={auditMode ? auditId : undefined} systemOf={systemOf} />
        )}
        {!loading && !error && total > 0 && (
          <div style={{ padding: '12px 16px', display: 'flex', justifyContent: 'space-between', fontSize: 13, color: 'var(--s-text-3)', borderTop: '1px solid var(--s-border)', flexWrap: 'wrap', gap: 8, alignItems: 'center' }}>
            <span>{footerText(rows.length, total, situation, severity, sysCount)}</span>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 12 }}>
              <label style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                Por página
                <select aria-label="Itens por página" value={pageSize} onChange={(e) => { setPageSize(Number(e.target.value)); setPage(1); }} style={{ ...select, height: 32 }}>
                  {[10, 20, 50].map((n) => (
                    <option key={n} value={n}>{n}</option>
                  ))}
                </select>
              </label>
              <span>Página {page} de {pages}</span>
              <button type="button" aria-label="Página anterior" disabled={page <= 1} onClick={() => setPage(page - 1)} style={{ border: '1px solid var(--s-border-strong)', background: 'var(--s-surface)', width: 32, height: 32, cursor: page <= 1 ? 'not-allowed' : 'pointer' }}>‹</button>
              <button type="button" aria-label="Próxima página" disabled={page >= pages} onClick={() => setPage(page + 1)} style={{ border: '1px solid var(--s-border-strong)', background: 'var(--s-surface)', width: 32, height: 32, cursor: page >= pages ? 'not-allowed' : 'pointer' }}>›</button>
            </span>
          </div>
        )}
      </section>

      {auditMode && resolved.length > 0 && (
        <section aria-label="Resolvidos">
          <button
            type="button"
            aria-expanded={showResolved}
            onClick={() => setShowResolved(!showResolved)}
            style={{ height: 36, padding: '0 14px', background: 'var(--s-surface)', border: '1px solid #0f62fe', color: 'var(--s-link)', cursor: 'pointer', fontSize: 13 }}
          >
            {showResolved ? 'Ocultar' : 'Mostrar'} {resolved.length} {resolved.length === 1 ? 'achado resolvido' : 'achados resolvidos'} desde a auditoria anterior
          </button>
          {showResolved && (
            <div style={{ marginTop: 16, background: 'var(--s-surface)', border: '1px solid var(--s-border)' }}>
              <FindingsTable findings={resolved} systemName={audit?.systemName} auditId={auditId} />
            </div>
          )}
        </section>
      )}
        </>
      )}
    </div>
  );
}
