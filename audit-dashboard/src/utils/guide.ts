import type { GuideRule, GuideSeverity, GuideStack } from '../types';

export type SeverityFilter = 'todas' | GuideSeverity;

export const SEVERITY_LABEL: Record<GuideSeverity, string> = { alta: 'Alta', media: 'Média', baixa: 'Baixa' };

const SEVERITY_ORDER: Record<GuideSeverity, number> = { alta: 0, media: 1, baixa: 2 };

/** Regras da tecnologia (na ordem da API: específicas primeiro), já com os dados completos de cada uma. */
export function rulesOfStack(stack: GuideStack, rules: GuideRule[]): Array<GuideRule & { own: boolean }> {
  const byId = new Map(rules.map((r) => [r.id, r]));
  const out: Array<GuideRule & { own: boolean }> = [];
  for (const ref of stack.rules) {
    const rule = byId.get(ref.id);
    if (rule) out.push({ ...rule, own: ref.own });
  }
  return out;
}

/** Filtra por severidade e por texto (título, id, CWE e ação); mais grave primeiro, depois por título. */
export function filterRules<T extends GuideRule>(rules: T[], severity: SeverityFilter, query: string): T[] {
  const q = query.trim().toLowerCase();
  return rules
    .filter((r) => severity === 'todas' || r.severity === severity)
    .filter((r) => q === '' || `${r.title} ${r.id} ${r.cwe} ${r.action}`.toLowerCase().includes(q))
    .sort((a, b) => SEVERITY_ORDER[a.severity] - SEVERITY_ORDER[b.severity] || a.title.localeCompare(b.title, 'pt-BR'));
}

export type Coverage = { label: string; tone: 'good' | 'warn' | 'bad' };

/** Mínimo de regras para dizer que o Sentinel cobre a tecnologia: específicas, ou herdadas se for linguagem base. */
export const MIN_RULES = 6;

export function coverageOf(stack: GuideStack): Coverage {
  const relevant = stack.baseLanguage ? stack.inheritedRules : stack.ownRules;
  if (relevant >= MIN_RULES) return { label: 'Boa', tone: 'good' };
  if (relevant > 0) return { label: 'Parcial', tone: 'warn' };
  return { label: 'Só regras gerais', tone: 'bad' };
}

export interface Criterion {
  title: string;
  points: number;
  done: boolean;
  detail: string;
}

/** Critérios da nota do guia (somam 10). Só vale o que foi feito de fato: o que depende de pessoas começa pendente. */
export function criteriaOf(stack: GuideStack): Criterion[] {
  const relevant = stack.baseLanguage ? stack.inheritedRules : stack.ownRules;
  const practicesOk = stack.practices.length > 0 && stack.practices.every((p) => p.doIt !== '' && p.avoid !== '');
  return [
    { title: 'Regras lidas do catálogo real', points: 1.5, done: stack.rules.length > 0, detail: 'Cada regra mostrada vem dos pacotes do Sentinel.' },
    { title: 'Boa prática com “faça” e “evite”', points: 1, done: practicesOk, detail: 'Cada prática diz o que fazer e o que evitar.' },
    {
      title: 'Comandos conferidos no CI do Sentinel',
      points: 1.5,
      done: stack.commandsVerified,
      detail: stack.commandsVerified ? 'Os comandos batem com o que o CI do próprio Sentinel executa.' : 'Padrão do ecossistema; confirme em um sistema real.',
    },
    {
      title: 'Regras suficientes para a tecnologia',
      points: 1.5,
      done: relevant >= MIN_RULES,
      detail: relevant >= MIN_RULES ? `${relevant} regras.` : `${relevant} regras; a meta é ${MIN_RULES} ou mais.`,
    },
    { title: 'Práticas conferidas na documentação oficial', points: 1.5, done: stack.review.docs, detail: 'Confira cada prática com a documentação do fornecedor e marque review.docs no stacks.yml.' },
    { title: 'Revisado por pessoas da equipe', points: 1, done: stack.review.team, detail: 'Duas ou três pessoas leem e cortam o que ninguém defende (review.team).' },
    { title: 'Testado com a IA numa tarefa real', points: 1, done: stack.review.ai, detail: 'Rodar uma tarefa com o CLAUDE.md e ajustar pelo que a IA errar (review.ai).' },
    { title: 'Exceção com motivo e prazo', points: 1, done: true, detail: 'O .sentinel.yml aceita rule, path, reason e until.' },
  ];
}

export function gradeOf(criteria: Criterion[]): number {
  return criteria.reduce((sum, c) => sum + (c.done ? c.points : 0), 0);
}

/** Número em pt-BR com vírgula: 6,5. */
export function formatGrade(n: number): string {
  return String(n).replace('.', ',');
}
