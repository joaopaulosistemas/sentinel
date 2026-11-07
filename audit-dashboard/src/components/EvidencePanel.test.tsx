import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { EvidencePanel } from './EvidencePanel';

function setup(enabled = true) {
  const calls: Array<{ url: string; method: string }> = [];
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
    calls.push({ url: String(url), method: init?.method ?? 'GET' });
    if (init?.method === 'PUT') return new Response(JSON.stringify({ name: 'schema.sql', sizeBytes: 22 }), { status: 200 });
    return new Response(JSON.stringify({ enabled, files: [] }), { status: 200 });
  }));
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const view = render(
    <QueryClientProvider client={client}>
      <EvidencePanel systemId={3} systemName="conta-corrente-api" onAudit={() => {}} />
    </QueryClientProvider>,
  );
  return { calls, view };
}

afterEach(() => vi.unstubAllGlobals());

describe('EvidencePanel', () => {
  it('entrega os arquivos aceitos por PUT na API do sistema', async () => {
    const { calls, view } = setup();
    const input = view.container.querySelector('input[type="file"]') as HTMLInputElement;
    const content = 'CREATE TABLE contas (id int primary key);';
    const file = new File([content], 'schema.sql', { type: 'text/plain' });
    // O jsdom não implementa Blob.text(); o painel lê o começo do arquivo para reconhecer o formato.
    Object.defineProperty(file, 'slice', { value: () => ({ text: async () => content }) });
    fireEvent.change(input, { target: { files: [file] } });
    const btn = await screen.findByRole('button', { name: 'Entregar ao Sentinel' });
    await waitFor(() => expect(btn).toBeEnabled());
    fireEvent.click(btn);
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT' && c.url.endsWith('/api/v1/systems/3/evidence/schema.sql'))).toBe(true));
    expect(await screen.findByText(/Vale a partir da próxima auditoria|Entregue/)).toBeInTheDocument();
  });

  it('servidor sem pasta de evidências desabilita a entrega e explica', async () => {
    setup(false);
    expect(await screen.findByText(/SENTINEL_EVIDENCE_DIR/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Entregar ao Sentinel' })).toBeDisabled();
  });
});
