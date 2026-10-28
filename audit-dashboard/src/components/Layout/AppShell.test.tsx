import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, describe, expect, it, vi } from 'vitest';
import React from 'react';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AppShell } from './AppShell';
import pkg from '../../../package.json';

function Wrap({ children }: { children: React.ReactNode }) {
  return <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{children}</QueryClientProvider>;
}

const matchMediaOriginal = window.matchMedia;
afterEach(() => { vi.unstubAllGlobals(); window.localStorage.clear(); window.matchMedia = matchMediaOriginal; });

/** Simula tela larga, em que o menu fica fixo e o divisor aparece. */
function telaLarga() {
  window.matchMedia = ((query: string) => ({ matches: true, media: query, onchange: null, addListener: () => {}, removeListener: () => {},
    addEventListener: () => {}, removeEventListener: () => {}, dispatchEvent: () => false })) as unknown as typeof window.matchMedia;
}

describe('AppShell', () => {
  it('mostra quantos pedidos do Claude esperam decisão no item de Informações de Auditoria', async () => {
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      const u = String(url);
      if (u.includes('/pending-actions/')) return new Response(JSON.stringify([{ id: 1, kind: 'START_AUDIT', summary: 's', status: 'PENDING', createdAt: '', expiresAt: '', decidedAt: null, resultNote: null, auditRunId: null }, { id: 2, kind: 'START_AUDIT', summary: 's', status: 'APPROVED', createdAt: '', expiresAt: '', decidedAt: null, resultNote: null, auditRunId: null }]), { status: 200 });
      return new Response(JSON.stringify([{ id: 4, name: 'a', stack: 'JAVA', team: '', lastAuditScore: null, lastAuditDate: null, openCritical: 0, openHigh: 0 }]), { status: 200 });
    }));
    render(<Wrap><MemoryRouter><AppShell><p>x</p></AppShell></MemoryRouter></Wrap>);
    expect(await screen.findByLabelText('1 pedido do Claude espera sua decisão')).toBeInTheDocument();
  });

  it('mostra a versão do package.json no cabeçalho', () => {
    render(
      <Wrap><MemoryRouter>
        <AppShell>
          <p>conteúdo</p>
        </AppShell>
      </MemoryRouter></Wrap>,
    );
    expect(screen.getByText(`v${pkg.version}`)).toBeInTheDocument();
    expect(screen.getByText('conteúdo')).toBeInTheDocument();
  });

  it('lista os itens principais de navegação', () => {
    render(
      <Wrap><MemoryRouter>
        <AppShell>
          <p>x</p>
        </AppShell>
      </MemoryRouter></Wrap>,
    );
    const nav = screen.getByRole('navigation', { name: 'Navegação principal' });
    expect(within(nav).getAllByRole('link').map((a) => a.textContent)).toEqual([
      'Visão geral',
      'Sistemas',
      'Informações de Auditoria',
      'Ranking de Sistemas',
      'Relatórios',
      'Configurações',
    ]);
  });
  it('tem um divisor acessível que muda a largura com o teclado e guarda a escolha', () => {
    telaLarga();
    render(<Wrap><MemoryRouter><AppShell><p>x</p></AppShell></MemoryRouter></Wrap>);
    const sep = screen.getByRole('separator', { name: 'Largura do menu' });
    expect(sep).toHaveAttribute('aria-valuenow', '256');
    fireEvent.keyDown(sep, { key: 'ArrowRight' });
    expect(sep).toHaveAttribute('aria-valuenow', '272');
    expect(window.localStorage.getItem('sentinel.menu-largura')).toBe('272');
    fireEvent.keyDown(sep, { key: 'Home' });
    expect(sep).toHaveAttribute('aria-valuenow', '48');
    expect(screen.getByRole('navigation', { name: 'Navegação principal' }).className).toContain('sentinel-menu-trilho');
    fireEvent.doubleClick(sep);
    expect(sep).toHaveAttribute('aria-valuenow', '256');
  });

  it('começa com a largura guardada', () => {
    telaLarga();
    window.localStorage.setItem('sentinel.menu-largura', '320');
    render(<Wrap><MemoryRouter><AppShell><p>x</p></AppShell></MemoryRouter></Wrap>);
    expect(screen.getByRole('separator', { name: 'Largura do menu' })).toHaveAttribute('aria-valuenow', '320');
  });

  it('em tela estreita não mostra o divisor', () => {
    render(<Wrap><MemoryRouter><AppShell><p>x</p></AppShell></MemoryRouter></Wrap>);
    expect(screen.queryByRole('separator', { name: 'Largura do menu' })).toBeNull();
  });
});
