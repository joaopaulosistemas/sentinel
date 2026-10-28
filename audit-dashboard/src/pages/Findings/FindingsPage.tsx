import { InlineNotification } from '@carbon/react';
import { PageShell } from '../../components/PageShell';
import { useQueries } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ClaudeReviewPanel } from '../../components/ClaudeReviewPanel';
import { ReauditPanel } from '../../components/ReauditPanel';
import { api } from '../../api/client';
import { useAudit } from '../../hooks/useAuditRuns';
import { useFindings, useFindingsOverview } from '../../hooks/useFindings';
import { useDiff, useFreshness } from '../../hooks/useLifecycle';
import { useSystems } from '../../hooks/useSystems';
import { useAuditStore } from '../../store/useAuditStore';
import type { AuditRunResponse, Category, SystemSummaryResponse } from '../../types';
import {
  DEFAULT_SEVERITY,
  DEFAULT_SITUATION,
  activeFilterChips,
  footerText,
  severityChoices,
  situationChoices,
  type SeverityChoice,
  type SituationChoice,
} from '../../utils/findingsView';
import { FindingsFilters, PaginationFooter } from './FindingsFilters';
import { claudeNoteFinder, findingsCrumbs, pickRows, reviewSystemIds } from './findingsRows';
import { FindingsListBody, PageHeading, ResolvedSection, SystemSummaryCards, TabBar, originFinder, type AbaId } from './FindingsParts';

/** `?systemId=` válido (inteiro positivo) ou nada. */
function linkedSystemId(raw: string | null): number | undefined {
  const value = Number(raw ?? '0');
  return value > 0 ? value : undefined;
}

