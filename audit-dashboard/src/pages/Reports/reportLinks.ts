export type ReportFormat = 'html' | 'pdf' | 'csv' | 'sbom';
export type ReportType = 'executivo' | 'tecnico' | 'comparativo';
export type ReportPeriod = 0 | 7 | 30 | 90;

export const reportTypes: Array<{ id: ReportType; label: string; hint: string }> = [
  { id: 'executivo', label: 'Executivo', hint: 'Nota, tendência e riscos em uma página' },
  { id: 'tecnico', label: 'Técnico', hint: 'Todos os achados com arquivo, linha e correção' },
  { id: 'comparativo', label: 'Comparativo', hint: 'Evolução entre auditorias' },
];

export const reportPeriods: Array<{ id: ReportPeriod; label: string }> = [
  { id: 30, label: 'Últimos 30 dias' },
  { id: 7, label: 'Últimos 7 dias' },
  { id: 90, label: 'Trimestre atual (90 dias)' },
  { id: 0, label: 'Todo o histórico' },
];

export const reportFormats: Array<{ id: ReportFormat; label: string; hint: string; path: string }> = [
  { id: 'pdf', label: 'PDF', hint: 'Arquivo pronto para anexar e arquivar', path: 'report.pdf' },
  { id: 'html', label: 'HTML', hint: 'Para ler na tela e compartilhar o link', path: 'report' },
  { id: 'csv', label: 'Planilha (CSV)', hint: 'Para abrir no Excel', path: 'findings.csv' },
  { id: 'sbom', label: 'SBOM (CycloneDX)', hint: 'Inventário das dependências, em JSON', path: 'sbom' },
];

/** PDF, HTML e planilha valem para qualquer relatório; o SBOM só existe para um sistema, no técnico. */
export function formatsFor(type: ReportType, singleSystem = true): typeof reportFormats {
  return reportFormats.filter((f) => f.id !== 'sbom' || (type === 'tecnico' && singleSystem));
}

/** Endereço do relatório. Relativo por padrão, como o resto do dashboard (o nginx/proxy encaminha /api). */
export function reportUrl(
  base: string,
  auditId: number,
  format: ReportFormat,
  opts: { type?: ReportType; days?: ReportPeriod } = {},
): string {
  const f = reportFormats.find((r) => r.id === format) ?? reportFormats[0];
  const url = `${base}/api/v1/audits/${auditId}/${f.path}`;
  if (f.id !== 'html' && f.id !== 'pdf') return url;
  const params = new URLSearchParams();
  if (opts.type && opts.type !== 'tecnico') params.set('type', opts.type);
  if (opts.type === 'comparativo' && opts.days) params.set('days', String(opts.days));
  const qs = params.toString();
  return qs ? `${url}?${qs}` : url;
}

/** Relatório de todos os sistemas (ou de um só, quando a planilha não é do técnico). */
export function portfolioUrl(
  base: string,
  type: ReportType,
  format: Exclude<ReportFormat, 'sbom'>,
  opts: { days?: ReportPeriod; systemId?: number } = {},
): string {
  const params = new URLSearchParams({ type, format });
  if (opts.days) params.set('days', String(opts.days));
  if (opts.systemId) params.set('systemId', String(opts.systemId));
  return `${base}/api/v1/reports/portfolio?${params.toString()}`;
}

/**
 * Endereço final do relatório conforme a escolha da tela: sem sistema, o consolidado; com sistema,
 * a auditoria mais recente dele (a planilha de tipos que não são o técnico vem do consolidado filtrado).
 */
export function chosenReportUrl(
  base: string,
  choice: { systemId: number | null; auditId: number | null; type: ReportType; format: ReportFormat; days: ReportPeriod },
): string | null {
  const { systemId, auditId, type, format, days } = choice;
  if (systemId === null) {
    return format === 'sbom' ? null : portfolioUrl(base, type, format, { days });
  }
  if (format === 'csv' && type !== 'tecnico') return portfolioUrl(base, type, 'csv', { days, systemId });
  return auditId ? reportUrl(base, auditId, format, { type, days }) : null;
}

const periodNames: Record<ReportPeriod, string> = { 7: '7 dias', 30: '30 dias', 90: 'trimestre', 0: 'todo o histórico' };

/** Nome do relatório na lista: "Executivo · 30 dias", "Técnico · auditoria #214". */
export function reportTitle(type: ReportType, days: ReportPeriod, auditId: number | null): string {
  const label = reportTypes.find((t) => t.id === type)?.label ?? 'Técnico';
  if (type === 'tecnico') return `${label} · ${auditId ? `auditoria #${auditId}` : 'últimas auditorias'}`;
  return `${label} · ${periodNames[days]}`;
}
