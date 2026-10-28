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
  LimitsFieldset,
  LoadError,
  PacksFieldset,
  PAGE,
  RuleFilters,
  RulesTable,
  SaveBar,
  SaveNotices,
  mono,
} from './EngineRulesParts';


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
  const latest = latestCompleted(runs.data);
  const fixPlan = useFixPlan(latest?.id ?? 0, latest !== undefined);
  const planItems = Array.isArray(fixPlan.data?.items) ? fixPlan.data.items : [];

  const syncFrom = (data: EngineConfig) => {
    setPacks(Object.fromEntries(data.packs.map((p) => [p.id, p.active])));
    setLimits(Object.fromEntries(Object.entries(data.tuning.thresholds).map(([k, v]) => [k, String(v)])));
    setRules(data.tuning.rules);
    setPicked(new Set());
  };

  const dirty = useMemo(() => {
    if (!cfg || Object.keys(packs).length === 0) return false;
    const saved = { enablePacks: cfg.tuning.enablePacks, disablePacks: cfg.tuning.disablePacks, thresholds: cfg.tuning.thresholds, rules: cfg.tuning.rules };
    return signature(sliceOf(cfg, packs, limits, rules)) !== signature(saved);
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
  if (query.isError || !cfg) return <LoadError error={query.error} />;

  const server = cfg.tuning;
  const profileRule = (id: string) => cfg.profileRules.find((r) => r.id === id);
  const adjustedIds = new Set(rules.filter((r) => !isNoopAdjustment(r)).map((r) => r.id));
  const current = (id: string) => currentAdjustment(rules, id);
  const setRule = (id: string, patch: Partial<RuleAdjustment>) => setRules(patchAdjustment(rules, id, patch));

  const packNames = Array.from(new Set(allRows.map((r) => r.pack).filter((p): p is string => !!p))).sort();
  const filtered = filterRuleRows(allRows, { query: text, scope, pack: packFilter }, adjustedIds);
  const shown = filtered.slice(0, visible);
  const pickedIds = [...picked];
  const gainPicked = estimatedGain(allRows, pickedIds);
  const catalogMissing = (cfg.catalog ?? []).length === 0;

  const limitError = (k: string) => limitInvalid(limits[k]);
  const invalid = Object.keys(limits).some(limitError);
  const addRule = () => {
    setRules(withNewRule(rules, newRule));
    setNewRule('');
  };
  const bulk = (action: BulkAction) => setRules(applyBulk(rules, pickedIds, action));
  const toggle = (id: string, on: boolean) => setPicked(toggledSet(picked, id, on));
  const allShownPicked = shown.length > 0 && shown.every((r) => picked.has(r.id));

  const submit = () =>
    save.mutate(mergeTuning(server, sliceOf(cfg, packs, limits, rules)), { onSuccess: (data) => syncFrom(data) });

  const resetPage = () => setVisible(PAGE);
  const pickAllShown = (on: boolean) => setPicked(pickShown(picked, shown.map((r) => r.id), on));

  return (
    <div style={{ padding: '20px 24px', display: 'flex', flexDirection: 'column', gap: 20 }}>
      <p style={{ margin: 0, color: 'var(--s-text-3)', fontSize: 14 }}>
        Ajustes só deste sistema, por cima do perfil. Valem a partir da próxima auditoria e quem calcula a nota continua sendo o motor de regras.
      </p>
      <SaveNotices save={save} dirty={dirty} />
      <Header cfg={cfg} busy={save.isPending} onReset={() => save.mutate(empty, { onSuccess: (data) => syncFrom(data) })} />
      <PacksFieldset cfg={cfg} packs={packs} setPacks={setPacks} />
      <LimitsFieldset cfg={cfg} limits={limits} setLimits={setLimits} limitError={limitError} />

      <section aria-label="Regras" style={{ display: 'grid', gap: 10 }}>
        <h3 style={{ margin: 0, fontSize: 14, fontWeight: 600 }}>Regras</h3>
        <p style={{ margin: 0, fontSize: 12, color: 'var(--s-text-3)' }}>
          {catalogMissing
            ? 'Lista o que apareceu na última auditoria deste sistema. '
            : 'Todas as regras dos pacotes, com o que cada uma faz. '}
          “Só observar” mostra o achado sem pesar na nota. Aceita curinga: <code style={{ fontFamily: mono }}>sentinel-*</code>.
          O ganho na nota é uma estimativa da última auditoria: quanto a nota sobe se a regra deixar de pesar.
        </p>
        <RuleFilters
          text={text}
          scope={scope}
          packFilter={packFilter}
          packNames={packNames}
          count={filtered.length}
          onText={(v) => { setText(v); resetPage(); }}
          onScope={(v) => { setScope(v); resetPage(); }}
          onPack={(v) => { setPackFilter(v); resetPage(); }}
        />
        <BulkBar count={picked.size} gain={gainPicked} bulk={bulk} />
        {filtered.length === 0 ? (
          <span style={{ fontSize: 13, color: 'var(--s-text-3)' }}>
            {allRows.length === 0 ? 'Ainda não há auditoria concluída: adicione uma regra pelo id.' : 'Nenhuma regra com esse filtro.'}
          </span>
        ) : (
          <RulesTable
            rows={shown}
            allShownPicked={allShownPicked}
            isPicked={(id) => picked.has(id)}
            current={current}
            inProfile={(id) => !!profileRule(id)}
            onPickAll={pickAllShown}
            onSelect={toggle}
            onChange={setRule}
          />
        )}
        {filtered.length > shown.length && (
          <div>
            <Button kind="ghost" size="sm" onClick={() => setVisible(visible + PAGE)}>
              Mostrar mais {Math.min(PAGE, filtered.length - shown.length)} (faltam {filtered.length - shown.length})
            </Button>
          </div>
        )}
        <AddRuleRow value={newRule} onChange={setNewRule} onAdd={addRule} />
      </section>

      <SaveBar pending={save.isPending} disabled={save.isPending || invalid} dirty={dirty} onSave={submit} />
    </div>
  );
}

/** Uma linha por caminho; linhas vazias e repetidas saem. */
export function pathLines(text: string): string[] {
  return [...new Set(text.split('\n').map((l) => l.trim()).filter((l) => l !== ''))];
}

/** Aba "Analisadores": quais ferramentas rodam neste sistema e quais caminhos ficam fora da auditoria. */
export function EngineAnalyzersPanel({ system, onDirtyChange }: PanelProps) {
  const { query, save } = useEngine(system.id);
  const cfg = query.data;
  const [off, setOff] = useState<string[]>([]);
  const [pathsText, setPathsText] = useState('');
  const paths = pathLines(pathsText);
  const dirty =
    !!cfg &&
    (JSON.stringify([...off].sort()) !== JSON.stringify([...cfg.tuning.disableAnalyzers].sort()) ||
      JSON.stringify(paths) !== JSON.stringify(cfg.tuning.ignorePaths ?? []));
  const syncSaved = (data: EngineConfig) => {
    setOff(data.tuning.disableAnalyzers);
    setPathsText((data.tuning.ignorePaths ?? []).join('\n'));
  };
  const dirtyRef = useRef(false);
  const loadedRef = useRef(false);
  dirtyRef.current = dirty;

  useEffect(() => {
    onDirtyChange?.(dirty);
  }, [dirty, onDirtyChange]);
  useEffect(() => {
    // Na primeira carga os campos ainda estão vazios (parecem "alterados"), então ela sempre preenche.
    if (cfg && (!loadedRef.current || !dirtyRef.current)) {
      loadedRef.current = true;
      setOff(cfg.tuning.disableAnalyzers);
      setPathsText((cfg.tuning.ignorePaths ?? []).join('\n'));
    }
  }, [cfg]);

  if (query.isLoading) return <div style={{ padding: 24 }}>Carregando…</div>;
  if (query.isError || !cfg) return <LoadError error={query.error} />;
  const toggle = (name: string, runs: boolean) => setOff(runs ? off.filter((n) => n !== name) : [...off, name]);

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
        invalid={paths.length > 50}
        invalidText="No máximo 50 caminhos."
        onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setPathsText(e.target.value)}
      />
      <SaveBar
        pending={save.isPending}
        disabled={save.isPending || paths.length > 50}
        dirty={dirty}
        onSave={() => save.mutate(mergeTuning(cfg.tuning, { disableAnalyzers: off, ignorePaths: paths }), { onSuccess: syncSaved })}
      />
    </div>
  );
}
