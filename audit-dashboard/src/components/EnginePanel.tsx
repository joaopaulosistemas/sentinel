import { Button, Checkbox, InlineNotification } from '@carbon/react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useRef, useState } from 'react';
import { api } from '../api/client';
import { useAuditRuns, useFixPlan } from '../hooks/useAuditRuns';
import type { EngineConfig, EngineTuning, RuleAdjustment, SystemSummaryResponse } from '../types';
import {
  applyBulk,
  buildRuleRows,
  estimatedGain,
  filterRuleRows,
  isNoopAdjustment,
  type BulkAction,
  type RuleScope,
} from '../utils/ruleCatalog';

const mono = "'IBM Plex Mono', ui-monospace, monospace";
const SEVERITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'] as const;
const THRESHOLD_LABEL: Record<string, string> = {
  'max-function-lines': 'Linhas por função',
  'max-component-lines': 'Linhas por componente',
  'max-parameters': 'Parâmetros por função',
  'max-complexity': 'Complexidade',
  'max-nesting': 'Aninhamento',
  'max-findings': 'Achados por regra de clean code',
};

/** Soma o que o sistema mudou aos ajustes que o servidor já tem, sem perder o que a outra aba salvou. */
export function mergeTuning(server: EngineTuning, slice: Partial<EngineTuning>): EngineTuning {
  return { ...server, ...slice };
}

/** Regra sem nenhum efeito não é ajuste. */
export const isNoop = isNoopAdjustment;

function useEngine(systemId: number) {
  const queryClient = useQueryClient();
  const key = ['engine', systemId];
  const query = useQuery({ queryKey: key, queryFn: () => api.getEngineConfig(systemId) });
  const save = useMutation({
    mutationFn: (body: EngineTuning) => api.updateEngineConfig(systemId, body),
    onSuccess: (data) => {
      queryClient.setQueryData(key, data);
      queryClient.invalidateQueries({ queryKey: ['systems'] });
    },
  });
  return { query, save };
}

function Header({ cfg, onReset, busy }: { cfg: EngineConfig; onReset: () => void; busy: boolean }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap', alignItems: 'center' }}>
      <div style={{ fontSize: 14 }}>
        <span style={{ color: 'var(--s-text-3)' }}>Perfil:</span> <strong>{cfg.profile}</strong>
        {cfg.profileDescription && <div style={{ fontSize: 12, color: 'var(--s-text-3)' }}>{cfg.profileDescription}</div>}
      </div>
      <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
        <span role="status" style={{ fontSize: 13, color: cfg.adjustments > 0 ? '#0043ce' : 'var(--s-text-3)' }}>
          {cfg.adjustments === 0 ? 'Igual ao perfil' : `${cfg.adjustments} ${cfg.adjustments === 1 ? 'ajuste' : 'ajustes'} em relação ao perfil`}
        </span>
        {cfg.adjustments > 0 && (
          <Button kind="ghost" size="sm" disabled={busy} onClick={onReset}>
            Voltar ao perfil
          </Button>
        )}
      </div>
    </div>
  );
}

const empty: EngineTuning = { rules: [], thresholds: {}, enablePacks: [], disablePacks: [], disableAnalyzers: [] };

const PAGE = 25;
const fieldStyle = { height: 36, padding: '0 8px', border: 0, borderBottom: '1px solid var(--s-border-strong)', background: 'var(--s-layer)', color: 'var(--s-text)' } as const;

/** Forma comparável do que está na tela, para saber se há alteração sem salvar. */
function signature(slice: Pick<EngineTuning, 'enablePacks' | 'disablePacks' | 'thresholds' | 'rules'>): string {
  return JSON.stringify({
    enablePacks: [...slice.enablePacks].sort(),
    disablePacks: [...slice.disablePacks].sort(),
    thresholds: Object.entries(slice.thresholds).sort(([a], [b]) => a.localeCompare(b)),
    rules: slice.rules.filter((r) => !isNoopAdjustment(r)).map((r) => ({ id: r.id, severity: r.severity, disabled: r.disabled, shadow: r.shadow })).sort((a, b) => a.id.localeCompare(b.id)),
  });
}

