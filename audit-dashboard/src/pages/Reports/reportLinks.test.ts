import { describe, expect, it } from 'vitest';
import { chosenReportUrl, formatsFor, portfolioUrl, reportTitle, reportUrl } from './reportLinks';

describe('endereço do relatório', () => {
  it('usa caminho relativo quando não há host configurado', () => {
    expect(reportUrl({ base: '', auditId: 215, format: 'html' })).toBe('/api/v1/audits/215/report');
    expect(reportUrl({ base: '', auditId: 215, format: 'sbom' })).toBe('/api/v1/audits/215/sbom');
    expect(reportUrl({ base: '', auditId: 215, format: 'csv' })).toBe('/api/v1/audits/215/findings.csv');
  });
  it('respeita um host configurado', () => {
    expect(reportUrl({ base: 'https://api.exemplo.com', auditId: 7, format: 'html' })).toBe('https://api.exemplo.com/api/v1/audits/7/report');
  });
  it('inclui o tipo e o período só quando fazem diferença', () => {
    expect(reportUrl({ base: '', auditId: 1, format: 'html', type: 'tecnico' })).toBe('/api/v1/audits/1/report');
    expect(reportUrl({ base: '', auditId: 1, format: 'html', type: 'executivo', days: 30 })).toBe('/api/v1/audits/1/report?type=executivo');
    expect(reportUrl({ base: '', auditId: 1, format: 'html', type: 'comparativo', days: 30 })).toBe('/api/v1/audits/1/report?type=comparativo&days=30');
    expect(reportUrl({ base: '', auditId: 1, format: 'html', type: 'comparativo', days: 0 })).toBe('/api/v1/audits/1/report?type=comparativo');
  });
  it('o PDF leva tipo e período como o HTML', () => {
    expect(reportUrl({ base: '', auditId: 3, format: 'pdf' })).toBe('/api/v1/audits/3/report.pdf');
    expect(reportUrl({ base: '', auditId: 3, format: 'pdf', type: 'comparativo', days: 7 })).toBe('/api/v1/audits/3/report.pdf?type=comparativo&days=7');
  });
  it('o SBOM só existe para um sistema, no relatório técnico', () => {
    expect(formatsFor('tecnico').map((f) => f.id)).toEqual(['pdf', 'html', 'csv', 'sbom']);
    expect(formatsFor('tecnico', false).map((f) => f.id)).toEqual(['pdf', 'html', 'csv']);
    expect(formatsFor('executivo').map((f) => f.id)).toEqual(['pdf', 'html', 'csv']);
  });
  it('monta o endereço do consolidado', () => {
    expect(portfolioUrl({ base: '', type: 'executivo', format: 'pdf', days: 30 })).toBe('/api/v1/reports/portfolio?type=executivo&format=pdf&days=30');
    expect(portfolioUrl({ base: '', type: 'tecnico', format: 'csv', days: 0, systemId: 4 })).toBe('/api/v1/reports/portfolio?type=tecnico&format=csv&systemId=4');
  });
  it('escolhe o endereço certo para cada combinação', () => {
    const base = { type: 'executivo' as const, format: 'pdf' as const, days: 30 as const };
    expect(chosenReportUrl('', { ...base, systemId: null, auditId: null })).toContain('/reports/portfolio');
    expect(chosenReportUrl('', { ...base, systemId: 2, auditId: 9 })).toBe('/api/v1/audits/9/report.pdf?type=executivo');
    expect(chosenReportUrl('', { ...base, format: 'csv', systemId: 2, auditId: 9 })).toContain('systemId=2');
    expect(chosenReportUrl('', { ...base, type: 'tecnico', format: 'csv', systemId: 2, auditId: 9 })).toBe('/api/v1/audits/9/findings.csv');
    expect(chosenReportUrl('', { ...base, systemId: 2, auditId: null })).toBeNull();
    expect(chosenReportUrl('', { ...base, format: 'sbom', systemId: null, auditId: null })).toBeNull();
  });
  it('dá nome ao relatório como na lista do protótipo', () => {
    expect(reportTitle({ type: 'executivo', days: 30, auditId: null })).toBe('Executivo · 30 dias');
    expect(reportTitle({ type: 'tecnico', days: 30, auditId: 214 })).toBe('Técnico · auditoria #214');
    expect(reportTitle({ type: 'comparativo', days: 90, auditId: null })).toBe('Comparativo · trimestre');
  });
});
