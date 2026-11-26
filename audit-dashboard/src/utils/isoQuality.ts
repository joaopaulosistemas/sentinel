import type { IIsoCharacteristic, IIsoTrendPoint, IsoProfile } from '../types';

/** Características que o Sentinel avalia, para o filtro da lista de achados. */
export const isoFilterOptions: Array<{ value: string; label: string }> = [
  { value: '', label: 'Todas' },
  { value: 'security', label: 'Segurança' },
  { value: 'maintainability', label: 'Manutenibilidade' },
  { value: 'reliability', label: 'Confiabilidade' },
  { value: 'performance-efficiency', label: 'Eficiência de desempenho' },
];

export const isoProfileLabels: Record<IsoProfile, string> = {
  padrao: 'Padrão (pesos iguais)',
  financeiro: 'Financeiro (segurança e confiabilidade pesam mais)',
  interno: 'Interno (manutenibilidade pesa mais)',
};

/** Variação da nota da característica entre os dois últimos pontos da tendência; null se não houver dois. */
export function isoDelta(points: IIsoTrendPoint[] | undefined, id: string): number | null {
  if (!points || points.length < 2) return null;
  const last = points[points.length - 1].notas[id];
  const before = points[points.length - 2].notas[id];
  if (last === undefined || before === undefined) return null;
  return Math.round((last - before) * 10) / 10;
}

export const isoProfileHints: Record<IsoProfile, string> = {
  padrao: 'Pesos iguais para as características avaliadas.',
  financeiro: 'Segurança e confiabilidade pesam mais: indicado para sistemas que lidam com dinheiro.',
  interno: 'Manutenibilidade pesa mais: indicado para ferramentas internas de longa vida.',
};

/** Situação em texto (a cor sozinha não basta): segue a régua única de scoreBand. */
export function isoSituacao(band: 'good' | 'warn' | 'bad' | 'none'): string {
  return band === 'good' ? 'Bom' : band === 'warn' ? 'Atenção' : band === 'bad' ? 'Ruim' : 'Sem nota';
}

/** Variação por extenso para leitor de tela: "subiu 5 pontos", "caiu 1,5 ponto", "sem variação"; "sem histórico" sem dois pontos. */
export function isoDeltaSr(delta: number | null): string {
  if (delta === null) return 'sem histórico para comparar';
  if (delta === 0) return 'sem variação';
  const abs = Math.abs(delta).toLocaleString('pt-BR', { maximumFractionDigits: 1 });
  return `${delta > 0 ? 'subiu' : 'caiu'} ${abs} ${abs === '1' ? 'ponto' : 'pontos'}`;
}

/** "▲ +3,2", "▼ −1,5", "= 0" ou "—" quando não há variação conhecida. */
export function isoDeltaLabel(delta: number | null): string {
  if (delta === null) return '—';
  if (delta === 0) return '= 0';
  const abs = Math.abs(delta).toLocaleString('pt-BR', { maximumFractionDigits: 1 });
  return delta > 0 ? `▲ +${abs}` : `▼ −${abs}`;
}

/** Características avaliadas na ordem de prioridade (a que mais merece atenção primeiro), seguidas das não avaliadas. */
export function orderedCharacteristics(all: IIsoCharacteristic[], priority: string[]): IIsoCharacteristic[] {
  const rank = (id: string) => {
    const i = priority.indexOf(id);
    return i === -1 ? priority.length : i;
  };
  const rated = all.filter((c) => c.avaliada).sort((a, b) => rank(a.id) - rank(b.id));
  return [...rated, ...all.filter((c) => !c.avaliada)];
}
