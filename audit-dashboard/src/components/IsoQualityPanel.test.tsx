import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { IAuditRunResponse, IIsoQuality } from '../types';
import { IsoQualityPanel } from './IsoQualityPanel';

const iso: IIsoQuality = {
  perfil: 'padrao', notaPonderada: 82.5, prioridade: ['security', 'maintainability'], metasAtingidas: null,
  caracteristicas: [
    { id: 'maintainability', nome: 'Manutenibilidade', avaliada: true, nota: 90, achados: 3, peso: 0.25, meta: null, atingiu: null },
    { id: 'security', nome: 'Segurança', avaliada: true, nota: 75, achados: 5, peso: 0.25, meta: null, atingiu: null },
    { id: 'safety', nome: 'Segurança operacional (safety)', avaliada: false, nota: null, achados: 0, peso: 0, meta: null, atingiu: null },
  ],
};
const trend = [
  { auditId: 1, branch: 'main', completedAt: null, notaPonderada: 80, notas: { security: 70, maintainability: 90 } },
  { auditId: 2, branch: 'main', completedAt: null, notaPonderada: 82.5, notas: { security: 75, maintainability: 90 } },
];

function wrap(run: Partial<IAuditRunResponse> = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const audit = { id: 7, systemId: 3, status: 'COMPLETED', ...run } as IAuditRunResponse;
  return render(<QueryClientProvider client={client}><IsoQualityPanel run={audit} /></QueryClientProvider>);
}

afterEach(() => vi.unstubAllGlobals());

describe('IsoQualityPanel', () => {
  it('mostra a nota ponderada, a variação e as não avaliadas à vista', async () => {
    vi.stubGlobal('fetch', vi.fn(async (url: string) =>
      new Response(JSON.stringify(String(url).includes('/trend') ? trend : iso), { status: 200 })));
    wrap();

    expect(await screen.findByText('82,5')).toBeInTheDocument();
    expect(screen.getByText('Segurança')).toBeInTheDocument();
    expect(await screen.findByText('▲ +5')).toBeInTheDocument();
    expect(screen.getByText(/não avaliada: o Sentinel não tem regras/)).toBeInTheDocument();
    expect(screen.getByText(/não certificação/)).toBeInTheDocument();
  });

  it('não consulta nada enquanto a auditoria não terminou', () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const { container } = wrap({ status: 'RUNNING' });

    expect(container).toBeEmptyDOMElement();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