/** Sistema da auditoria aberta: pelo id, ou pelo nome quando a resposta ainda não traz o id. */
function findAuditSystemId(audit: AuditRunResponse | undefined, systems: SystemSummaryResponse[]): number | undefined {
  return audit?.systemId ?? systems.find((s) => s.name === audit?.systemName)?.id;
}

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
  const [systemId, setSystemId] = useState<number | undefined>(() => linkedSystemId(searchParams.get('systemId')));
  const [severity, setSeverity] = useState<SeverityChoice>(DEFAULT_SEVERITY);
  const [category, setCategory] = useState<Category | undefined>(undefined);
  const [situation, setSituation] = useState<SituationChoice>(searchParams.get('onlyNew') === '1' ? 'new' : DEFAULT_SITUATION);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [grouped, setGrouped] = useState(false);
  const [showResolved, setShowResolved] = useState(false);
  const [showLow, setShowLow] = useState(false);
  const [aba, setAba] = useState<AbaId>(searchParams.get('aba') === 'reauditoria' ? 'reauditoria' : 'achados');

  const { data: systems } = useSystems();
  const { data: audit } = useAudit(auditId);
  // Os resolvidos não estão na lista da auditoria (já não existem nela): vêm da comparação com a anterior.
  const { data: diff } = useDiff(auditMode ? auditId : undefined);
  const { data: freshness } = useFreshness(auditMode ? auditId : undefined);
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

  const current = auditMode ? single : overview;
  const { isLoading: loading, error } = current;

  const { rows, total, pages, lowHidden, lowTotal } = pickRows({
    auditMode,
    single: single.data ?? [],
    severities: sev,
    showLow,
    page,
    pageSize,
    overview: overview.data,
  });

  const itemByFinding = new Map(overview.data?.items.map((i) => [i.finding.id, i]) ?? []);
  const summary = overview.data?.bySystem ?? [];
  const stackByName = new Map(summary.map((s) => [s.systemId, s.stack]));

  const reset = () => setPage(1);
  const systemName = summary.find((b) => b.systemId === systemId)?.systemName ?? '';

  const chips = activeFilterChips({ systemName, showSystem: !auditMode, severity, category, situation });
  const isDefault = severity === DEFAULT_SEVERITY && situation === DEFAULT_SITUATION && category === undefined && systemId === undefined;

  const systemOf = auditMode
    ? undefined
    : originFinder(itemByFinding, stackByName, (hit) => {
        setSelectedAudit(hit.auditId);
        navigate(`/systems/${hit.systemId}`);
      });

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
  const auditSystemId = auditMode ? findAuditSystemId(audit, systems ?? []) : undefined;
  const activeSystemId = auditMode ? auditSystemId : systemId;
  // Sugestões abertas do Claude, para marcar na lista os achados de que elas falam (mesma consulta da aba Revisão).
  const reviewIds = reviewSystemIds(auditMode, auditSystemId, systemId, [...itemByFinding.values()]);
  const reviews = useQueries({
    queries: reviewIds.map((id) => ({ queryKey: ['claude-review', id], queryFn: () => api.getClaudeReview(id), staleTime: 30_000 })),
  });
  const suggestionsBySystem = new Map(reviewIds.map((id, i) => [id, reviews[i]?.data?.suggestions ?? []]));
  const claudeNoteOf = claudeNoteFinder(auditMode, auditSystemId, systemId, itemByFinding, suggestionsBySystem);
  const currentSystemValue = String(activeSystemId ?? '');
  const sysCount = auditMode ? 1 : summary.length;

  return (
    <PageShell crumbs={findingsCrumbs(auditMode, audit, auditId)}>
      <PageHeading auditMode={auditMode} auditId={auditId} systemName={audit?.systemName} />

      <TabBar aba={aba} onSelect={setAba} />

      {aba === 'revisao' ? (
        <ClaudeReviewPanel key={activeSystemId ?? 'sem-sistema'} systems={systems ?? []} initialSystemId={activeSystemId} />
      ) : aba === 'reauditoria' ? (
        <ReauditPanel key={activeSystemId ?? 'sem-sistema'} systems={systems ?? []} initialSystemId={activeSystemId} />
      ) : (
        <>
      {auditMode && freshness?.stale === true && (
        <InlineNotification
          kind="warning"
          title="Auditoria desatualizada"
          subtitle={`A branch mudou desde esta auditoria (auditado ${freshness.auditedCommit?.slice(0, 7)}, atual ${freshness.currentCommit?.slice(0, 7)}). Achados já corrigidos podem aparecer aqui; reaudite para conferir.`}
          hideCloseButton
        />
      )}
      <FindingsFilters
        auditMode={auditMode}
        systems={systems}
        currentSystemValue={currentSystemValue}
        onPickSystem={pickSystem}
        severity={severity}
        onSeverity={(v) => { setSeverity(v); reset(); }}
        category={category}
        onCategory={(v) => { setCategory(v); reset(); }}
        situation={situation}
        onSituation={(v) => { setSituation(v); reset(); }}
        grouped={grouped}
        onGrouped={setGrouped}
        lowTotal={lowTotal}
        lowHidden={lowHidden}
        showLow={showLow}
        onShowLow={(v) => { setShowLow(v); reset(); }}
        chips={chips}
        isDefault={isDefault}
        onRestore={restoreDefaults}
      />

      {!auditMode && summary.length > 0 && <SystemSummaryCards summary={summary} />}

      <section aria-label="Lista de findings" style={{ background: 'var(--s-surface)', border: '1px solid var(--s-border)' }}>
        <FindingsListBody
          loading={loading}
          error={error}
          total={total}
          auditMode={auditMode}
          auditId={auditId}
          auditSystemName={audit?.systemName}
          grouped={grouped}
          rows={rows}
          items={overview.data?.items ?? []}
          systemOf={systemOf}
          claudeNoteOf={claudeNoteOf}
        />
        {!loading && !error && total > 0 && (
          <PaginationFooter text={footerText(rows.length, total, situation, severity, sysCount)} page={page} pages={pages} pageSize={pageSize} onPage={setPage} onPageSize={(n) => { setPageSize(n); setPage(1); }} />
        )}
      </section>

      {auditMode && resolved.length > 0 && (
        <ResolvedSection
          resolved={resolved}
          open={showResolved}
          onToggle={() => setShowResolved(!showResolved)}
          systemName={audit?.systemName}
          auditId={auditId}
          claudeNoteOf={claudeNoteOf}
        />
      )}
        </>
      )}
    </PageShell>
  );
}