interface PanelProps {
  system: SystemSummaryResponse;
  /** Avisa quem abriu o painel quando há (ou deixa de haver) alteração sem salvar. */
  onDirtyChange?: (dirty: boolean) => void;
}

/** Aba "Motor de regras": pacotes, limites e regras deste sistema, por cima do perfil. A nota continua sendo do motor. */
export function EngineRulesPanel({ system, onDirtyChange }: PanelProps) {
  const { query, save } = useEngine(system.id);
  const cfg = query.data;
  const [packs, setPacks] = useState<Record<string, boolean>>({});
  const [limits, setLimits] = useState<Record<string, string>>({});
  const [rules, setRules] = useState<RuleAdjustment[]>([]);
  const [newRule, setNewRule] = useState('');
  const [text, setText] = useState('');
  const [scope, setScope] = useState<RuleScope>('all');
  const [packFilter, setPackFilter] = useState('');
  const [visible, setVisible] = useState(PAGE);
  const [picked, setPicked] = useState<Set<string>>(new Set());

  // Efeito de cada regra na nota: vem do plano de correção da última auditoria concluída.
  const runs = useAuditRuns(system.id);
  const runList = Array.isArray(runs.data) ? runs.data : [];
  const latest = runList.filter((r) => r.status === 'COMPLETED').sort((a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime())[0];
  const fixPlan = useFixPlan(latest?.id ?? 0, latest !== undefined);
  const planItems = Array.isArray(fixPlan.data?.items) ? fixPlan.data.items : [];

  const syncFrom = (data: EngineConfig) => {
    setPacks(Object.fromEntries(data.packs.map((p) => [p.id, p.active])));
    setLimits(Object.fromEntries(Object.entries(data.tuning.thresholds).map(([k, v]) => [k, String(v)])));
    setRules(data.tuning.rules);
    setPicked(new Set());
  };

  const sliceOf = (data: EngineConfig): Pick<EngineTuning, 'enablePacks' | 'disablePacks' | 'thresholds' | 'rules'> => {
    const enablePacks = data.packs.filter((p) => packs[p.id] && !p.inProfile).map((p) => p.id);
    const disablePacks = data.packs.filter((p) => !packs[p.id] && p.inProfile).map((p) => p.id);
    const thresholds: Record<string, number> = {};
    for (const [k, v] of Object.entries(limits)) {
      const n = Number(v);
      if (v.trim() !== '' && Number.isInteger(n) && n >= 1) thresholds[k] = n;
    }
    return { enablePacks, disablePacks, thresholds, rules: rules.filter((r) => !isNoopAdjustment(r)) };
  };

  const dirty = useMemo(() => {
    if (!cfg || Object.keys(packs).length === 0) return false;
    const saved = { enablePacks: cfg.tuning.enablePacks, disablePacks: cfg.tuning.disablePacks, thresholds: cfg.tuning.thresholds, rules: cfg.tuning.rules };
    return signature(sliceOf(cfg)) !== signature(saved);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cfg, packs, limits, rules]);

  const dirtyRef = useRef(false);
  dirtyRef.current = dirty;
  useEffect(() => {
    onDirtyChange?.(dirty);
  }, [dirty, onDirtyChange]);

  // Só recarrega a tela com o que veio do servidor se não houver edição em andamento.
  useEffect(() => {
    if (!cfg || dirtyRef.current) return;
    syncFrom(cfg);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cfg]);

  const allRows = useMemo(
    () =>
      cfg
        ? buildRuleRows({ catalog: cfg.catalog, known: cfg.knownRules, profileRules: cfg.profileRules, adjusted: rules, plan: planItems })
        : [],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [cfg, rules, fixPlan.data],
  );

  if (query.isLoading) return <div style={{ padding: 24 }}>Carregando…</div>;
  if (query.isError || !cfg) {
    return (
      <div style={{ padding: 24 }}>
        <InlineNotification kind="error" title="Não foi possível carregar" subtitle={query.error instanceof Error ? query.error.message : 'Erro desconhecido'} hideCloseButton />
      </div>
    );
  }

  const server = cfg.tuning;
  const profileRule = (id: string) => cfg.profileRules.find((r) => r.id === id);
  const adjustedIds = new Set(rules.filter((r) => !isNoopAdjustment(r)).map((r) => r.id));
  const current = (id: string): RuleAdjustment => rules.find((r) => r.id === id) ?? { id, severity: null, disabled: false, shadow: null };
  const setRule = (id: string, patch: Partial<RuleAdjustment>) => {
    const next = { ...current(id), ...patch };
    setRules([...rules.filter((r) => r.id !== id), next]);
  };

  const packNames = Array.from(new Set(allRows.map((r) => r.pack).filter((p): p is string => !!p))).sort();
  const filtered = filterRuleRows(allRows, { query: text, scope, pack: packFilter }, adjustedIds);
  const shown = filtered.slice(0, visible);
  const pickedIds = [...picked];
  const gainPicked = estimatedGain(allRows, pickedIds);
  const catalogMissing = (cfg.catalog ?? []).length === 0;

  const limitError = (k: string) => limits[k] !== undefined && limits[k].trim() !== '' && !(Number.isInteger(Number(limits[k])) && Number(limits[k]) >= 1);
  const invalid = Object.keys(limits).some(limitError);
  const addRule = () => {
    const id = newRule.trim().toLowerCase();
    if (!id || !/^[a-z0-9_.*:/-]+$/.test(id)) return;
    if (!rules.some((r) => r.id === id)) setRules([...rules, { id, severity: null, disabled: false, shadow: true }]);
    setNewRule('');
  };
  const bulk = (action: BulkAction) => setRules(applyBulk(rules, pickedIds, action));
  const toggle = (id: string, on: boolean) => {
    const next = new Set(picked);
    if (on) next.add(id);
    else next.delete(id);
    setPicked(next);
  };
  const allShownPicked = shown.length > 0 && shown.every((r) => picked.has(r.id));

  const submit = () =>
    save.mutate(mergeTuning(server, sliceOf(cfg)), { onSuccess: (data) => syncFrom(data) });

  return (
    <div style={{ padding: '20px 24px', display: 'flex', flexDirection: 'column', gap: 20 }}>
      <p style={{ margin: 0, color: 'var(--s-text-3)', fontSize: 14 }}>
        Ajustes só deste sistema, por cima do perfil. Valem a partir da próxima auditoria e quem calcula a nota continua sendo o motor de regras.
      </p>
      {save.isError && <InlineNotification kind="error" title="Não foi possível salvar" subtitle={(save.error as Error).message} hideCloseButton />}
      {save.isSuccess && !save.isPending && !dirty && <InlineNotification kind="success" title="Ajustes salvos" subtitle="Valem na próxima auditoria." hideCloseButton lowContrast />}
      <Header cfg={cfg} busy={save.isPending} onReset={() => save.mutate(empty, { onSuccess: (data) => syncFrom(data) })} />

      <fieldset style={{ border: 0, padding: 0, margin: 0, display: 'grid', gap: 8 }}>
        <legend style={{ fontSize: 14, fontWeight: 600, marginBottom: 4 }}>Pacotes de regras</legend>
        {cfg.packs.map((p) => (
          <Checkbox
            key={p.id}
            id={`eng-pack-${p.id}`}
            labelText={`${p.name || p.id}${p.inProfile ? '' : ' (fora do perfil)'}`}
            helperText={p.description ?? undefined}
            checked={!!packs[p.id]}
            onChange={(_, { checked }) => setPacks({ ...packs, [p.id]: checked })}
          />
        ))}
      </fieldset>

      <fieldset style={{ border: 0, padding: 0, margin: 0, display: 'grid', gap: 8 }}>
        <legend style={{ fontSize: 14, fontWeight: 600, marginBottom: 4 }}>Limites de clean code</legend>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
          {cfg.thresholds.map((t) => (
            <label key={t.key} style={{ display: 'grid', gap: 4, fontSize: 12, color: 'var(--s-text-3)' }}>
              {THRESHOLD_LABEL[t.key] ?? t.key}
              <input
                aria-label={THRESHOLD_LABEL[t.key] ?? t.key}
                inputMode="numeric"
                value={limits[t.key] ?? ''}
                placeholder={t.profileValue === null ? 'padrão do motor' : `perfil: ${t.profileValue}`}
                onChange={(e) => setLimits({ ...limits, [t.key]: e.target.value })}
                style={{ ...fieldStyle, borderBottom: `1px solid ${limitError(t.key) ? '#da1e28' : 'var(--s-border-strong)'}`, fontFamily: mono }}
              />
              {limitError(t.key) && <span style={{ color: 'var(--s-danger-text)' }}>Use um inteiro maior que zero.</span>}
            </label>
          ))}
        </div>
      </fieldset>

      <section aria-label="Regras" style={{ display: 'grid', gap: 10 }}>
        <h3 style={{ margin: 0, fontSize: 14, fontWeight: 600 }}>Regras</h3>
        <p style={{ margin: 0, fontSize: 12, color: 'var(--s-text-3)' }}>
          {catalogMissing
            ? 'Lista o que apareceu na última auditoria deste sistema. '
            : 'Todas as regras dos pacotes, com o que cada uma faz. '}
          “Só observar” mostra o achado sem pesar na nota. Aceita curinga: <code style={{ fontFamily: mono }}>sentinel-*</code>.
          O ganho na nota é uma estimativa da última auditoria: quanto a nota sobe se a regra deixar de pesar.
        </p>

        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'end' }}>
          <label style={{ display: 'grid', gap: 4, fontSize: 12, color: 'var(--s-text-3)' }}>
            Buscar regra
            <input
              aria-label="Buscar regra"
              type="search"
              placeholder="id, texto, pacote ou categoria"
              value={text}
              onChange={(e) => { setText(e.target.value); setVisible(PAGE); }}
              style={{ ...fieldStyle, minWidth: 240 }}
            />
          </label>
          <label style={{ display: 'grid', gap: 4, fontSize: 12, color: 'var(--s-text-3)' }}>
            Mostrar
            <select aria-label="Mostrar regras" value={scope} onChange={(e) => { setScope(e.target.value as RuleScope); setVisible(PAGE); }} style={fieldStyle}>
              <option value="all">Todas</option>
              <option value="seen">As que apareceram na última auditoria</option>
              <option value="adjusted">As que eu ajustei</option>
            </select>
          </label>
          {packNames.length > 0 && (
            <label style={{ display: 'grid', gap: 4, fontSize: 12, color: 'var(--s-text-3)' }}>
              Pacote
              <select aria-label="Filtrar por pacote" value={packFilter} onChange={(e) => { setPackFilter(e.target.value); setVisible(PAGE); }} style={fieldStyle}>
                <option value="">Todos</option>
                {packNames.map((p) => (
                  <option key={p} value={p}>{p}</option>
                ))}
              </select>
            </label>
          )}
          <span role="status" style={{ fontSize: 12, color: 'var(--s-text-3)' }}>
            {filtered.length} {filtered.length === 1 ? 'regra' : 'regras'}
          </span>
        </div>

        {picked.size > 0 && (
          <div role="group" aria-label="Ações em lote" style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center', background: 'var(--s-selected)', padding: '8px 12px' }}>
            <strong style={{ fontSize: 13 }}>{picked.size} {picked.size === 1 ? 'selecionada' : 'selecionadas'}</strong>
            <Button kind="tertiary" size="sm" onClick={() => bulk({ kind: 'shadow', on: true })}>Só observar</Button>
            <Button kind="tertiary" size="sm" onClick={() => bulk({ kind: 'disable', on: true })}>Desligar</Button>
            <Button kind="tertiary" size="sm" onClick={() => bulk({ kind: 'disable', on: false })}>Ligar de novo</Button>
            <label style={{ fontSize: 12, display: 'inline-flex', gap: 6, alignItems: 'center' }}>
              Severidade
              <select
                aria-label="Severidade em lote"
                value=""
                onChange={(e) => e.target.value && bulk({ kind: 'severity', value: (e.target.value === 'perfil' ? null : e.target.value) as RuleAdjustment['severity'] })}
              >
                <option value="">escolher…</option>
                <option value="perfil">do perfil</option>
                {SEVERITIES.map((sv) => (
                  <option key={sv} value={sv}>{sv}</option>
                ))}
              </select>
            </label>
            <Button kind="ghost" size="sm" onClick={() => bulk({ kind: 'reset' })}>Limpar ajustes</Button>
            {gainPicked > 0 && (
              <span style={{ fontSize: 12, color: 'var(--s-text-3)' }} title="Estimativa; regras diferentes podem se sobrepor">
                Se deixarem de pesar, a nota sobe cerca de +{gainPicked.toFixed(1)}
              </span>
            )}
          </div>
        )}

        {filtered.length === 0 ? (
          <span style={{ fontSize: 13, color: 'var(--s-text-3)' }}>
            {allRows.length === 0 ? 'Ainda não há auditoria concluída: adicione uma regra pelo id.' : 'Nenhuma regra com esse filtro.'}
          </span>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
              <thead>
                <tr style={{ textAlign: 'left', background: 'var(--s-header-row)' }}>
                  <th style={{ padding: '6px 8px', width: 28 }}>
                    <input
                      type="checkbox"
                      aria-label="Selecionar as regras mostradas"
                      checked={allShownPicked}
                      onChange={(e) => setPicked(e.target.checked ? new Set([...picked, ...shown.map((r) => r.id)]) : new Set([...picked].filter((id) => !shown.some((r) => r.id === id))))}
                    />
                  </th>
                  <th style={{ padding: '6px 8px' }}>Regra</th>
                  <th style={{ padding: '6px 8px' }}>Ganho na nota</th>
                  <th style={{ padding: '6px 8px' }}>Severidade</th>
                  <th style={{ padding: '6px 8px' }}>Desligada</th>
                  <th style={{ padding: '6px 8px' }}>Só observar</th>
                </tr>
              </thead>
              <tbody>
                {shown.map((row) => {
                  const id = row.id;
                  const r = current(id);
                  const base = profileRule(id);
                  return (
                    <tr key={id} style={{ borderTop: '1px solid var(--s-border)', background: isNoopAdjustment(r) ? undefined : 'var(--s-selected)' }}>
                      <td style={{ padding: '6px 8px', verticalAlign: 'top' }}>
                        <input type="checkbox" aria-label={`Selecionar ${id}`} checked={picked.has(id)} onChange={(e) => toggle(id, e.target.checked)} />
                      </td>
                      <td style={{ padding: '6px 8px', maxWidth: 520 }}>
                        <div style={{ fontFamily: mono, wordBreak: 'break-all' }}>
                          {id}
                          {row.count > 0 && <span style={{ color: 'var(--s-text-3)' }}> · {row.count}</span>}
                        </div>
                        {row.title && <div style={{ color: 'var(--s-text-2)', marginTop: 2 }}>{row.title}</div>}
                        <div style={{ color: 'var(--s-text-3)', marginTop: 2 }}>
                          {[row.pack, row.category, row.declared ? `declara ${row.declared}` : null].filter(Boolean).join(' · ')}
                          {base && <span>{row.pack || row.category || row.declared ? ' · ' : ''}perfil já ajusta esta regra</span>}
                        </div>
                      </td>
                      <td style={{ padding: '6px 8px', verticalAlign: 'top', color: row.gain ? 'var(--s-success-text)' : 'var(--s-text-3)' }}>
                        {row.gain ? `+${row.gain.toFixed(1)}` : row.count > 0 ? '—' : 'não apareceu'}
                      </td>
                      <td style={{ padding: '6px 8px', verticalAlign: 'top' }}>
                        <select
                          aria-label={`Severidade de ${id}`}
                          value={r.severity ?? ''}
                          onChange={(e) => setRule(id, { severity: (e.target.value || null) as RuleAdjustment['severity'] })}
                        >
                          <option value="">do perfil</option>
                          {SEVERITIES.map((sv) => (
                            <option key={sv} value={sv}>{sv}</option>
                          ))}
                        </select>
                      </td>
                      <td style={{ padding: '6px 8px', verticalAlign: 'top' }}>
                        <input type="checkbox" aria-label={`Desligar ${id}`} checked={r.disabled} onChange={(e) => setRule(id, { disabled: e.target.checked })} />
                      </td>
                      <td style={{ padding: '6px 8px', verticalAlign: 'top' }}>
                        <input type="checkbox" aria-label={`Só observar ${id}`} checked={r.shadow === true} onChange={(e) => setRule(id, { shadow: e.target.checked ? true : null })} />
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        {filtered.length > shown.length && (
          <div>
            <Button kind="ghost" size="sm" onClick={() => setVisible(visible + PAGE)}>
              Mostrar mais {Math.min(PAGE, filtered.length - shown.length)} (faltam {filtered.length - shown.length})
            </Button>
          </div>
        )}
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
          <input
            aria-label="Id da regra"
            placeholder="id da regra ou padrão com *"
            value={newRule}
            onChange={(e) => setNewRule(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && addRule()}
            style={{ ...fieldStyle, fontFamily: mono, minWidth: 240 }}
          />
          <Button kind="tertiary" size="sm" onClick={addRule}>Adicionar regra</Button>
        </div>
      </section>

      <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
        <Button kind="primary" size="md" disabled={save.isPending || invalid} onClick={submit}>
          {save.isPending ? 'Salvando…' : 'Salvar ajustes'}
        </Button>
        {dirty && <span role="status" style={{ fontSize: 13, color: 'var(--s-warn-text)' }}>Alterações não salvas</span>}
      </div>
    </div>
  );
}

/** Aba "Analisadores": quais ferramentas rodam neste sistema. */
export function EngineAnalyzersPanel({ system, onDirtyChange }: PanelProps) {
  const { query, save } = useEngine(system.id);
  const cfg = query.data;
  const [off, setOff] = useState<string[]>([]);
  const dirty = !!cfg && JSON.stringify([...off].sort()) !== JSON.stringify([...cfg.tuning.disableAnalyzers].sort());
  const dirtyRef = useRef(false);
  dirtyRef.current = dirty;

  useEffect(() => {
    onDirtyChange?.(dirty);
  }, [dirty, onDirtyChange]);
  useEffect(() => {
    if (cfg && !dirtyRef.current) setOff(cfg.tuning.disableAnalyzers);
  }, [cfg]);

  if (query.isLoading) return <div style={{ padding: 24 }}>Carregando…</div>;
  if (query.isError || !cfg) {
    return (
      <div style={{ padding: 24 }}>
        <InlineNotification kind="error" title="Não foi possível carregar" subtitle={query.error instanceof Error ? query.error.message : 'Erro desconhecido'} hideCloseButton />
      </div>
    );
  }
  const toggle = (name: string, runs: boolean) => setOff(runs ? off.filter((n) => n !== name) : [...off, name]);

  return (
    <div style={{ padding: '20px 24px', display: 'flex', flexDirection: 'column', gap: 20 }}>
      <p style={{ margin: 0, color: 'var(--s-text-3)', fontSize: 14 }}>
        Escolha o que roda neste sistema. Um analisador desligado aparece como “não verificado” no resultado: o que ele cobriria fica sem checagem.
      </p>
      {save.isError && <InlineNotification kind="error" title="Não foi possível salvar" subtitle={(save.error as Error).message} hideCloseButton />}
      {save.isSuccess && !save.isPending && !dirty && <InlineNotification kind="success" title="Ajustes salvos" subtitle="Valem na próxima auditoria." hideCloseButton lowContrast />}
      <Header cfg={cfg} busy={save.isPending} onReset={() => save.mutate(empty, { onSuccess: (data) => setOff(data.tuning.disableAnalyzers) })} />
      <fieldset style={{ border: 0, padding: 0, margin: 0, display: 'grid', gap: 8 }}>
        <legend style={{ fontSize: 14, fontWeight: 600, marginBottom: 4 }}>Analisadores</legend>
        {cfg.analyzers.map((a) => (
          <Checkbox
            key={a.name}
            id={`eng-an-${a.name}`}
            labelText={a.name}
            helperText={a.description}
            checked={!off.includes(a.name)}
            onChange={(_, { checked }) => toggle(a.name, checked)}
          />
        ))}
      </fieldset>
      <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
        <Button
          kind="primary"
          size="md"
          disabled={save.isPending}
          onClick={() => save.mutate(mergeTuning(cfg.tuning, { disableAnalyzers: off }), { onSuccess: (data) => setOff(data.tuning.disableAnalyzers) })}
        >
          {save.isPending ? 'Salvando…' : 'Salvar ajustes'}
        </Button>
        {dirty && <span role="status" style={{ fontSize: 13, color: 'var(--s-warn-text)' }}>Alterações não salvas</span>}
      </div>
    </div>
  );
}
