import { Button, Checkbox, TextArea } from '@carbon/react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type React from 'react';
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
  type RuleRow,
  type RuleScope,
} from '../utils/ruleCatalog';
import {
  currentAdjustment,
  latestCompleted,
  limitInvalid,
  patchAdjustment,
  pickShown,
  sliceOf,
  toggledSet,
  withNewRule,
} from '../utils/engineTuning';
import {
  AddRuleRow,
  BulkBar,
  FamilyTabs,
  LoadError,
  PacksFieldset,
  PAGE,
  RuleFilters,
  RulesTable,
  SaveBar,
  SaveNotices,
  mono,
} from './EngineRulesParts';
import { familyOf, familyTabs, hiddenAdjustedCount, rowsOfActivePacks, ruleCountByPack, type FamilyTab } from '../utils/ruleFamilies';


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

const empty: EngineTuning = { rules: [], thresholds: {}, enablePacks: [], disablePacks: [], disableAnalyzers: [], ignorePaths: [] };

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

function hasUnsavedRules(
  cfg: EngineConfig | undefined,
  packs: Record<string, boolean>,
  limits: Record<string, string>,
  rules: RuleAdjustment[],
): boolean {
  if (!cfg || Object.keys(packs).length === 0) return false;
  const saved = { enablePacks: cfg.tuning.enablePacks, disablePacks: cfg.tuning.disablePacks, thresholds: cfg.tuning.thresholds, rules: cfg.tuning.rules };
  return signature(sliceOf(cfg, packs, limits, rules)) !== signature(saved);
}

/** Avisa quem abriu o painel quando há (ou deixa de haver) alteração sem salvar. */
function useDirtyReport(dirty: boolean, onDirtyChange?: (dirty: boolean) => void): void {
  useEffect(() => {
    onDirtyChange?.(dirty);
  }, [dirty, onDirtyChange]);
}

/** Efeito de cada regra na nota: vem do plano de correção da última auditoria concluída. */
function useFixPlanItems(systemId: number) {
  const runs = useAuditRuns(systemId);
  const latest = latestCompleted(runs.data);
  const fixPlan = useFixPlan(latest?.id ?? 0, latest !== undefined);
  const items = Array.isArray(fixPlan.data?.items) ? fixPlan.data.items : [];
  return { items, data: fixPlan.data };
}

interface RulesSectionProps {
  allRows: RuleRow[];
  filtered: RuleRow[];
  shown: RuleRow[];
  filters: { text: string; scope: RuleScope; packFilter: string; packNames: string[] };
  onFilters: { text: (v: string) => void; scope: (v: RuleScope) => void; pack: (v: string) => void };
  catalogMissing: boolean;
  family: { tabs: FamilyTab[]; active: string; onPick: (key: string) => void };
  packsNote: string;
  picked: Set<string>;
  gainPicked: number;
  bulk: (action: BulkAction) => void;
  table: {
    current: (id: string) => RuleAdjustment;
    inProfile: (id: string) => boolean;
    onPickAll: (on: boolean) => void;
    onSelect: (id: string, on: boolean) => void;
    onChange: (id: string, patch: Partial<RuleAdjustment>) => void;
  };
  onMore: () => void;
  newRule: { value: string; onChange: (v: string) => void; onAdd: () => void };
}

