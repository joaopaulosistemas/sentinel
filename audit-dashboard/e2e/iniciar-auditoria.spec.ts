import { expect, test } from '@playwright/test';
import { mockApi } from './helpers';

test('da visão geral à execução: escolhe a branch, inicia e cai no acompanhamento', async ({ page }) => {
  const run = { id: 77, systemId: 1, systemName: 'conta-corrente-api', branch: 'develop', status: 'PENDING', riskScore: null, findingsSummary: {}, startedAt: new Date().toISOString(), completedAt: null };
  const calls = await mockApi(page, {
    '/api/v1/audits/77': () => run,
    '/api/v1/audits': (method) => (method === 'POST' ? run : []),
  });
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Visão geral' })).toBeVisible();
  await expect(page.getByRole('list', { name: 'Etapas da auditoria' })).toBeVisible();

  await page.getByRole('button', { name: 'Nova auditoria' }).click();
  await page.getByLabel('Branch').fill('develop');
  await page.getByRole('button', { name: 'Iniciar auditoria' }).click();

  await expect(page.getByText('Auditoria #77 iniciada')).toBeVisible();
  const post = calls.find((c) => c.method === 'POST' && c.url === '/api/v1/audits');
  expect(post?.body).toMatchObject({ systemId: 1, branch: 'develop' });

  await page.getByRole('link', { name: 'Acompanhar execução' }).click();
  await expect(page).toHaveURL(/\/audits\/77$/);
});
