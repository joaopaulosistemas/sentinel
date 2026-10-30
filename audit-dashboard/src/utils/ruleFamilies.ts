import type { EngineConfig, EnginePackState } from '../types';
import type { RuleRow } from './ruleCatalog';

/** De que lado da aplicação o pacote trata. "both" cobre o que serve aos dois e os pacotes que não conhecemos. */
export type PackSide = 'back' | 'front' | 'both';

const SIDE_OF_PACK: Record<string, PackSide> = {
  'core-quarkus': 'back',
  'core-spring': 'back',
  'core-persistencia': 'back',
  'core-corporativo': 'back',
  'java-cert-sonar': 'back',
  'java-google-style': 'back',
  'core-frontend': 'front',
  'core-vue': 'front',
  'ts-clean-code': 'front',
};

export const SIDE_TITLE: Record<PackSide, string> = { back: 'Back-end', front: 'Front-end', both: 'Transversais' };
const SIDE_ORDER: PackSide[] = ['back', 'front', 'both'];

export function sideOfPack(id: string): PackSide {
  return SIDE_OF_PACK[id] ?? 'both';
}

export interface PackGroup {
  side: PackSide;
  title: string;
  packs: EnginePackState[];
}

/** Agrupa os pacotes por lado, na ordem back-end, front-end, transversais; grupo vazio não aparece. */
export function groupPacks(packs: EnginePackState[]): PackGroup[] {
  return SIDE_ORDER.map((side) => ({ side, title: SIDE_TITLE[side], packs: packs.filter((p) => sideOfPack(p.id) === side) })).filter((g) => g.packs.length > 0);
}

/** Quantas regras cada pacote declara no catálogo. Pacote só de ferramentas externas não aparece aqui. */
export function ruleCountByPack(catalog: EngineConfig['catalog']): Record<string, number> {
  const counts: Record<string, number> = {};
  for (const c of catalog ?? []) counts[c.pack] = (counts[c.pack] ?? 0) + 1;
  return counts;
}

/** Limite que só vale para um lado. Os demais valem para Java e TypeScript/JavaScript e ficam em "Transversais". */
const FRONT_ONLY_LIMITS = new Set(['max-component-lines']);

export function limitSide(key: string): PackSide {
  return FRONT_ONLY_LIMITS.has(key) ? 'front' : 'both';
}

export const NO_FAMILY = 'none';

const FAMILY_LABEL: Record<string, string> = {
  security: 'Segurança',
  quality: 'Qualidade',
  architecture: 'Arquitetura',
  performance: 'Desempenho',
  reliability: 'Confiabilidade',
  dependency: 'Dependências',
  observability: 'Observabilidade',
  [NO_FAMILY]: 'Sem família',
};
const FAMILY_ORDER = Object.keys(FAMILY_LABEL);

/** Família da regra: a categoria que ela declara no pacote. Sem categoria, "Sem família". */
export function familyOf(row: Pick<RuleRow, 'category'>): string {
  const c = row.category?.trim().toLowerCase();
  return c ? c : NO_FAMILY;
}

export function familyLabel(key: string): string {
  return FAMILY_LABEL[key] ?? key.charAt(0).toUpperCase() + key.slice(1);
}

export interface FamilyTab {
  key: string;
  name: string;
  total: number;
  /** Regras da família que apareceram na última auditoria. */
  seen: number;
  adjusted: number;
}

function familyRank(key: string): number {
  const i = FAMILY_ORDER.indexOf(key);
  if (key === NO_FAMILY) return FAMILY_ORDER.length + 1;
  return i === -1 ? FAMILY_ORDER.length : i;
}

/** Uma aba por família que tem regra, na ordem de peso na nota; "Sem família" por último. */
export function familyTabs(rows: RuleRow[], adjustedIds: Set<string>): FamilyTab[] {
  const byKey = new Map<string, FamilyTab>();
  for (const r of rows) {
    const key = familyOf(r);
    const tab = byKey.get(key) ?? { key, name: familyLabel(key), total: 0, seen: 0, adjusted: 0 };
    tab.total += 1;
    if (r.count > 0) tab.seen += 1;
    if (adjustedIds.has(r.id)) tab.adjusted += 1;
    byKey.set(key, tab);
  }
  return [...byKey.values()].sort((a, b) => familyRank(a.key) - familyRank(b.key) || a.key.localeCompare(b.key));
}

/** Só as regras dos pacotes ligados aparecem; regra sem pacote (adicionada pelo id) ou de pacote desconhecido sempre aparece. */
export function rowsOfActivePacks(rows: RuleRow[], packs: Record<string, boolean>): RuleRow[] {
  return rows.filter((r) => r.pack === null || !(r.pack in packs) || packs[r.pack]);
}

/** Quantas regras ajustadas ficam escondidas porque o pacote delas está desligado. */
export function hiddenAdjustedCount(rows: RuleRow[], packs: Record<string, boolean>, adjustedIds: Set<string>): number {
  const visible = new Set(rowsOfActivePacks(rows, packs).map((r) => r.id));
  return rows.filter((r) => adjustedIds.has(r.id) && !visible.has(r.id)).length;
}