function RulesSection(p: RulesSectionProps) {
  const { filtered, shown, allRows, filters } = p;
  const allShownPicked = shown.length > 0 && shown.every((r) => p.picked.has(r.id));
  return (
    <section aria-label="Regras" style={{ display: 'grid', gap: 10 }}>
      <h3 style={{ margin: 0, fontSize: 14, fontWeight: 600 }}>Regras</h3>
      <p style={{ margin: 0, fontSize: 12, color: 'var(--s-text-3)' }}>
        {p.catalogMissing
          ? 'Lista o que apareceu na última auditoria deste sistema. '
          : 'Todas as regras dos pacotes, com o que cada uma faz. '}
        “Só observar” mostra o achado sem pesar na nota. Aceita curinga: <code style={{ fontFamily: mono }}>sentinel-*</code>.
        O ganho na nota é uma estimativa da última auditoria: quanto a nota sobe se a regra deixar de pesar.
      </p>
      <div style={{ padding: '8px 12px', background: 'var(--s-header-row)', borderLeft: '3px solid #0f62fe', fontSize: 13 }}>{p.packsNote}</div>
      <FamilyTabs tabs={p.family.tabs} active={p.family.active} onPick={p.family.onPick} />
      <RuleFilters
        text={filters.text}
        scope={filters.scope}
        packFilter={filters.packFilter}
        packNames={filters.packNames}
        count={filtered.length}
        onText={p.onFilters.text}
        onScope={p.onFilters.scope}
        onPack={p.onFilters.pack}
      />
      <BulkBar count={p.picked.size} gain={p.gainPicked} bulk={p.bulk} />
      {filtered.length === 0 ? (
        <span style={{ fontSize: 13, color: 'var(--s-text-3)' }}>
          {allRows.length === 0 ? 'Ainda não há regra para mostrar: ligue um pacote acima ou adicione uma regra pelo id.' : 'Nenhuma regra com esse filtro nesta família.'}
        </span>
      ) : (
        <RulesTable
          rows={shown}
          allShownPicked={allShownPicked}
          isPicked={(id) => p.picked.has(id)}
          current={p.table.current}
          inProfile={p.table.inProfile}
          onPickAll={p.table.onPickAll}
          onSelect={p.table.onSelect}
          onChange={p.table.onChange}
        />
      )}
      {filtered.length > shown.length && (
        <div>
          <Button kind="ghost" size="sm" onClick={p.onMore}>
            Mostrar mais {Math.min(PAGE, filtered.length - shown.length)} (faltam {filtered.length - shown.length})
          </Button>
        </div>
      )}
      <AddRuleRow value={p.newRule.value} onChange={p.newRule.onChange} onAdd={p.newRule.onAdd} />
    </section>
  );
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
  const [family, setFamily] = useState('');
  const [visible, setVisible] = useState(PAGE);
  const [picked, setPicked] = useState<Set<string>>(new Set());
  const plan = useFixPlanItems(system.id);

  const syncFrom = (data: EngineConfig) => {
    setPacks(Object.fromEntries(data.packs.map((p) => [p.id, p.active])));
    setLimits(Object.fromEntries(Object.entries(data.tuning.thresholds).map(([k, v]) => [k, String(v)])));
    setRules(data.tuning.rules);
    setPicked(new Set());
  };

  const dirty = useMemo(() => hasUnsavedRules(cfg, packs, limits, rules), [cfg, packs, limits, rules]);

  const dirtyRef = useRef(false);
  dirtyRef.current = dirty;
  useDirtyReport(dirty, onDirtyChange);

  // Só recarrega a tela com o que veio do servidor se não houver edição em andamento.
  useEffect(() => {
    if (!cfg || dirtyRef.current) return;
    syncFrom(cfg);
  }, [cfg]);

  const allRows = useMemo(
    () =>
      cfg
        ? buildRuleRows({ catalog: cfg.catalog, known: cfg.knownRules, profileRules: cfg.profileRules, adjusted: rules, plan: plan.items })
        : [],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [cfg, rules, plan.data],
  );

  if (query.isLoading) return <div style={{ padding: 24 }}>Carregando…</div>;
  if (query.isError || !cfg) return <LoadError error={query.error} />;

  const adjustedIds = new Set(rules.filter((r) => !isNoopAdjustment(r)).map((r) => r.id));
  // Só aparecem as regras dos pacotes ligados acima; a família escolhida filtra o resto.
  const packRows = rowsOfActivePacks(allRows, packs);
  const tabs = familyTabs(packRows, adjustedIds);
  const activeFamily = tabs.some((t) => t.key === family) ? family : (tabs[0]?.key ?? '');
  const packNames = Array.from(new Set(packRows.map((r) => r.pack).filter((p): p is string => !!p))).sort();
  const inFamily = tabs.length < 2 ? packRows : packRows.filter((r) => familyOf(r) === activeFamily);
  const filtered = filterRuleRows(inFamily, { query: text, scope, pack: packFilter }, adjustedIds);
  const hidden = hiddenAdjustedCount(allRows, packs, adjustedIds);
  const packsNote =
    `Mostrando as regras dos ${cfg.packs.filter((p) => packs[p.id]).length} pacotes ligados acima (${packRows.length} ${packRows.length === 1 ? 'regra' : 'regras'}). Ligue um pacote para ver as regras dele aqui.` +
    (hidden > 0 ? ` ${hidden} ${hidden === 1 ? 'regra ajustada está' : 'regras ajustadas estão'} em pacote desligado e não aparece.` : '');
  const shown = filtered.slice(0, visible);
  const pickedIds = [...picked];

  const invalid = Object.keys(limits).some((k) => limitInvalid(limits[k]));
  const resetPage = () => setVisible(PAGE);
  const submit = () =>
    save.mutate(mergeTuning(cfg.tuning, sliceOf(cfg, packs, limits, rules)), { onSuccess: (data) => syncFrom(data) });

  return (
    <div style={{ padding: '20px 24px', display: 'flex', flexDirection: 'column', gap: 20 }}>
      <p style={{ margin: 0, color: 'var(--s-text-3)', fontSize: 14 }}>
        Ajustes só deste sistema, por cima do perfil. Valem a partir da próxima auditoria e quem calcula a nota continua sendo o motor de regras.
      </p>
      <SaveNotices save={save} dirty={dirty} />
      <Header cfg={cfg} busy={save.isPending} onReset={() => save.mutate(empty, { onSuccess: (data) => syncFrom(data) })} />
      <PacksFieldset
        cfg={cfg}
        packs={packs}
        setPacks={setPacks}
        ruleCounts={ruleCountByPack(cfg.catalog)}
        limits={limits}
        setLimits={setLimits}
        limitError={(k) => limitInvalid(limits[k])}
      />
      <RulesSection
        allRows={packRows}
        filtered={filtered}
        shown={shown}
        filters={{ text, scope, packFilter, packNames }}
        onFilters={{
          text: (v) => { setText(v); resetPage(); },
          scope: (v) => { setScope(v); resetPage(); },
          pack: (v) => { setPackFilter(v); resetPage(); },
        }}
        catalogMissing={(cfg.catalog ?? []).length === 0}
        family={{ tabs, active: activeFamily, onPick: (k) => { setFamily(k); resetPage(); } }}
        packsNote={packsNote}
        picked={picked}
        gainPicked={estimatedGain(allRows, pickedIds)}
        bulk={(action) => setRules(applyBulk(rules, pickedIds, action))}
        table={{
          current: (id) => currentAdjustment(rules, id),
          inProfile: (id) => cfg.profileRules.some((r) => r.id === id),
          onPickAll: (on) => setPicked(pickShown(picked, shown.map((r) => r.id), on)),
          onSelect: (id, on) => setPicked(toggledSet(picked, id, on)),
          onChange: (id, patch) => setRules(patchAdjustment(rules, id, patch)),
        }}
        onMore={() => setVisible(visible + PAGE)}
        newRule={{
          value: newRule,
          onChange: setNewRule,
          onAdd: () => {
            setRules(withNewRule(rules, newRule));
            setNewRule('');
          },
        }}
      />
      <SaveBar pending={save.isPending} disabled={save.isPending || invalid} dirty={dirty} onSave={submit} />
    </div>
  );
}

