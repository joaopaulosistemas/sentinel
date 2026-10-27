import { Button, Checkbox, InlineNotification } from '@carbon/react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { EngineConfig, EngineTuning, RuleAdjustment, SystemSummaryResponse } from '../types';

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
export function isNoop(r: RuleAdjustment): boolean {
  return r.severity === null && !r.disabled && r.shadow === null;
}

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

/** Aba "Motor de regras": pacotes, limites e regras deste sistema, por cima do perfil. A nota continua sendo do motor. */
export function EngineRulesPanel({ system }: { system: SystemSummaryResponse }) {
  const { query, save } = useEngine(system.id);
  const cfg = query.data;
  const [packs, setPacks] = useState<Record<string, boolean>>({});
  const [limits, setLimits] = useState<Record<string, string>>({});
  const [rules, setRules] = useState<RuleAdjustment[]>([]);
  const [newRule, setNewRule] = useState('');

  useEffect(() => {
    if (!cfg) return;
    setPacks(Object.fromEntries(cfg.packs.map((p) => [p.id, p.active])));
    setLimits(Object.fromEntries(Object.entries(cfg.tuning.thresholds).map(([k, v]) => [k, String(v)])));
    setRules(cfg.tuning.rules);
  }, [cfg]);

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
  const shownIds = Array.from(new Set([...rules.map((r) => r.id), ...cfg.knownRules.slice(0, 40).map((k) => k.id.toLowerCase())]));
  const countOf = (id: string) => cfg.knownRules.find((k) => k.id.toLowerCase() === id)?.count;
  const current = (id: string): RuleAdjustment => rules.find((r) => r.id === id) ?? { id, severity: null, disabled: false, shadow: null };
  const setRule = (id: string, patch: Partial<RuleAdjustment>) => {
    const next = { ...current(id), ...patch };
    setRules([...rules.filter((r) => r.id !== id), next]);
  };

  const buildSlice = (): Partial<EngineTuning> => {
    const enablePacks = cfg.packs.filter((p) => packs[p.id] && !p.inProfile).map((p) => p.id);
    const disablePacks = cfg.packs.filter((p) => !packs[p.id] && p.inProfile).map((p) => p.id);
    const thresholds: Record<string, number> = {};
    for (const [k, v] of Object.entries(limits)) {
      const n = Number(v);
      if (v.trim() !== '' && Number.isInteger(n) && n >= 1) thresholds[k] = n;
    }
    return { enablePacks, disablePacks, thresholds, rules: rules.filter((r) => !isNoop(r)) };
  };
  const limitError = (k: string) => limits[k] !== undefined && limits[k].trim() !== '' && !(Number.isInteger(Number(limits[k])) && Number(limits[k]) >= 1);
  const invalid = Object.keys(limits).some(limitError);
  const addRule = () => {
    const id = newRule.trim().toLowerCase();
    if (!id || !/^[a-z0-9_.*:/-]+$/.test(id)) return;
    if (!rules.some((r) => r.id === id)) setRules([...rules, { id, severity: null, disabled: false, shadow: true }]);
    setNewRule('');
  };

  return (
    <div style={{ padding: '20px 24px', display: 'flex', flexDirection: 'column', gap: 20 }}>
      <p style={{ margin: 0, color: 'var(--s-text-3)', fontSize: 14 }}>
        Ajustes só deste sistema, por cima do perfil. Valem a partir da próxima auditoria e quem calcula a nota continua sendo o motor de regras.
      </p>
      {save.isError && <InlineNotification kind="error" title="Não foi possível salvar" subtitle={(save.error as Error).message} hideCloseButton />}
      {save.isSuccess && !save.isPending && <InlineNotification kind="success" title="Ajustes salvos" subtitle="Valem na próxima auditoria." hideCloseButton lowContrast />}
      <Header cfg={cfg} busy={save.isPending} onReset={() => save.mutate(empty)} />

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
                style={{ height: 36, padding: '0 8px', border: 0, borderBottom: `1px solid ${limitError(t.key) ? '#da1e28' : 'var(--s-border-strong)'}`, background: 'var(--s-layer)', fontFamily: mono }}
              />
              {limitError(t.key) && <span style={{ color: '#da1e28' }}>Use um inteiro maior que zero.</span>}
            </label>
          ))}
        </div>
      </fieldset>

      <section aria-label="Regras" style={{ display: 'grid', gap: 8 }}>
        <h3 style={{ margin: 0, fontSize: 14, fontWeight: 600 }}>Regras</h3>
        <p style={{ margin: 0, fontSize: 12, color: 'var(--s-text-3)' }}>
          Lista o que apareceu na última auditoria deste sistema. "Só observar" mostra o achado sem pesar na nota. Aceita curinga: <code style={{ fontFamily: mono }}>sentinel-*</code>.
        </p>
        {shownIds.length === 0 ? (
          <span style={{ fontSize: 13, color: 'var(--s-text-3)' }}>Ainda não há auditoria concluída: adicione uma regra pelo id.</span>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
              <thead>
                <tr style={{ textAlign: 'left', background: 'var(--s-header-row)' }}>
                  <th style={{ padding: '6px 8px' }}>Regra</th>
                  <th style={{ padding: '6px 8px' }}>Severidade</th>
                  <th style={{ padding: '6px 8px' }}>Desligada</th>
                  <th style={{ padding: '6px 8px' }}>Só observar</th>
                </tr>
              </thead>
              <tbody>
                {shownIds.map((id) => {
                  const r = current(id);
                  const base = profileRule(id);
                  return (
                    <tr key={id} style={{ borderTop: '1px solid var(--s-border)', background: isNoop(r) ? undefined : '#edf5ff' }}>
                      <td style={{ padding: '6px 8px', fontFamily: mono, wordBreak: 'break-all' }}>
                        {id}
                        {countOf(id) !== undefined && <span style={{ color: 'var(--s-text-3)' }}> · {countOf(id)}</span>}
                        {base && <div style={{ color: 'var(--s-text-3)', fontFamily: 'inherit' }}>perfil já ajusta esta regra</div>}
                      </td>
                      <td style={{ padding: '6px 8px' }}>
                        <select
                          aria-label={`Severidade de ${id}`}
                          value={r.severity ?? ''}
                          onChange={(e) => setRule(id, { severity: (e.target.value || null) as RuleAdjustment['severity'] })}
                        >
                          <option value="">do perfil</option>
                          {SEVERITIES.map((s) => (
                            <option key={s} value={s}>{s}</option>
                          ))}
                        </select>
                      </td>
                      <td style={{ padding: '6px 8px' }}>
                        <input type="checkbox" aria-label={`Desligar ${id}`} checked={r.disabled} onChange={(e) => setRule(id, { disabled: e.target.checked })} />
                      </td>
                      <td style={{ padding: '6px 8px' }}>
                        <input type="checkbox" aria-label={`Só observar ${id}`} checked={r.shadow === true} onChange={(e) => setRule(id, { shadow: e.target.checked ? true : null })} />
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
          <input
            aria-label="Id da regra"
            placeholder="id da regra ou padrão com *"
            value={newRule}
            onChange={(e) => setNewRule(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && addRule()}
            style={{ height: 36, padding: '0 8px', border: 0, borderBottom: '1px solid var(--s-border-strong)', background: 'var(--s-layer)', fontFamily: mono, minWidth: 240 }}
          />
          <Button kind="tertiary" size="sm" onClick={addRule}>Adicionar regra</Button>
        </div>
      </section>

      <div>
        <Button kind="primary" size="md" disabled={save.isPending || invalid} onClick={() => save.mutate(mergeTuning(server, buildSlice()))}>
          {save.isPending ? 'Salvando…' : 'Salvar ajustes'}
        </Button>
      </div>
    </div>
  );
}

/** Aba "Analisadores": quais ferramentas rodam neste sistema. */
export function EngineAnalyzersPanel({ system }: { system: SystemSummaryResponse }) {
  const { query, save } = useEngine(system.id);
  const cfg = query.data;
  const [off, setOff] = useState<string[]>([]);
  useEffect(() => {
    if (cfg) setOff(cfg.tuning.disableAnalyzers);
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
        Escolha o que roda neste sistema. Um analisador desligado aparece como "não verificado" no resultado: o que ele cobriria fica sem checagem.
      </p>
      {save.isError && <InlineNotification kind="error" title="Não foi possível salvar" subtitle={(save.error as Error).message} hideCloseButton />}
      {save.isSuccess && !save.isPending && <InlineNotification kind="success" title="Ajustes salvos" subtitle="Valem na próxima auditoria." hideCloseButton lowContrast />}
      <Header cfg={cfg} busy={save.isPending} onReset={() => save.mutate(empty)} />
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
      <div>
        <Button kind="primary" size="md" disabled={save.isPending} onClick={() => save.mutate(mergeTuning(cfg.tuning, { disableAnalyzers: off }))}>
          {save.isPending ? 'Salvando…' : 'Salvar ajustes'}
        </Button>
      </div>
    </div>
  );
}
