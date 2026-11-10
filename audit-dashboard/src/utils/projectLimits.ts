/** Organiza os limites da análise e o mapa do projeto para a tela da Visão geral. */

export interface ILimitGroup {
  id: string;
  title: string;
  items: string[];
}

export interface ILanguageShare {
  name: string;
  files: number;
  percent: number;
}

const ANALYZER_PREFIX = 'Analisador ';
const TRUNCATED_PREFIX = 'Repositório muito grande';
const AREA_SEPARATOR = /^([^:]{1,60}): (.+)$/;
const MAX_LANGUAGES = 6;
const PERCENT = 100;

/** Agrupa as frases soltas da API: analisadores que não rodaram, uma área de análise por vez e o restante. */
export function groupLimits(unverified: string[]): ILimitGroup[] {
  const analyzers: string[] = [];
  const mapLimits: string[] = [];
  const others: string[] = [];
  const areas = new Map<string, string[]>();

  for (const entry of unverified) {
    if (entry.startsWith(ANALYZER_PREFIX)) {
      analyzers.push(entry.slice(ANALYZER_PREFIX.length));
    } else if (entry.startsWith(TRUNCATED_PREFIX)) {
      mapLimits.push(entry);
    } else {
      const match = AREA_SEPARATOR.exec(entry);
      if (match) {
        areas.set(match[1], [...(areas.get(match[1]) ?? []), match[2]]);
      } else {
        others.push(entry);
      }
    }
  }

  const groups: ILimitGroup[] = [];
  if (analyzers.length > 0) groups.push({ id: 'analisadores', title: 'Analisadores que não rodaram por inteiro', items: analyzers });
  for (const [label, items] of areas) groups.push({ id: `area-${label}`, title: label, items });
  if (mapLimits.length > 0) groups.push({ id: 'mapa', title: 'Alcance do mapa do projeto', items: mapLimits });
  if (others.length > 0) groups.push({ id: 'outros', title: 'Outros pontos', items: others });
  return groups;
}

/** Linguagens por arquivos, da maior para a menor; o que passa do limite vira "Outras". */
export function languageShares(languages: Record<string, number>): ILanguageShare[] {
  const entries = Object.entries(languages)
    .filter(([, files]) => files > 0)
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]));
  const total = entries.reduce((sum, [, files]) => sum + files, 0);
  if (total === 0) return [];

  const shown = entries.slice(0, MAX_LANGUAGES);
  const rest = entries.slice(MAX_LANGUAGES).reduce((sum, [, files]) => sum + files, 0);
  const shares = shown.map(([name, files]) => ({ name, files, percent: (files / total) * PERCENT }));
  if (rest > 0) shares.push({ name: 'Outras', files: rest, percent: (rest / total) * PERCENT });
  return shares;
}

/** Arquivos de teste para cada 100 de código; null quando não há código. */
export function testRatioPercent(sourceFiles: number, testFiles: number): number | null {
  if (sourceFiles <= 0) return null;
  return Math.round((testFiles / sourceFiles) * PERCENT);
}
