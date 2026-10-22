export type ReportFormat = 'html' | 'sbom';

export const reportFormats: Array<{ id: ReportFormat; label: string; hint: string; path: string }> = [
  { id: 'html', label: 'Relatório HTML', hint: 'Para ler e compartilhar: nota, achados e como corrigir', path: 'report' },
  { id: 'sbom', label: 'SBOM (CycloneDX)', hint: 'Inventário das dependências, em JSON', path: 'sbom' },
];

/** Endereço do relatório. Relativo por padrão, como o resto do dashboard (o nginx/proxy encaminha /api). */
export function reportUrl(base: string, auditId: number, format: ReportFormat): string {
  const f = reportFormats.find((r) => r.id === format) ?? reportFormats[0];
  return `${base}/api/v1/audits/${auditId}/${f.path}`;
}
