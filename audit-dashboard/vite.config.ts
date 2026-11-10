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
        manualChunks: {
          carbon: ['@carbon/react', '@carbon/icons-react'],
          charts: ['recharts'],
          react: ['react', 'react-dom', 'react-router-dom', '@tanstack/react-query'],
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
