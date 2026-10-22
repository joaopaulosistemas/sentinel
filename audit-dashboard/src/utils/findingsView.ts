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

export type FilterKey = 'system' | 'severity' | 'category' | 'status' | 'onlyNew' | 'includeSuppressed';

export interface FilterChip {
  key: FilterKey;
  label: string;
}

export interface ActiveFilters {
  systemName?: string;
  severity?: Severity;
  category?: Category;
  status?: FindingStatus;
  onlyNew?: boolean;
  includeSuppressed?: boolean;
}

/** Um chip por filtro ligado, na ordem em que aparecem na tela, para o usuário ver e remover cada um. */
export function activeFilterChips(f: ActiveFilters): FilterChip[] {
  const chips: FilterChip[] = [];
  if (f.systemName) chips.push({ key: 'system', label: `Sistema: ${f.systemName}` });
  if (f.severity) chips.push({ key: 'severity', label: `Severidade: ${severityLabels[f.severity]}` });
  if (f.category) chips.push({ key: 'category', label: `Categoria: ${categoryLabels[f.category]}` });
  if (f.status) chips.push({ key: 'status', label: `Status: ${statusLabels[f.status]}` });
  if (f.onlyNew) chips.push({ key: 'onlyNew', label: 'Somente novos' });
  if (f.includeSuppressed) chips.push({ key: 'includeSuppressed', label: 'Com suprimidos' });
  return chips;
}
