import type { EngineConfig, FixPlanItem, RuleAdjustment } from '../types';

/** Id curto da regra: o que vem depois do último ponto, em minúsculas. É assim que o motor casa os ajustes. */
export const shortRuleId = (id: string): string => id.toLowerCase().slice(id.lastIndexOf('.') + 1);

/** Regra sem nenhum efeito não é ajuste. */
export function isNoopAdjustment(r: RuleAdjustment): boolean {
  return r.severity === null && !r.disabled && r.shadow === null;
}

export interface RuleRow {
  id: string;
  title: string | null;
  pack: string | null;
  /** Severidade declarada na regra (Semgrep: ERROR, WARNING, INFO), sem tradução. */
  declared: string | null;
  category: string | null;
  /** Achados na última auditoria concluída; 0 se não apareceu nela. */
  count: number;
  /** Quanto a nota sobe se esta regra deixar de pesar (corrigida ou desligada), segundo o plano da última auditoria. */
  gain: number | null;
  /** O perfil já ajusta esta regra. */
  inProfile: boolean;
}

export interface RuleSources {
  catalog: EngineConfig['catalog'];
  known: EngineConfig['knownRules'];
  profileRules: RuleAdjustment[];
  adjusted: RuleAdjustment[];
  plan: FixPlanItem[];
}

/** Junta catálogo dos pacotes, regras vistas na última auditoria, ajustes e plano de correção numa lista só. */
export function buildRuleRows({ catalog = [], known, profileRules, adjusted, plan }: RuleSources): RuleRow[] {
  const rows = new Map<string, RuleRow>();
  const ensure = (id: string): RuleRow => {
    const key = shortRuleId(id);
    let row = rows.get(key);
    if (!row) {
      row = { id: key, title: null, pack: null, declared: null, category: null, count: 0, gain: null, inProfile: false };
      rows.set(key, row);
    }
    return row;
  };

  for (const c of catalog) {
    const row = ensure(c.id);
    row.title = c.message ?? row.title;
    row.pack = c.pack;
    row.declared = c.severity ?? null;
    row.category = c.category ?? null;
  }
  for (const k of known) {
    ensure(k.id).count += k.count;
  }
  for (const item of plan) {
    if (!item.ruleId) continue;
    const row = ensure(item.ruleId);
    row.title = row.title ?? item.title;
    row.gain = (row.gain ?? 0) + item.scoreGain;
  }
  for (const p of profileRules) ensure(p.id).inProfile = true;
  for (const a of adjusted) ensure(a.id);
  return [...rows.values()];
}

export type RuleScope = 'all' | 'seen' | 'adjusted';

export interface RuleFilter {
  query: string;
  scope: RuleScope;
  pack: string;
}

/** Filtra por texto (id, texto da regra, pacote), por escopo (todas, vistas na última auditoria, ajustadas) e por pacote. */
export function filterRuleRows(rows: RuleRow[], filter: RuleFilter, adjustedIds: Set<string>): RuleRow[] {
  const q = filter.query.trim().toLowerCase();
  return rows
    .filter((r) => {
      if (filter.scope === 'seen' && r.count === 0) return false;
      if (filter.scope === 'adjusted' && !adjustedIds.has(r.id)) return false;
      if (filter.pack !== '' && r.pack !== filter.pack) return false;
      if (q === '') return true;
      return `${r.id} ${r.title ?? ''} ${r.pack ?? ''} ${r.category ?? ''}`.toLowerCase().includes(q);
    })
    .sort((a, b) => Number(adjustedIds.has(b.id)) - Number(adjustedIds.has(a.id)) || b.count - a.count || a.id.localeCompare(b.id));
}

export type BulkAction =
  | { kind: 'shadow'; on: boolean }
  | { kind: 'disable'; on: boolean }
  | { kind: 'severity'; value: RuleAdjustment['severity'] }
  | { kind: 'reset' };

/** Aplica uma ação a várias regras de uma vez; devolve a lista nova de ajustes (sem os que não têm efeito). */
export function applyBulk(current: RuleAdjustment[], ids: string[], action: BulkAction): RuleAdjustment[] {
  const byId = new Map(current.map((r) => [r.id, r]));
  for (const id of ids) {
    const base: RuleAdjustment = byId.get(id) ?? { id, severity: null, disabled: false, shadow: null };
    let next = base;
    if (action.kind === 'shadow') next = { ...base, shadow: action.on ? true : null };
    else if (action.kind === 'disable') next = { ...base, disabled: action.on };
    else if (action.kind === 'severity') next = { ...base, severity: action.value };
    else next = { id, severity: null, disabled: false, shadow: null };
    byId.set(id, next);
  }
  return [...byId.values()].filter((r) => !isNoopAdjustment(r));
}

/** Soma do ganho estimado das regras escolhidas. É uma estimativa: ganhos de regras diferentes podem se sobrepor. */
export function estimatedGain(rows: RuleRow[], ids: string[]): number {
  const wanted = new Set(ids);
  return rows.filter((r) => wanted.has(r.id)).reduce((sum, r) => sum + (r.gain ?? 0), 0);
}
