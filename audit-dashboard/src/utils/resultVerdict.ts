import type { Category, FixPlan, RiskScoreDto } from '../types';

export const TARGET_SCORE = 75;

export interface Verdict {
  tone: 'good' | 'warn' | 'bad';
  headline: string;
  detail: string;
}

/** Frase do veredito: diz o que pesa e o que fazer, sem exigir que a pessoa leia a lista inteira. */
export function verdict(plan: FixPlan): Verdict {
  const now = Math.round(plan.scoreNow);
  const tone = now >= TARGET_SCORE ? 'good' : now >= 50 ? 'warn' : 'bad';
  if (plan.criticalCount > 0) {
    const after = Math.round(plan.scoreIfCriticalFixed);
    const plural = plan.criticalCount === 1 ? 'problema crítico' : 'problemas críticos';
    const meta =
      after >= TARGET_SCORE ? ` e a nota passa da meta de ${TARGET_SCORE}` : ` (meta: ${TARGET_SCORE})`;
    return {
      tone,
      headline: `${plan.criticalCount} ${plural} para resolver primeiro`,
      detail: `Corrigindo só os críticos, a nota estimada vai de ${now} para ${after}${meta}.`,
    };
  }
  if (plan.items.length === 0) {
    return { tone, headline: 'Nada a corrigir', detail: 'Nenhum achado ativo pesa na nota.' };
  }
  const top = plan.items[0];
  return {
    tone,
    headline: now >= TARGET_SCORE ? 'Dentro da meta, sem problemas críticos' : 'Sem críticos, mas abaixo da meta',
    detail: `O maior ganho vem de "${top.title}": a nota sobe ${top.scoreGain.toFixed(1)} ponto${top.scoreGain === 1 ? '' : 's'}.`,
  };
}

export const categoryLabels: Record<Category, string> = {
  SECURITY: 'Segurança',
  QUALITY: 'Qualidade',
  ARCHITECTURE: 'Arquitetura',
  DEPENDENCY: 'Dependências',
  OBSERVABILITY: 'Observabilidade',
  PERFORMANCE: 'Desempenho',
};

const scoreKey: Record<Category, keyof RiskScoreDto> = {
  SECURITY: 'security',
  QUALITY: 'quality',
  ARCHITECTURE: 'architecture',
  DEPENDENCY: 'dependencies',
  OBSERVABILITY: 'observability',
  PERFORMANCE: 'performance',
};

/** Categorias com a nota de cada uma, da pior para a melhor. Sem nota (dimensão ausente) não entra. */
export function categoriesByScore(score: RiskScoreDto | null): Array<{ category: Category; label: string; score: number }> {
  if (!score) return [];
  return (Object.keys(scoreKey) as Category[])
    .map((category) => ({ category, label: categoryLabels[category], score: score[scoreKey[category]] }))
    .filter((c): c is { category: Category; label: string; score: number } => typeof c.score === 'number')
    .sort((a, b) => a.score - b.score);
}
