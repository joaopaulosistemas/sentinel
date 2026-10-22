import type { Category, FindingResponse, FindingsOverview, FindingStatus, Severity } from '../types';

export interface SystemGroup {
  systemId: number;
  systemName: string;
  auditId: number;
  findings: FindingResponse[];
}

/** Agrupa os achados da página por sistema, mantendo a ordem em que cada sistema aparece primeiro. */
export function groupBySystem(items: FindingsOverview['items']): SystemGroup[] {
  const groups = new Map<number, SystemGroup>();
  for (const item of items) {
    const group = groups.get(item.systemId);
    if (group) group.findings.push(item.finding);
    else groups.set(item.systemId, { systemId: item.systemId, systemName: item.systemName, auditId: item.auditId, findings: [item.finding] });
  }
  return [...groups.values()];
}

export const severityLabels: Record<Severity, string> = {
  CRITICAL: 'Crítico',
  HIGH: 'Alto',
  MEDIUM: 'Médio',
  LOW: 'Baixo',
  INFO: 'Info',
};

export const categoryLabels: Record<Category, string> = {
  SECURITY: 'Segurança',
  QUALITY: 'Qualidade',
  ARCHITECTURE: 'Arquitetura',
  DEPENDENCY: 'Dependências',
  OBSERVABILITY: 'Observabilidade',
  PERFORMANCE: 'Desempenho',
};

export const statusLabels: Record<FindingStatus, string> = {
  OPEN: 'Aberto',
  ACKNOWLEDGED: 'Reconhecido',
  FIXED: 'Resolvido',
  SUPPRESSED: 'Suprimido',
};

/** Cores da pílula de severidade (fundo e texto), iguais em tabelas e resumos. */
export const severityPill: Record<Severity, { label: string; bg: string; fg: string }> = {
  CRITICAL: { label: 'Crítica', bg: '#ffd7d9', fg: '#750e13' },
  HIGH: { label: 'Alta', bg: '#ffe0cc', fg: '#6b2d00' },
  MEDIUM: { label: 'Média', bg: '#fcf4d6', fg: '#5a4200' },
  LOW: { label: 'Baixa', bg: '#e5f6ff', fg: '#00417a' },
  INFO: { label: 'Info', bg: '#e0e0e0', fg: '#161616' },
};

/** Opções do filtro de severidade; "Crítica e alta" é o ponto de partida. */
export type SeverityChoice = 'critHigh' | 'all' | Severity;

export const severityChoices: Array<{ id: SeverityChoice; label: string; severities: Severity[] }> = [
  { id: 'critHigh', label: 'Crítica e alta', severities: ['CRITICAL', 'HIGH'] },
  { id: 'all', label: 'Todas', severities: [] },
  { id: 'CRITICAL', label: 'Crítica', severities: ['CRITICAL'] },
  { id: 'HIGH', label: 'Alta', severities: ['HIGH'] },
  { id: 'MEDIUM', label: 'Média', severities: ['MEDIUM'] },
  { id: 'LOW', label: 'Baixa', severities: ['LOW'] },
  { id: 'INFO', label: 'Info', severities: ['INFO'] },
];

/** Opções do filtro de situação; "Abertos" é o ponto de partida. */
export type SituationChoice = 'open' | 'new' | 'acknowledged' | 'suppressed' | 'all';

export const situationChoices: Array<{
  id: SituationChoice;
  label: string;
  /** Como a contagem do rodapé chama esses achados */
  noun: string;
  status?: FindingStatus;
  onlyNew: boolean;
  includeSuppressed: boolean;
}> = [
  { id: 'open', label: 'Abertos', noun: 'abertos', status: 'OPEN', onlyNew: false, includeSuppressed: false },
  { id: 'new', label: 'Novos desde a última auditoria', noun: 'novos', onlyNew: true, includeSuppressed: false },
  { id: 'acknowledged', label: 'Reconhecidos', noun: 'reconhecidos', status: 'ACKNOWLEDGED', onlyNew: false, includeSuppressed: false },
  { id: 'suppressed', label: 'Suprimidos', noun: 'suprimidos', status: 'SUPPRESSED', onlyNew: false, includeSuppressed: true },
  { id: 'all', label: 'Todas', noun: 'achados', onlyNew: false, includeSuppressed: true },
];

export const DEFAULT_SEVERITY: SeverityChoice = 'critHigh';
export const DEFAULT_SITUATION: SituationChoice = 'open';

export interface FilterChip {
  key: 'system' | 'severity' | 'category' | 'situation';
  label: string;
  /** O sistema fica em azul; os demais, em cinza */
  tone: 'blue' | 'gray';
}

export interface ActiveFilters {
  /** Nome do sistema escolhido; vazio = todos */
  systemName?: string;
  /** Mostrar o chip "Todos os sistemas" quando nenhum foi escolhido (só na visão de todos) */
  showSystem: boolean;
  severity: SeverityChoice;
  category?: Category;
  situation: SituationChoice;
}

/** Um chip por filtro ligado, na ordem da tela. Severidade "Todas" e situação "Todas" não geram chip. */
export function activeFilterChips(f: ActiveFilters): FilterChip[] {
  const chips: FilterChip[] = [];
  if (f.showSystem) chips.push({ key: 'system', label: f.systemName || 'Todos os sistemas', tone: 'blue' });
  if (f.severity !== 'all') {
    chips.push({ key: 'severity', label: severityChoices.find((c) => c.id === f.severity)?.label ?? f.severity, tone: 'gray' });
  }
  if (f.category) chips.push({ key: 'category', label: categoryLabels[f.category], tone: 'gray' });
  if (f.situation !== 'all') {
    chips.push({ key: 'situation', label: situationChoices.find((c) => c.id === f.situation)?.label ?? f.situation, tone: 'gray' });
  }
  return chips;
}

/** "críticos e altos", "críticos"... usado no rodapé ("142 abertos (críticos e altos)"). */
export function severityPhrase(choice: SeverityChoice): string {
  switch (choice) {
    case 'critHigh':
      return 'críticos e altos';
    case 'all':
      return '';
    case 'CRITICAL':
      return 'críticos';
    case 'HIGH':
      return 'altos';
    case 'MEDIUM':
      return 'médios';
    case 'LOW':
      return 'baixos';
    default:
      return 'informativos';
  }
}

/** Texto do rodapé da lista: "Mostrando 9 de 142 abertos (críticos e altos) em 4 sistemas". */
export function footerText(shown: number, total: number, situation: SituationChoice, severity: SeverityChoice, systems: number): string {
  const noun = situationChoices.find((c) => c.id === situation)?.noun ?? 'achados';
  const phrase = severityPhrase(severity);
  const scope = systems > 0 ? ` em ${systems} ${systems === 1 ? 'sistema' : 'sistemas'}` : '';
  return `Mostrando ${shown} de ${total} ${noun}${phrase ? ` (${phrase})` : ''}${scope}`;
}
