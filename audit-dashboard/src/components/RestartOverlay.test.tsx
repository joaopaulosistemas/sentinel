import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { axe } from 'vitest-axe';
import * as matchers from 'vitest-axe/matchers';
import { RESTART_TIMEOUT_SECONDS, RestartOverlay } from './RestartOverlay';

expect.extend(matchers);

declare module 'vitest' {
  interface Assertion {
    toHaveNoViolations(): void;
  }
}

beforeEach(() => vi.useFakeTimers());
afterEach(() => vi.useRealTimers());

function renderOverlay(phase: 'requesting' | 'waiting' = 'waiting') {
  const onKeepWaiting = vi.fn();
  const onClose = vi.fn();
  const view = render(<RestartOverlay phase={phase} onKeepWaiting={onKeepWaiting} onClose={onClose} />);
  return { ...view, onKeepWaiting, onClose };
}

describe('RestartOverlay', () => {
  it('trava a tela com um aviso modal que explica o que está acontecendo', () => {
    renderOverlay();
    const dialog = screen.getByRole('alertdialog', { name: 'Reiniciando a API' });
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(dialog).toHaveTextContent('só são lidas quando a API sobe');
    expect(dialog).toHaveTextContent('A tela está travada de propósito');
    expect(dialog).toHaveTextContent('Auditorias em andamento são interrompidas');
  });

  it('mostra as etapas conforme a fase', () => {
    const { rerender } = renderOverlay('requesting');
    const etapas = () => screen.getAllByRole('listitem').map((li) => li.getAttribute('data-state'));
    expect(screen.getByText('Enviando o pedido de reinício')).toBeInTheDocument();
    expect(etapas()).toEqual(['active', 'pending', 'pending']);

    rerender(<RestartOverlay phase="waiting" onKeepWaiting={() => {}} onClose={() => {}} />);
    expect(screen.getByText('Pedido de reinício enviado')).toBeInTheDocument();
    expect(etapas()).toEqual(['done', 'active', 'pending']);
  });

  it('conta o tempo de espera', () => {
    renderOverlay();
    expect(screen.getByText('Aguardando há 0 s')).toBeInTheDocument();
    act(() => { vi.advanceTimersByTime(5000); });
    expect(screen.getByText('Aguardando há 5 s')).toBeInTheDocument();
  });

  it('trava a rolagem da página e a devolve ao sair', () => {
    document.body.style.overflow = 'auto';
    const { unmount } = renderOverlay();
    expect(document.body.style.overflow).toBe('hidden');
    unmount();
    expect(document.body.style.overflow).toBe('auto');
    document.body.style.overflow = '';
  });

  it('o foco fica dentro do aviso e o Escape não o fecha enquanto espera', () => {
    const { onClose } = renderOverlay();
    const dialog = screen.getByRole('alertdialog');
    expect(dialog).toHaveFocus();
    expect(fireEvent.keyDown(dialog, { key: 'Tab' })).toBe(false);
    fireEvent.keyDown(dialog, { key: 'Escape' });
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.queryByRole('button')).toBeNull();
  });

  it('passado o tempo limite, explica as causas e oferece uma saída', () => {
    const { onClose, onKeepWaiting } = renderOverlay();
    act(() => { vi.advanceTimersByTime(RESTART_TIMEOUT_SECONDS * 1000); });
    expect(screen.getByRole('alertdialog', { name: 'A API está demorando para voltar' })).toBeInTheDocument();
    expect(screen.getByText(/docker compose logs audit-api/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Continuar esperando' }));
    expect(onKeepWaiting).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole('button', { name: 'Fechar este aviso' }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('depois do tempo limite o Escape libera a tela', () => {
    const { onClose } = renderOverlay();
    act(() => { vi.advanceTimersByTime(RESTART_TIMEOUT_SECONDS * 1000); });
    fireEvent.keyDown(screen.getByRole('alertdialog'), { key: 'Escape' });
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('sem violações de acessibilidade', async () => {
    vi.useRealTimers();
    const { container, unmount } = renderOverlay();
    expect(await axe(container)).toHaveNoViolations();
    unmount();
  });
});
