import type { IIsoCharacteristic, IIsoTrendPoint, IsoProfile } from '../types';

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

/** "▲ +3,2", "▼ −1,5" ou "= 0"; vazio sem variação conhecida. */
export function isoDeltaLabel(delta: number | null): string {
  if (delta === null) return '';
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
