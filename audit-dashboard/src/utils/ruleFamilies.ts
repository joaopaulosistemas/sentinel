import type { IEngineAnalyzerState, IEngineConfig, IEnginePackState, EngineSide } from '../types';
import type { IRuleRow } from './ruleCatalog';

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

export interface IPackGroup {
  side: PackSide;
  title: string;
  packs: IEnginePackState[];
}

/** Agrupa os pacotes por lado, na ordem back-end, front-end, transversais; grupo vazio não aparece. */
export function groupPacks(packs: IEnginePackState[]): IPackGroup[] {
  return SIDE_ORDER.map((side) => ({ side, title: SIDE_TITLE[side], packs: packs.filter((p) => sideOfPack(p.id) === side) })).filter((g) => g.packs.length > 0);
}

/** Quantas regras cada pacote declara no catálogo. Pacote só de ferramentas externas não aparece aqui. */
export function ruleCountByPack(catalog: IEngineConfig['catalog']): Record<string, number> {
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
export function familyOf(row: Pick<IRuleRow, 'category'>): string {
  const category = row.category?.trim().toLowerCase();
  return category ? category : NO_FAMILY;
}

export function familyLabel(key: string): string {
  return FAMILY_LABEL[key] ?? key.charAt(0).toUpperCase() + key.slice(1);
}

export interface IFamilyTab {
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
export function familyTabs(rows: IRuleRow[], adjustedIds: Set<string>): IFamilyTab[] {
  const byKey = new Map<string, IFamilyTab>();
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
export function rowsOfActivePacks(rows: IRuleRow[], packs: Record<string, boolean>): IRuleRow[] {
  return rows.filter((r) => r.pack === null || !(r.pack in packs) || packs[r.pack]);
}

interface IHiddenAdjustedInput {
  rows: IRuleRow[];
  packs: Record<string, boolean>;
  adjustedIds: Set<string>;
}

/** Quantas regras ajustadas ficam escondidas porque o pacote delas está desligado. */
export function hiddenAdjustedCount({ rows, packs, adjustedIds }: IHiddenAdjustedInput): number {
  const visible = new Set(rowsOfActivePacks(rows, packs).map((r) => r.id));
  return rows.filter((r) => adjustedIds.has(r.id) && !visible.has(r.id)).length;
}

/** Nome de cada lado (back, front, comum) na tela. */
export const SIDE_NAME: Record<EngineSide, string> = { back: 'Back-end', front: 'Front-end', common: 'Infra e comum' };
export const SIDES: EngineSide[] = ['back', 'front', 'common'];

/** Lado da regra: o que a API informa; sem isso (API antiga), o do pacote, com "transversal" virando comum. */
export function sideOfRule(row: Pick<IRuleRow, 'side' | 'pack'>): EngineSide {
  if (row.side) return row.side;
  const legacy = row.pack ? sideOfPack(row.pack) : 'both';
  return legacy === 'both' ? 'common' : legacy;
}

type Sided = Array<{ pack: string; side?: EngineSide }> | undefined;

/** Quantas regras há em cada lado, no catálogo de pacotes. */
export function sideTotals(catalog: Sided): Record<EngineSide, number> {
  const out: Record<EngineSide, number> = { back: 0, front: 0, common: 0 };
  for (const c of catalog ?? []) out[sideOfRule({ side: c.side, pack: c.pack })] += 1;
  return out;
}

export interface ISidePack {
  pack: IEnginePackState;
  /** Regras do pacote neste lado. */
  count: number;
  /** Regras do mesmo pacote nos outros lados (para avisar que o pacote é misto). */
  others: Array<{ side: EngineSide; count: number }>;
}

export interface ISideGroup {
  side: EngineSide;
  title: string;
  packs: ISidePack[];
  /** Regras do lado somando os pacotes. */
  rules: number;
}

/**
 * Pacotes por lado. Um pacote com regras de dois lados aparece nos dois, cada vez com a contagem do lado; pacote sem regra
 * no catálogo (só ferramentas externas) fica no lado que o mapa antigo dá a ele. Grupo vazio não aparece.
 */
export function packsBySide(packs: IEnginePackState[], catalog: Sided): ISideGroup[] {
  const counts = new Map<string, Record<EngineSide, number>>();
  for (const c of catalog ?? []) {
    const packCounts = counts.get(c.pack) ?? { back: 0, front: 0, common: 0 };
    packCounts[sideOfRule({ side: c.side, pack: c.pack })] += 1;
    counts.set(c.pack, packCounts);
  }
  const groups = SIDES.map((side): ISideGroup => ({ side, title: SIDE_NAME[side], packs: [], rules: 0 }));
  for (const p of packs) {
    const packCounts = counts.get(p.id);
    if (!packCounts) {
      const legacy = sideOfPack(p.id);
      groups[SIDES.indexOf(legacy === 'both' ? 'common' : legacy)].packs.push({ pack: p, count: 0, others: [] });
      continue;
    }
    for (const side of SIDES) {
      if (packCounts[side] === 0) continue;
      const others = SIDES.filter((o) => o !== side && packCounts[o] > 0).map((o) => ({ side: o, count: packCounts[o] }));
      const group = groups[SIDES.indexOf(side)];
      group.packs.push({ pack: p, count: packCounts[side], others });
      group.rules += packCounts[side];
    }
  }
  return groups.filter((g) => g.packs.length > 0);
}

/** Analisadores por lado; sem a informação (API antiga) ficam em "Infra e comum". */
export function analyzersBySide(analyzers: IEngineAnalyzerState[]): Array<{ side: EngineSide; title: string; items: IEngineAnalyzerState[] }> {
  return SIDES.map((side) => ({ side, title: SIDE_NAME[side], items: analyzers.filter((a) => (a.side ?? 'common') === side) })).filter((g) => g.items.length > 0);
}

/** Atalho de escopo: quais lados estão desligados. */
export type ScopePreset = 'full' | 'back' | 'front';

export function presetOf(sidesOff: string[]): ScopePreset | null {
  const off = new Set(sidesOff);
  if (off.size === 0) return 'full';
  if (off.size === 1 && off.has('front')) return 'back';
  if (off.size === 1 && off.has('back')) return 'front';
  return null;
}

export function sidesOffOf(preset: ScopePreset): Array<'back' | 'front'> {
  return preset === 'full' ? [] : preset === 'back' ? ['front'] : ['back'];
}

/** O lado desta regra ou analisador está desligado? "common" nunca está. */
export function sideIsOff(side: EngineSide, sidesOff: string[]): boolean {
  return side !== 'common' && sidesOff.includes(side);
}
