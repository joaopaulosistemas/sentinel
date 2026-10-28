import type { AuditRunResponse, EngineConfig, EngineTuning, RuleAdjustment } from '../types';
import { isNoopAdjustment } from './ruleCatalog';

export type TuningSlice = Pick<EngineTuning, 'enablePacks' | 'disablePacks' | 'thresholds' | 'rules'>;

/** Limite digitado: vazio vale "padrão do motor"; o resto precisa ser inteiro ≥ 1. */
export function limitInvalid(value: string | undefined): boolean {
  if (value === undefined || value.trim() === '') return false;
  const n = Number(value);
  return !(Number.isInteger(n) && n >= 1);
}

/** O que a tela edita (pacotes, limites, regras) na forma que o servidor guarda. */
export function sliceOf(data: EngineConfig, packs: Record<string, boolean>, limits: Record<string, string>, rules: RuleAdjustment[]): TuningSlice {
  const enablePacks = data.packs.filter((p) => packs[p.id] && !p.inProfile).map((p) => p.id);
  const disablePacks = data.packs.filter((p) => !packs[p.id] && p.inProfile).map((p) => p.id);
  const thresholds: Record<string, number> = {};
  for (const [k, v] of Object.entries(limits)) {
    if (v.trim() !== '' && !limitInvalid(v)) thresholds[k] = Number(v);
  }
  return { enablePacks, disablePacks, thresholds, rules: rules.filter((r) => !isNoopAdjustment(r)) };
}

const NO_ADJUSTMENT = (id: string): RuleAdjustment => ({ id, severity: null, disabled: false, shadow: null });

export function currentAdjustment(rules: RuleAdjustment[], id: string): RuleAdjustment {
  return rules.find((r) => r.id === id) ?? NO_ADJUSTMENT(id);
}

export function patchAdjustment(rules: RuleAdjustment[], id: string, patch: Partial<RuleAdjustment>): RuleAdjustment[] {
  return [...rules.filter((r) => r.id !== id), { ...currentAdjustment(rules, id), ...patch }];
}

/** Regra nova (id ou padrão com *) entra só observada; id inválido ou repetido não muda nada. */
export function withNewRule(rules: RuleAdjustment[], typed: string): RuleAdjustment[] {
  const id = typed.trim().toLowerCase();
  if (!id || !/^[a-z0-9_.*:/-]+$/.test(id) || rules.some((r) => r.id === id)) return rules;
  return [...rules, { id, severity: null, disabled: false, shadow: true }];
}

export function toggledSet(set: Set<string>, id: string, on: boolean): Set<string> {
  const next = new Set(set);
  if (on) next.add(id);
  else next.delete(id);
  return next;
}

/** Marca ou desmarca todas as linhas mostradas, preservando as seleções fora da página. */
export function pickShown(picked: Set<string>, shownIds: string[], on: boolean): Set<string> {
  if (on) return new Set([...picked, ...shownIds]);
  const shown = new Set(shownIds);
  return new Set([...picked].filter((id) => !shown.has(id)));
}

/** Última auditoria concluída: dela vem o efeito de cada regra na nota. */
export function latestCompleted(runs: unknown): AuditRunResponse | undefined {
  const list = Array.isArray(runs) ? (runs as AuditRunResponse[]) : [];
  return list.filter((r) => r.status === 'COMPLETED').sort((a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime())[0];
}
