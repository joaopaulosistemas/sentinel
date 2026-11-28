import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorBoundary } from './ErrorBoundary';

let quebrar = true;

function Tela() {
  if (quebrar) throw new Error('resposta sem lista de achados');
  return <p>Tela aberta</p>;
}

describe('ErrorBoundary', () => {
  beforeEach(() => {
    quebrar = true;
    // O React escreve o erro capturado no console; o teste espera esse erro.
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
  });
  afterEach(() => vi.restoreAllMocks());

  it('mostra a tela normalmente quando nada quebra', () => {
    quebrar = false;
    render(<ErrorBoundary><Tela /></ErrorBoundary>);
    expect(screen.getByText('Tela aberta')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('troca a tela branca por um aviso com o que fazer e o detalhe técnico', () => {
    render(<ErrorBoundary><Tela /></ErrorBoundary>);
    const alerta = screen.getByRole('alert');
    expect(alerta).toHaveTextContent('Esta tela não abriu');
    expect(screen.getByRole('button', { name: 'Tentar de novo' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Voltar ao início' })).toHaveAttribute('href', '/');
    expect(screen.getByText('resposta sem lista de achados')).toBeInTheDocument();
  });

  it('leva o foco ao título do aviso, para o leitor de tela anunciar e o teclado seguir daí', () => {
    render(<ErrorBoundary><Tela /></ErrorBoundary>);
    expect(screen.getByRole('heading', { name: 'Esta tela não abriu' })).toHaveFocus();
  });

  it('Tentar de novo desenha a tela outra vez depois que a causa passou', () => {
    render(<ErrorBoundary><Tela /></ErrorBoundary>);
    quebrar = false;
    fireEvent.click(screen.getByRole('button', { name: 'Tentar de novo' }));
    expect(screen.getByText('Tela aberta')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('trocar de rota (resetKey) limpa o aviso sem precisar clicar', () => {
    const { rerender } = render(<ErrorBoundary resetKey="/achados"><Tela /></ErrorBoundary>);
    expect(screen.getByRole('alert')).toBeInTheDocument();
    quebrar = false;
    rerender(<ErrorBoundary resetKey="/sistemas"><Tela /></ErrorBoundary>);
    expect(screen.getByText('Tela aberta')).toBeInTheDocument();
  });

  it('pacote que não baixou (versão nova publicada) pede para recarregar a página', () => {
    function Pacote(): never {
      throw new TypeError('Failed to fetch dynamically imported module: /assets/ClaudePage-abc.js');
    }
    render(<ErrorBoundary><Pacote /></ErrorBoundary>);
    expect(screen.getByRole('alert')).toHaveTextContent('nova versão');
    expect(screen.getByRole('button', { name: 'Recarregar a página' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Tentar de novo' })).toBeNull();
  });
});
