import { Button } from '@carbon/react';
import { Component, createRef, type ReactNode } from 'react';

interface IProps {
  children: ReactNode;
  /** Quando muda (por exemplo, a rota), o aviso some e a tela é desenhada de novo. */
  resetKey?: string;
}

interface IState {
  error: Error | null;
}

/** Um pacote da tela que não baixou, em geral porque saiu uma versão nova do painel enquanto a aba estava aberta. */
function isChunkError(error: Error): boolean {
  return /dynamically imported module|Importing a module script failed|ChunkLoadError/i.test(`${error.name} ${error.message}`);
}

/**
 * Impede que um erro ao desenhar uma tela derrube o painel inteiro: o menu continua e a pessoa vê o que houve e o que fazer.
 * Não pega erros de eventos nem de chamadas à API (esses já aparecem nas próprias telas).
 */
export class ErrorBoundary extends Component<IProps, IState> {
  state: IState = { error: null };

  private readonly heading = createRef<HTMLHeadingElement>();

  static getDerivedStateFromError(error: Error): IState {
    return { error };
  }

  componentDidUpdate(prevProps: IProps, prevState: IState): void {
    if (this.state.error && prevProps.resetKey !== this.props.resetKey) {
      this.setState({ error: null });
      return;
    }
    // Quem usa leitor de tela ouve o aviso; quem usa só o teclado continua dali, não do início da página.
    if (this.state.error && !prevState.error) this.heading.current?.focus();
  }

  componentDidMount(): void {
    if (this.state.error) this.heading.current?.focus();
  }

  private readonly tryAgain = () => this.setState({ error: null });

  render(): ReactNode {
    const { error } = this.state;
    if (!error) return this.props.children;
    const chunk = isChunkError(error);
    return (
      <div role="alert" style={{ display: 'grid', gap: 12, padding: '24px 0', maxWidth: 640 }}>
        <h2 ref={this.heading} tabIndex={-1} style={{ margin: 0, fontSize: 20, fontWeight: 400 }}>Esta tela não abriu</h2>
        <p style={{ margin: 0, color: 'var(--s-text-2)' }}>
          {chunk
            ? 'Saiu uma nova versão do painel enquanto esta aba estava aberta. Recarregue a página para continuar.'
            : 'Algo inesperado aconteceu ao desenhar esta tela. Tente de novo; se continuar, volte ao início e avise quem cuida do Sentinel.'}
        </p>
        <div style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
          {chunk ? (
            <Button kind="primary" size="md" onClick={() => window.location.reload()}>Recarregar a página</Button>
          ) : (
            <Button kind="primary" size="md" onClick={this.tryAgain}>Tentar de novo</Button>
          )}
          <a href="/">Voltar ao início</a>
        </div>
        <details>
          <summary>Detalhe técnico</summary>
          <pre style={{ margin: '8px 0 0', whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', fontSize: 13 }}>{error.message}</pre>
        </details>
      </div>
    );
  }
}
