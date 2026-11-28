import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { IDecision } from '../../utils/claudeDecisions';
import { ClaudeDecisions } from './ClaudeDecisions';

const espera: IDecision = {
  key: 'pedido-1-7', kind: 'pedido', systemId: 1, systemName: 'sentinel', title: 'Auditar a main', detail: '',
  primary: { label: 'Aprovar', action: { type: 'pending', systemId: 1, actionId: 7, decision: 'approve' } },
  secondary: { label: 'Recusar', action: { type: 'pending', systemId: 1, actionId: 7, decision: 'reject' } },
  result: null,
};

const decidida: IDecision = {
  key: 'pedido-1-8', kind: 'pedido', systemId: 1, systemName: 'sentinel', title: 'Suprimir achado', detail: '', primary: null, secondary: null,
  result: { label: 'Aprovado', tone: 'good', at: '2026-11-28T03:00:00', undoBlocked: null,
    undo: { label: 'Desfazer', action: { type: 'pending', systemId: 1, actionId: 8, decision: 'undo' } } },
};

function renderiza(items: IDecision[]) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ClaudeDecisions items={items} loading={false} systems={[]} />
    </QueryClientProvider>,
  );
}

describe('ClaudeDecisions: aparência dos botões e dos filtros', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('os botões usam as classes do sistema, sem cor escrita no elemento', () => {
    renderiza([espera]);
    const aprovar = screen.getByRole('button', { name: 'Aprovar' });
    const recusar = screen.getByRole('button', { name: 'Recusar' });
    expect(aprovar).toHaveClass('sentinel-btn', 'sentinel-btn--primary');
    expect(recusar).toHaveClass('sentinel-btn');
    expect(recusar).not.toHaveClass('sentinel-btn--primary');
    expect(aprovar.getAttribute('style')).toBeNull();
    expect(recusar.getAttribute('style')).toBeNull();
  });

  it('o filtro escolhido diz que está pressionado e deixa o desenho do estado para a folha de estilo', () => {
    renderiza([espera, decidida]);
    const esperando = screen.getByRole('button', { name: /^Esperando/ });
    const decididas = screen.getByRole('button', { name: /^Decididas/ });
    expect(esperando).toHaveAttribute('aria-pressed', 'true');
    expect(decididas).toHaveAttribute('aria-pressed', 'false');
    expect(esperando).toHaveClass('sentinel-btn');
    expect(esperando.getAttribute('style')).toBeNull();
    fireEvent.click(decididas);
    expect(decididas).toHaveAttribute('aria-pressed', 'true');
    expect(esperando).toHaveAttribute('aria-pressed', 'false');
  });

  it('enquanto a decisão é enviada, os botões ficam desabilitados e a área avisa que está ocupada', async () => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise(() => undefined)));
    renderiza([espera]);
    const area = screen.getByRole('region', { name: 'Decisões do Claude' });
    expect(area).toHaveAttribute('aria-busy', 'false');
    fireEvent.click(screen.getByRole('button', { name: 'Aprovar' }));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Aprovar' })).toBeDisabled());
    expect(screen.getByRole('button', { name: 'Recusar' })).toBeDisabled();
    expect(area).toHaveAttribute('aria-busy', 'true');
  });
});
