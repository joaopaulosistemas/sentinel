import { describe, expect, it } from 'vitest';
import { reportUrl } from './reportLinks';

describe('endereço do relatório', () => {
  it('usa caminho relativo quando não há host configurado', () => {
    expect(reportUrl('', 215, 'html')).toBe('/api/v1/audits/215/report');
    expect(reportUrl('', 215, 'sbom')).toBe('/api/v1/audits/215/sbom');
  });
  it('respeita um host configurado', () => {
    expect(reportUrl('https://api.exemplo.com', 7, 'html')).toBe('https://api.exemplo.com/api/v1/audits/7/report');
  });
});
