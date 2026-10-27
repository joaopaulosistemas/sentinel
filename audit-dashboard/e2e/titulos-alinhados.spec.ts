import { expect, test } from '@playwright/test';
import { mockApi, system } from './helpers';

const run = (status: string) => ({
  id: 77, systemId: 1, systemName: 'conta-corrente-api', branch: 'main', status, riskScore: status === 'COMPLETED' ? { overall: 61 } : null,
  findingsSummary: {}, startedAt: new Date().toISOString(), completedAt: status === 'COMPLETED' ? new Date().toISOString() : null,
});

const rotas: Array<[string, string, string]> = [
  ['Visão geral', '/', 'COMPLETED'],
  ['Sistemas', '/systems', 'COMPLETED'],
  ['Ranking', '/ranking', 'COMPLETED'],
  ['Relatórios', '/reports', 'COMPLETED'],
  ['Informações de Auditoria', '/findings', 'COMPLETED'],
  ['Achados de uma auditoria', '/findings?auditId=77', 'COMPLETED'],
  ['Detalhe do sistema', '/systems/1', 'COMPLETED'],
  ['Auditoria em andamento', '/audits/77', 'RUNNING'],
  ['Auditoria concluída', '/audits/77', 'COMPLETED'],
];

test('o título de todas as telas fica na mesma altura da Visão geral', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  let status = 'COMPLETED';
  await mockApi(page, {
    '/api/v1/findings?': () => ({ total: 0, bySystem: [], items: [] }),
    '/fix-plan': () => ({ scoreNow: 61, scoreIfCriticalFixed: 61, criticalCount: 0, items: [] }),
    '/audits/77/': () => [],
    '/trend': () => [],
    '/suppressions': () => [],
    '/api/v1/systems/1/audits': () => [],
    '/api/v1/systems/1': () => system,
    '/api/v1/audits/77': () => run(status),
  });
  const tops: Record<string, number> = {};
  for (const [nome, rota, st] of rotas) {
    status = st;
    await page.goto(rota);
    await page.waitForLoadState('networkidle');
    await expect(page.locator('h1').first(), nome).toBeVisible();
    // toda tela tem o caminho (breadcrumb), começando em Início, 22 px abaixo do cabeçalho
    const caminho = page.getByRole('navigation', { name: 'Caminho' });
    await expect(caminho, nome).toBeVisible();
    await expect(caminho.getByRole('link', { name: 'Início' }), nome).toBeVisible();
    expect(Math.round((await caminho.boundingBox())!.y), `${nome}: caminho`).toBe(70);
    // repete a medida até estabilizar (a tela pode redesenhar ao carregar os dados)
    await expect.poll(async () => {
      tops[nome] = Math.round(await page.locator('h1').first().evaluate((el) => el.getBoundingClientRect().top + window.scrollY));
      return tops[nome];
    }, { message: nome }).toBeGreaterThan(0);
  }
  const base = tops['Visão geral'];
  for (const [nome, y] of Object.entries(tops)) expect(y, `${nome}: ${JSON.stringify(tops)}`).toBe(base);
});
