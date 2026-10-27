import { expect, test } from '@playwright/test';
import { mockApi } from './helpers';

const engine = {
  profile: 'java-bancario', profileDescription: 'Regras para serviços Java', adjustments: 0,
  tuning: { rules: [], thresholds: {}, enablePacks: [], disablePacks: [], disableAnalyzers: [] },
  profileRules: [],
  packs: [{ id: 'java', name: 'Java', description: null, inProfile: true, active: true }],
  analyzers: [], thresholds: [], knownRules: [],
  catalog: [
    { id: 'java.sql.sql-injection', pack: 'java', message: 'SQL montado por concatenação', severity: 'ERROR', category: 'security' },
    { id: 'java.log.sensitive-log', pack: 'java', message: 'Dado sensível no log', severity: 'WARNING', category: 'security' },
  ],
};

test('ajusta uma regra pelo catálogo, avisa sobre alteração não salva e salva', async ({ page }) => {
  const calls = await mockApi(page, {
    '/engine-config': (method, body) => (method === 'PUT' ? body : engine),
  });
  await page.goto('/systems?edit=1');
  await page.getByRole('tab', { name: /Motor de regras/ }).click();

  await page.getByLabel('Buscar regra').fill('sql');
  await expect(page.getByText('SQL montado por concatenação')).toBeVisible();
  await expect(page.getByText('Dado sensível no log')).toBeHidden();

  await page.getByLabel('Desligar sql-injection').check();
  await expect(page.getByText('Alterações não salvas')).toBeVisible();

  // Fechar sem salvar pede confirmação.
  await page.getByRole('button', { name: 'Fechar' }).click();
  await expect(page.getByText(/Há alterações não salvas em/)).toBeVisible();
  await page.getByRole('button', { name: 'Continuar editando' }).click();

  await page.getByRole('button', { name: 'Salvar ajustes' }).click();
  await expect.poll(() => calls.some((c) => c.method === 'PUT' && c.url.includes('/engine-config'))).toBe(true);
});
