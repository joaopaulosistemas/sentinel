/**
 * Régua única da nota (0 a 100). Todas as telas leem daqui: meta, faixas e cores.
 * Antes, cada tela tinha os seus cortes (75/50 em umas, 80/60/40 em outras) e a mesma nota mudava de cor conforme a tela.
 */
export const TARGET_SCORE = 75;
export const WARN_SCORE = 50;

export type ScoreBand = 'good' | 'warn' | 'bad' | 'none';

function hasNoScore(score: number | null | undefined): score is null | undefined {
  return score === null || score === undefined || Number.isNaN(score);
}

/** Faixa da nota: boa na meta ou acima, atenção a partir de 50, ruim abaixo; sem nota (nunca auditado) é "none". */
export function scoreBand(score: number | null | undefined): ScoreBand {
  if (hasNoScore(score)) return 'none';
  const rounded = Math.round(score);
  if (rounded >= TARGET_SCORE) return 'good';
  if (rounded >= WARN_SCORE) return 'warn';
  return 'bad';
}

const BAND_COLOR: Record<ScoreBand, string> = {
  good: 'var(--s-success-text)',
  warn: 'var(--s-warn-text)',
  bad: 'var(--s-danger-text)',
  none: 'var(--s-text-4)',
};

/** Cor do texto ou da barra para a nota. */
export const scoreColor = (score: number | null | undefined): string => BAND_COLOR[scoreBand(score)];

/** Tipo de etiqueta do Carbon para a nota. */
export function scoreTagType(score: number | null | undefined): 'green' | 'warm-gray' | 'red' | 'gray' {
  const band = scoreBand(score);
  return band === 'good' ? 'green' : band === 'warn' ? 'warm-gray' : band === 'bad' ? 'red' : 'gray';
}
