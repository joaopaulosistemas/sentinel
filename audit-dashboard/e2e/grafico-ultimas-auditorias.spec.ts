import { expect, test } from '@playwright/test';
import { mockApi, system } from './helpers';

const SCORE = { security: 60, quality: 60, architecture: 60, dependencies: 60, observability: 60 };

// id, dia de outubro, nota (null = a auditoria falhou)
const HISTORY: Array<[number, number, number | null]> = [
  [12, 20, 41], [16, 22, 46], [20, 24, 50], [24, 26, 53], [27, 27, null],
  [31, 28, 54], [36, 29, 61], [40, 29, 68], [44, 30, 72], [48, 30, 77],
];

const runOf = ([id, day, overall]: (typeof HISTORY)[number]) => ({
  id,
  systemId: 1,
  systemName: system.name,
  branch: 'main',
  status: overall === null ? 'FAILED' : 'COMPLETED',
  riskScore: overall === null ? null : { overall, ...SCORE },
  findingsSummary: { CRITICAL: 0, HIGH: 2, MEDIUM: 5, LOW: 3, INFO: 1 },
  startedAt: `2026-10-${day}T10:00:00`,
  completedAt: `2026-10-${day}T10:03:00`,
  totalFindings: 11,
  errorMessage: overall === null ? 'Tempo esgotado' : null,
});

const plan = (score: number) => ({ scoreNow: score, scoreIfCriticalFixed: score, criticalCount: 0, items: [] });

/** A API simulada: o histórico do sistema e, para cada auditoria, o resultado e o plano de correção (as chaves mais específicas vêm primeiro). */
function handlers() {
  const byPath: Record<string, (method: string, body: unknown) => unknown> = {};
  for (const entry of HISTORY) byPath[`/audits/${entry[0]}/fix-plan`] = () => plan(entry[2] ?? 0);
  for (const entry of HISTORY) byPath[`/audits/${entry[0]}/insights`] = () => ({ analyzers: [] });
  byPath['/systems/1/audits'] = () => HISTORY.map(runOf);
  for (const entry of HISTORY) byPath[`/audits/${entry[0]}`] = () => runOf(entry);
  return byPath;
}

test('o gráfico das últimas auditorias abre o resultado da auditoria clicada', async ({ page }) => {
  await mockApi(page, handlers());
  await page.goto('/audits/48');

  await expect(page.getByRole('heading', { level: 1, name: /resultado da auditoria #48/ })).toBeVisible();
  const chart = page.getByRole('region', { name: 'Últimas auditorias' });
  await expect(chart.getByRole('link', { name: /Abrir a auditoria/ })).toHaveCount(10);
  await expect(chart.getByRole('link', { name: /auditoria 48/ })).toHaveAttribute('aria-current', 'page');

  // o medidor mora no veredito, e o gráfico fica na linha de cartões logo abaixo dele
  const verdict = page.getByRole('region', { name: /Etapa 1/ });
  await expect(verdict.getByRole('img', { name: 'Nota 77 de 100, meta 75' })).toBeVisible();
  const verdictBox = await verdict.boundingBox();
  const chartBox = await chart.boundingBox();
  expect(chartBox!.y).toBeGreaterThan(verdictBox!.y + verdictBox!.height - 1);

  // clicar no valor da coluna abre aquela auditoria
  await chart.getByRole('link', { name: /Abrir a auditoria 36/ }).getByText('61').click();
  await expect(page).toHaveURL(/\/audits\/36$/);
  await expect(page.getByRole('heading', { level: 1, name: /resultado da auditoria #36/ })).toBeVisible();
  await expect(chart.getByRole('link', { name: /auditoria 36/ })).toHaveAttribute('aria-current', 'page');
});

test('a auditoria que falhou abre a tela de andamento com o erro', async ({ page }) => {
  await mockApi(page, handlers());
  await page.goto('/audits/48');
  await page.getByRole('region', { name: 'Últimas auditorias' }).getByRole('link', { name: /Abrir a auditoria 27/ }).click();
  await expect(page).toHaveURL(/\/audits\/27$/);
  await expect(page.getByText('A auditoria falhou').first()).toBeVisible();
});

test('a dica da coluna aparece ao focar com o teclado', async ({ page }) => {
  await mockApi(page, handlers());
  await page.goto('/audits/48');
  const column = page.getByRole('region', { name: 'Últimas auditorias' }).getByRole('link', { name: /Abrir a auditoria 31/ });
  await column.focus();
  await expect(page.getByRole('status')).toContainText('Auditoria #31');
  await expect(page.getByRole('status')).toContainText('Clique para abrir o resultado');
});
