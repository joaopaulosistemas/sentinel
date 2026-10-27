import { defineConfig } from '@playwright/test';

// Os testes e2e rodam contra o dashboard real (vite dev) com a API simulada por rota, então não precisam de backend.
// Em CI sem o navegador do Playwright, aponte E2E_CHROMIUM para um Chromium instalado.
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  use: {
    baseURL: 'http://localhost:3000',
    launchOptions: process.env.E2E_CHROMIUM ? { executablePath: process.env.E2E_CHROMIUM } : {},
  },
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:3000',
    reuseExistingServer: true,
    timeout: 60_000,
  },
});
