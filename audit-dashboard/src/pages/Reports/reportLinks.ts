export type ReportFormat = 'html' | 'pdf' | 'csv' | 'sbom';
export type ReportType = 'executivo' | 'tecnico' | 'comparativo';
export type ReportPeriod = 0 | 7 | 30 | 90;

export const reportTypes: Array<{ id: ReportType; label: string; hint: string }> = [
  { id: 'executivo', label: 'Executivo', hint: 'Nota, meta e o que corrigir primeiro, em uma página' },
  { id: 'tecnico', label: 'Técnico', hint: 'Todos os achados com arquivo, linha e como corrigir' },
  { id: 'comparativo', label: 'Comparativo', hint: 'Evolução da nota entre as auditorias do sistema' },
];

export const reportPeriods: Array<{ id: ReportPeriod; label: string }> = [
  { id: 30, label: 'Últimos 30 dias' },
  { id: 7, label: 'Últimos 7 dias' },
  { id: 90, label: 'Últimos 90 dias' },
  { id: 0, label: 'Todo o histórico' },
];

export const reportFormats: Array<{ id: ReportFormat; label: string; hint: string; path: string }> = [
  { id: 'html', label: 'Relatório HTML', hint: 'Para ler na tela e compartilhar o link', path: 'report' },
  { id: 'pdf', label: 'Relatório PDF', hint: 'Arquivo pronto para anexar e arquivar', path: 'report.pdf' },
  { id: 'csv', label: 'Planilha (CSV)', hint: 'Todos os achados, para abrir no Excel', path: 'findings.csv' },
  { id: 'sbom', label: 'SBOM (CycloneDX)', hint: 'Inventário das dependências, em JSON', path: 'sbom' },
];

/** CSV e SBOM só existem para o relatório técnico; o executivo e o comparativo saem em HTML ou PDF. */
export function formatsFor(type: ReportType): typeof reportFormats {
  return type === 'tecnico' ? reportFormats : reportFormats.filter((f) => f.id === 'html' || f.id === 'pdf');
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
