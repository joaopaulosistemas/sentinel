import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ReportsPage } from './ReportsPage';

function renderPage() {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) => {
      const u = String(url);
      if (u === '/api/v1/systems/2/audits') {
        return new Response(
          JSON.stringify([{ id: 214, systemId: 2, systemName: 'portal', branch: 'main', status: 'COMPLETED', riskScore: { overall: 62 }, findingsSummary: {}, startedAt: '2026-10-16T09:00:00', completedAt: '2026-10-16T09:10:00' }]),
          { status: 200 },
        );
      }
      if (u === '/api/v1/systems/3/audits') return new Response('[]', { status: 200 });
      return new Response(
        JSON.stringify([
          { id: 2, name: 'portal', stack: 'JAVA', team: 'T', lastAuditScore: 62, lastAuditDate: null, openCritical: 0, openHigh: 0 },
          { id: 3, name: 'novo', stack: 'JAVA', team: 'T', lastAuditScore: null, lastAuditDate: null, openCritical: 0, openHigh: 0 },
        ]),
        { status: 200 },
      );
    }),
  );
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <ReportsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

let open: ReturnType<typeof vi.fn>;
beforeEach(() => {
  localStorage.clear();
  open = vi.fn();
  vi.stubGlobal('open', open);
});
afterEach(() => vi.unstubAllGlobals());

describe('ReportsPage', () => {
  it('gera o consolidado de todos os sistemas e registra na lista com destaque "agora"', async () => {
    renderPage();
    expect(screen.getByText('Nenhum relatório gerado ainda neste navegador.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Gerar relatório/ }));
    expect(open).toHaveBeenCalledWith('/api/v1/reports/portfolio?type=executivo&format=pdf&days=30', '_blank', 'noreferrer');
    const list = screen.getByLabelText('Relatórios gerados');
    expect(within(list).getByText('Executivo · 30 dias')).toBeInTheDocument();
    expect(within(list).getByText('Todos os sistemas')).toBeInTheDocument();
    expect(within(list).getByText('agora')).toBeInTheDocument();
    expect(within(list).getByText('Executivo · 30 dias').parentElement).toHaveClass('sentinel-linha-hover');
    expect(within(list).getByRole('link', { name: 'Abrir' })).toHaveAttribute('href', expect.stringContaining('/reports/portfolio'));
  });

  it('para um sistema usa a auditoria mais recente e libera o SBOM no técnico', async () => {
    renderPage();
    await screen.findByRole('option', { name: 'portal' });
    fireEvent.change(screen.getByLabelText('Escolha o sistema ou todos'), { target: { value: '2' } });
    fireEvent.click(screen.getByRole('button', { name: /Técnico/ }));
    expect(await screen.findByText(/Usa a auditoria mais recente: #214/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'SBOM (CycloneDX)' }));
    fireEvent.click(screen.getByRole('button', { name: /Gerar relatório/ }));
    expect(open).toHaveBeenCalledWith('/api/v1/audits/214/sbom', '_blank', 'noreferrer');
    expect(screen.getByText('Técnico · auditoria #214')).toBeInTheDocument();
  });

  it('sem sistema escolhido não oferece SBOM e, sem auditoria concluída, bloqueia o botão', async () => {
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: /Técnico/ }));
    expect(screen.queryByRole('button', { name: 'SBOM (CycloneDX)' })).not.toBeInTheDocument();
    await screen.findByRole('option', { name: 'novo' });
    fireEvent.change(screen.getByLabelText('Escolha o sistema ou todos'), { target: { value: '3' } });
    expect(await screen.findByText(/ainda não tem auditoria concluída/)).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: /Gerar relatório/ })).toBeDisabled());
  });

  it('lê entradas antigas do histórico e limpa a lista', () => {
    localStorage.setItem(
      'sentinel_report_history',
      JSON.stringify([{ id: 'a', systemName: 'portal', auditId: 7, format: 'html', type: 'executivo', days: 30, generatedAt: '2026-10-01T10:00:00', url: '/x' }]),
    );
    renderPage();
    expect(screen.getByText('Executivo · 30 dias')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Limpar lista' }));
    expect(screen.getByText('Nenhum relatório gerado ainda neste navegador.')).toBeInTheDocument();
  });
});
