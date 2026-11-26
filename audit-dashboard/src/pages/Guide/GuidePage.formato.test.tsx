import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { GuidePage } from './GuidePage';

afterEach(() => vi.unstubAllGlobals());

describe('GuidePage com resposta fora do formato', () => {
  it('mostra o aviso de erro em vez de derrubar a tela', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify([]), { status: 200 })));
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter><GuidePage /></MemoryRouter>
      </QueryClientProvider>,
    );
    expect(await screen.findByText('Erro ao carregar o guia')).toBeInTheDocument();
  });
});
