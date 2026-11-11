import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { readFileSync } from 'node:fs';

const pkg = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf-8'));

// Em desenvolvimento o dashboard roda em http://localhost:3000 e encaminha /api para a API.
// API_URL e API_KEY (opcional) são lidos do ambiente do processo que roda `npm run dev`.
const apiUrl = process.env.API_URL || 'http://localhost:8080';
const apiKey = process.env.API_KEY;

export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        // Bibliotecas grandes e estáveis em pacotes separados: o navegador as mantém em cache entre versões do app.
        // Função, não objeto: o Rolldown (Vite 8) não aceita o objeto do Rollup. A ordem importa: "react" vem por último
        // porque "react-router-dom" e "@tanstack/react-query" têm "react" no caminho.
        manualChunks(id: string) {
          if (!id.includes('node_modules')) return undefined;
          if (id.includes('/@carbon/')) return 'carbon';
          if (id.includes('/recharts/') || id.includes('/d3-') || id.includes('/victory-vendor/')) return 'charts';
          if (/\/node_modules\/(react|react-dom|react-router|react-router-dom|@tanstack\/react-query|scheduler)\//.test(id)) return 'react';
          return undefined;
        },
      },
    },
  },
  define: { __APP_VERSION__: JSON.stringify(pkg.version) },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    exclude: ['**/node_modules/**', 'e2e/**'],
    // Piso de cobertura (medido em 2026-11: linhas 88,7%, instruções 87,1%, ramos 77,9%, funções 82,7%). Sobe com o tempo.
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/test/**', 'src/**/*.d.ts', 'src/main.tsx'],
      reporter: ['text-summary', 'lcov'],
      thresholds: { lines: 80, statements: 80, branches: 70, functions: 75 },
    },
  },
  server: {
    port: 3000,
    strictPort: true,
    proxy: {
      '/api': {
        target: apiUrl,
        changeOrigin: true,
        headers: apiKey ? { 'X-API-Key': apiKey } : {},
      },
    },
  },
});
