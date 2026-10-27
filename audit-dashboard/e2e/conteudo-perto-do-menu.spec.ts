import { expect, test } from '@playwright/test';
import { mockApi } from './helpers';

// O menu lateral tem 256 px e o recuo é de 40 px: o conteúdo começa em 296 px em qualquer largura de tela.
for (const largura of [1100, 1440, 1920, 2560]) {
  test(`o conteúdo fica colado ao menu com a tela em ${largura} px`, async ({ page }) => {
    await page.setViewportSize({ width: largura, height: 800 });
    await mockApi(page, {});
    await page.goto('/');
    await expect(page.locator('h1').first()).toBeVisible();
    const x = Math.round((await page.locator('h1').first().boundingBox())!.x);
    expect(x).toBe(296);
  });
}
