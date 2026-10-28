import type { ScoreGroup } from '../types';

/** Liga ou desliga uma regra na lista da simulação "e se", sem repetir e sem alterar a lista recebida. */
export function toggleRule(rules: string[], rule: string): string[] {
  return rules.includes(rule) ? rules.filter((r) => r !== rule) : [...rules, rule];
}

/** Frase curta sobre o efeito da regra na nota: a do teto é a que mais surpreende. */
export function groupHint(group: ScoreGroup): string {
  if (group.capped) {
    return `Teto: ${group.count} ocorrências somariam ${group.raw}, mas só ${group.counted} entram. Só some da nota quando todas forem corrigidas.`;
  }
  if (group.severity === 'CRITICAL' || group.severity === 'HIGH') {
    return 'Sem teto: cada ocorrência tira o valor inteiro.';
  }
  return `${group.count} ${group.count === 1 ? 'ocorrência' : 'ocorrências'}, ${group.counted} ponto${group.counted === 1 ? '' : 's'}.`;
}

/** Diferença entre a nota simulada e a real, com sinal (ex.: "+3,2"); vazio quando não há diferença. */
export function deltaLabel(before: number, after: number): string {
  const delta = Math.round((after - before) * 10) / 10;
  if (delta === 0) return '';
  const text = Math.abs(delta).toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
  return `${delta > 0 ? '+' : '−'}${text}`;
}