/** Uma linha por caminho; linhas vazias e repetidas saem. */
export function pathLines(text: string): string[] {
  return [...new Set(text.split('\n').map((l) => l.trim()).filter((l) => l !== ''))];
}

function analyzersDirty(cfg: EngineConfig | undefined, off: string[], paths: string[]): boolean {
  if (!cfg) return false;
  const offChanged = JSON.stringify([...off].sort()) !== JSON.stringify([...cfg.tuning.disableAnalyzers].sort());
  return offChanged || JSON.stringify(paths) !== JSON.stringify(cfg.tuning.ignorePaths ?? []);
}

const MAX_IGNORED_PATHS = 50;

/** Aba "Analisadores": quais ferramentas rodam neste sistema e quais caminhos ficam fora da auditoria. */
export function EngineAnalyzersPanel({ system, onDirtyChange }: PanelProps) {
  const { query, save } = useEngine(system.id);
  const cfg = query.data;
  const [off, setOff] = useState<string[]>([]);
  const [pathsText, setPathsText] = useState('');
  const paths = pathLines(pathsText);
  const dirty = analyzersDirty(cfg, off, paths);
  const syncSaved = (data: EngineConfig) => {
    setOff(data.tuning.disableAnalyzers);
    setPathsText((data.tuning.ignorePaths ?? []).join('\n'));
  };
  const dirtyRef = useRef(false);
  const loadedRef = useRef(false);
  dirtyRef.current = dirty;

  useDirtyReport(dirty, onDirtyChange);
  useEffect(() => {
    // Na primeira carga os campos ainda estão vazios (parecem "alterados"), então ela sempre preenche.
    if (cfg && (!loadedRef.current || !dirtyRef.current)) {
      loadedRef.current = true;
      syncSaved(cfg);
    }
  }, [cfg]);

  if (query.isLoading) return <div style={{ padding: 24 }}>Carregando…</div>;
  if (query.isError || !cfg) return <LoadError error={query.error} />;
  const toggle = (name: string, runs: boolean) => setOff(runs ? off.filter((n) => n !== name) : [...off, name]);
  const tooManyPaths = paths.length > MAX_IGNORED_PATHS;

  return (
    <div style={{ padding: '20px 24px', display: 'flex', flexDirection: 'column', gap: 20 }}>
      <p style={{ margin: 0, color: 'var(--s-text-3)', fontSize: 14 }}>
        Escolha o que roda neste sistema. Um analisador desligado aparece como “não verificado” no resultado: o que ele cobriria fica sem checagem.
      </p>
      <SaveNotices save={save} dirty={dirty} />
      <Header cfg={cfg} busy={save.isPending} onReset={() => save.mutate(empty, { onSuccess: syncSaved })} />
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
      <TextArea
        id="eng-ignore-paths"
        labelText="Caminhos ignorados"
        helperText="Um por linha. Achados nesses caminhos não entram na nota deste sistema. Exemplos: docs/ (pasta), **/test/** (testes em qualquer lugar), *.md (por nome), /build (só na raiz)."
        placeholder={'docs/\n**/test/**\n*.md'}
        rows={4}
        value={pathsText}
        invalid={tooManyPaths}
        invalidText="No máximo 50 caminhos."
        onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setPathsText(e.target.value)}
      />
      <SaveBar
        pending={save.isPending}
        disabled={save.isPending || tooManyPaths}
        dirty={dirty}
        onSave={() => save.mutate(mergeTuning(cfg.tuning, { disableAnalyzers: off, ignorePaths: paths }), { onSuccess: syncSaved })}
      />
    </div>
  );
}
