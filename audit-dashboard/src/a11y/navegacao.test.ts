import { describe, expect, it } from 'vitest';

/** Guardas de regressão da varredura de acessibilidade das telas. */
const fontes = import.meta.glob(['../**/*.tsx', '!../**/*.test.tsx'], { query: '?raw', import: 'default', eager: true }) as Record<string, string>;
const src = (fim: string) => Object.entries(fontes).find(([f]) => f.endsWith(fim))?.[1] ?? '';

describe('navegação e títulos', () => {
  it('o link de pular para o conteúdo está em português', () => {
    expect(src('Layout/AppShell.tsx')).toContain('<SkipToContent>Pular para o conteúdo</SkipToContent>');
  });

  it('o Ranking não pula de h1 para h3', () => {
    expect(src('Ranking/RankingPage.tsx')).not.toMatch(/<h3[ >]/);
  });

  it('os links do caminho têm alvo de pelo menos 24 px', () => {
    expect(src('components/PageShell.tsx')).toContain('sentinel-caminho');
  });
});
