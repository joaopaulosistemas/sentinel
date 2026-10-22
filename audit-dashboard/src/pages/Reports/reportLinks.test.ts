import { describe, expect, it } from 'vitest';
import { formatsFor, reportUrl } from './reportLinks';

describe('endereço do relatório', () => {
  it('usa caminho relativo quando não há host configurado', () => {
    expect(reportUrl('', 215, 'html')).toBe('/api/v1/audits/215/report');
    expect(reportUrl('', 215, 'sbom')).toBe('/api/v1/audits/215/sbom');
    expect(reportUrl('', 215, 'csv')).toBe('/api/v1/audits/215/findings.csv');
  });
  it('respeita um host configurado', () => {
    expect(reportUrl('https://api.exemplo.com', 7, 'html')).toBe('https://api.exemplo.com/api/v1/audits/7/report');
  });
  it('inclui o tipo e o período só quando fazem diferença', () => {
    expect(reportUrl('', 1, 'html', { type: 'tecnico' })).toBe('/api/v1/audits/1/report');
    expect(reportUrl('', 1, 'html', { type: 'executivo', days: 30 })).toBe('/api/v1/audits/1/report?type=executivo');
    expect(reportUrl('', 1, 'html', { type: 'comparativo', days: 30 })).toBe('/api/v1/audits/1/report?type=comparativo&days=30');
    expect(reportUrl('', 1, 'html', { type: 'comparativo', days: 0 })).toBe('/api/v1/audits/1/report?type=comparativo');
  });
  it('o PDF leva tipo e período como o HTML', () => {
    expect(reportUrl('', 3, 'pdf')).toBe('/api/v1/audits/3/report.pdf');
    expect(reportUrl('', 3, 'pdf', { type: 'comparativo', days: 7 })).toBe('/api/v1/audits/3/report.pdf?type=comparativo&days=7');
  });
  it('só o relatório técnico tem CSV e SBOM', () => {
    expect(formatsFor('tecnico').map((f) => f.id)).toEqual(['html', 'pdf', 'csv', 'sbom']);
    expect(formatsFor('executivo').map((f) => f.id)).toEqual(['html', 'pdf']);
    expect(formatsFor('comparativo').map((f) => f.id)).toEqual(['html', 'pdf']);
  });
});
