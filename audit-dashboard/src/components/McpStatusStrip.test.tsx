import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import type { McpCall } from '../types';
import { McpStatusStrip } from './McpStatusStrip';

const call = (tool: string, ok = true): McpCall => ({ tool, ok, durationMs: 9, detail: ok ? null : 'chave inválida', at: new Date().toISOString().slice(0, 19) });

function strip(props: Partial<Parameters<typeof McpStatusStrip>[0]>) {
  return render(
    <MemoryRouter>
      <McpStatusStrip systemId={7} enabled calls={[]} pending={0} {...props} />
    </MemoryRouter>,
  );
}

describe('McpStatusStrip', () => {
  it('não mostra nada enquanto não sabe se a consulta está ligada', () => {
    const { container } = strip({ enabled: null });
    expect(container).toBeEmptyDOMElement();
  });

  it('desligado oferece configurar na aba Claude (MCP) do sistema', () => {
    strip({ enabled: false });
    expect(screen.getByText(/desligado para este sistema/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Configurar' })).toHaveAttribute('href', '/systems?edit=7&tab=mcp');
  });

  it('ligado e sem chamadas diz que ainda não houve consulta', () => {
    strip({});
    expect(screen.getByText(/ligado, sem consultas nesta auditoria/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver chamadas' })).toHaveAttribute('href', '/systems?edit=7&tab=mcp');
  });

  it('mostra quantas consultas houve, as recusadas e a última', () => {
    strip({ calls: [call('buscar_achados'), call('status_auditoria', false), call('status_auditoria')] });
    expect(screen.getByText(/3 consultas nesta auditoria/)).toBeInTheDocument();
    expect(screen.getByText(/\(1 recusada\)/)).toBeInTheDocument();
    expect(screen.getByText('buscar_achados')).toBeInTheDocument();
  });

  it('avisa dos pedidos que esperam aprovação, com link para a Reauditoria do sistema', () => {
    strip({ pending: 2 });
    expect(screen.getByRole('link', { name: '2 pedidos do Claude aguardam aprovação' })).toHaveAttribute('href', '/findings?aba=reauditoria&systemId=7');
  });

  it('deixa claro que o Claude não muda a nota', () => {
    strip({});
    expect(screen.getByText(/não muda a nota desta auditoria/)).toBeInTheDocument();
  });
});
