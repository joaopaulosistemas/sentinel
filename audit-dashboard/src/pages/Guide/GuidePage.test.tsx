import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { axe } from 'vitest-axe';
import * as matchers from 'vitest-axe/matchers';
import type { GuideResponse } from '../../types';
import { GuidePage } from './GuidePage';

expect.extend(matchers);

declare module 'vitest' {
  interface Assertion {
    toHaveNoViolations(): void;
  }
}

const GUIDE: GuideResponse = {
  process: [{ title: 'Primeira linha que diz o que mudou', doIt: 'tipo(escopo): descrição.', avoid: 'wip.' }],
  stacks: [
    {
      id: 'quarkus', name: 'Quarkus', kind: 'backend', summary: 'Java para nuvem', tools: ['Checkstyle'], gaps: ['Sem regra de Mutiny'],
      commands: ['mvn -B -ntp verify'], commandsVerified: true, review: { docs: false, team: false, ai: false },
      practices: [{ title: 'Acesso declarado', doIt: 'Use @RolesAllowed.', avoid: 'Endpoint sem anotação.' }],
      baseLanguage: false, ownRules: 1, inheritedRules: 1, rules: [{ id: 'sentinel-quarkus-getenv-direto', own: true }, { id: 'sentinel-crypto-hash-fraco', own: false }],
    },
    {
      id: 'vue', name: 'Vue', kind: 'frontend', summary: 'Vue 3', tools: [], gaps: [], commands: ['npm test'], commandsVerified: false,
      review: { docs: false, team: false, ai: false }, practices: [{ title: 'v-for com key', doIt: 'Use :key.', avoid: 'v-for sem key.' }],
      baseLanguage: false, ownRules: 0, inheritedRules: 0, rules: [],
    },
  ],
  rules: [
    { id: 'sentinel-quarkus-getenv-direto', pack: 'core-quarkus', title: 'Leitura direta de variável de ambiente', action: 'Use @ConfigProperty', severity: 'baixa', cwe: '', before: 'System.getenv("X")', after: '@ConfigProperty(name = "x")' },
    { id: 'sentinel-crypto-hash-fraco', pack: 'core-security', title: 'Algoritmo de hash fraco', action: 'Use SHA-256', severity: 'alta', cwe: 'CWE-328', before: '', after: '' },
  ],
};

function renderPage(): HTMLElement {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) => {
      if (String(url).endsWith('/claude-md')) return new Response('# Regras de código — Quarkus\n\n- `mvn -B -ntp verify`\n', { status: 200 });
      return new Response(JSON.stringify(GUIDE), { status: 200 });
    }),
  );
  const { container } = render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <GuidePage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return container;
}

afterEach(() => vi.unstubAllGlobals());

describe('GuidePage', () => {
  it('abre em Quarkus, com as práticas, as lacunas e as regras, a mais grave primeiro', async () => {
    renderPage();
    expect(await screen.findByRole('heading', { name: 'Boas práticas de Quarkus' })).toBeInTheDocument();
    expect(screen.getByText('Acesso declarado')).toBeInTheDocument();
    expect(screen.getByText('Sem regra de Mutiny')).toBeInTheDocument();
    const lista = screen.getByRole('heading', { name: /Regras \(2 de 2\)/ }).closest('section') as HTMLElement;
    const titulos = within(lista).getAllByRole('button', { expanded: false }).map((b) => b.textContent);
    expect(titulos[0]).toContain('Algoritmo de hash fraco');
  });

  it('mostra commits e revisão, iguais em qualquer tecnologia', async () => {
    renderPage();
    expect(await screen.findByRole('heading', { name: 'Commits e revisão' })).toBeInTheDocument();
    expect(screen.getByText('Primeira linha que diz o que mudou')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /^Vue/ }));
    expect(await screen.findByRole('heading', { name: 'Boas práticas de Vue' })).toBeInTheDocument();
    expect(screen.getByText('Primeira linha que diz o que mudou')).toBeInTheDocument();
  });

  it('filtra por severidade e busca, e abre o antes e depois da regra', async () => {
    renderPage();
    await screen.findByText('Leitura direta de variável de ambiente');
    fireEvent.click(screen.getByRole('button', { name: /^Baixa · 1/ }));
    expect(screen.queryByText('Algoritmo de hash fraco')).not.toBeInTheDocument();
    fireEvent.click(screen.getByText('Leitura direta de variável de ambiente'));
    expect(screen.getByText('System.getenv("X")')).toBeInTheDocument();
    expect(screen.getByText('@ConfigProperty(name = "x")')).toBeInTheDocument();
  });

  it('troca de tecnologia e mostra a nota e o que falta', async () => {
    renderPage();
    await screen.findByText('Acesso declarado');
    fireEvent.click(screen.getByRole('button', { name: /^Vue/ }));
    expect(await screen.findByRole('heading', { name: 'Boas práticas de Vue' })).toBeInTheDocument();
    expect(screen.getByText('Só regras gerais')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'O que falta para a nota 10 em Vue' })).toBeInTheDocument();
    expect(screen.getByText('Revisado por pessoas da equipe')).toBeInTheDocument();
  });

  it('mostra o CLAUDE.md gerado pela API na aba da IA', async () => {
    renderPage();
    await screen.findByText('Acesso declarado');
    fireEvent.click(screen.getByRole('button', { name: 'Para a IA (CLAUDE.md)' }));
    await waitFor(() => expect(screen.getByText(/Regras de código — Quarkus/)).toBeInTheDocument());
    expect(screen.getByRole('button', { name: 'Copiar' })).toBeEnabled();
    expect(screen.getByText(/linhas \(meta: abaixo de 200\)/)).toBeInTheDocument();
  });

  it('não tem violações de acessibilidade', async () => {
    const container = renderPage();
    await screen.findByText('Acesso declarado');
    expect(await axe(container)).toHaveNoViolations();
  });
});
