import type { IAuditRunResponse, IEngineConfig, IEngineTuning, IRuleAdjustment } from '../types';
import { isNoopAdjustment } from './ruleCatalog';

export type TuningSlice = Pick<IEngineTuning, 'enablePacks' | 'disablePacks' | 'thresholds' | 'rules'> & { disableSides: Array<'back' | 'front'> };

/** Limite digitado: vazio vale "padrão do motor"; o resto precisa ser inteiro ≥ 1. */
export function limitInvalid(value: string | undefined): boolean {
  if (value === undefined || value.trim() === '') return false;
  const parsed = Number(value);
  return !(Number.isInteger(parsed) && parsed >= 1);
}

/** O que a tela edita (pacotes, limites, regras, lados desligados) na forma que o servidor guarda. */
export interface ISliceInput {
  data: IEngineConfig;
  packs: Record<string, boolean>;
  limits: Record<string, string>;
  rules: IRuleAdjustment[];
  sidesOff?: Array<'back' | 'front'>;
}

export function sliceOf({ data, packs, limits, rules, sidesOff = [] }: ISliceInput): TuningSlice {
  const enablePacks = data.packs.filter((p) => packs[p.id] && !p.inProfile).map((p) => p.id);
  const disablePacks = data.packs.filter((p) => !packs[p.id] && p.inProfile).map((p) => p.id);
  const thresholds: Record<string, number> = {};
  for (const [k, v] of Object.entries(limits)) {
    if (v.trim() !== '' && !limitInvalid(v)) thresholds[k] = Number(v);
  }
  return { enablePacks, disablePacks, thresholds, rules: rules.filter((r) => !isNoopAdjustment(r)), disableSides: [...sidesOff] };
}

const NO_ADJUSTMENT = (id: string): IRuleAdjustment => ({ id, severity: null, disabled: false, shadow: null });

export function currentAdjustment(rules: IRuleAdjustment[], id: string): IRuleAdjustment {
  return rules.find((r) => r.id === id) ?? NO_ADJUSTMENT(id);
}

export interface IPatchAdjustmentInput {
  rules: IRuleAdjustment[];
  id: string;
  patch: Partial<IRuleAdjustment>;
}

export function patchAdjustment({ rules, id, patch }: IPatchAdjustmentInput): IRuleAdjustment[] {
  return [...rules.filter((r) => r.id !== id), { ...currentAdjustment(rules, id), ...patch }];
}

/** Regra nova (id ou padrão com *) entra só observada; id inválido ou repetido não muda nada. */
export function withNewRule(rules: IRuleAdjustment[], typed: string): IRuleAdjustment[] {
  const id = typed.trim().toLowerCase();
  if (!isAcceptableNewRuleId(id, rules)) return rules;
  return [...rules, { id, severity: null, disabled: false, shadow: true }];
}

/** Id preenchido, só com os caracteres permitidos e ainda não ajustado. */
function isAcceptableNewRuleId(id: string, rules: IRuleAdjustment[]): boolean {
  const filled = id !== '';
  const hasAllowedCharsOnly = /^[a-z0-9_.*:/-]+$/.test(id);
  const alreadyAdjusted = rules.some((r) => r.id === id);
  return filled && hasAllowedCharsOnly && !alreadyAdjusted;
}

/** Cópia do conjunto com o id dentro. */
export function setWith(set: Set<string>, id: string): Set<string> {
  return new Set(set).add(id);
}

/** Cópia do conjunto sem o id. */
export function setWithout(set: Set<string>, id: string): Set<string> {
  const next = new Set(set);
  next.delete(id);
  return next;
}

/** Marca todas as linhas mostradas, preservando as seleções fora da página. */
export function pickAllShown(picked: Set<string>, shownIds: string[]): Set<string> {
  return new Set([...picked, ...shownIds]);
}

/** Desmarca todas as linhas mostradas, preservando as seleções fora da página. */
export function unpickAllShown(picked: Set<string>, shownIds: string[]): Set<string> {
  const shown = new Set(shownIds);
  return new Set([...picked].filter((id) => !shown.has(id)));
}

/** Última auditoria concluída: dela vem o efeito de cada regra na nota. */
export function latestCompleted(runs: unknown): IAuditRunResponse | undefined {
  const list = Array.isArray(runs) ? (runs as IAuditRunResponse[]) : [];
  return list.filter((r) => r.status === 'COMPLETED').sort((a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime())[0];
}
