import type { Page } from '@playwright/test';

export const system = {
  id: 1, name: 'conta-corrente-api', stack: 'JAVA', team: 'Squad Contas', lastAuditScore: 61,
  lastAuditDate: new Date().toISOString(), openCritical: 1, openHigh: 2,
  repoUrl: 'https://github.com/acme/conta-corrente', scheduledBranch: 'main', auditIntervalHours: 0, auditOnPush: false,
};

/** Responde a API do dashboard por rota. `handlers` sobrescreve por trecho de URL; o resto devolve lista vazia. */
export async function mockApi(page: Page, handlers: Record<string, (method: string, body: unknown) => unknown> = {}) {
  const calls: Array<{ method: string; url: string; body: unknown }> = [];
  await page.route('**/api/v1/**', async (route) => {
    const req = route.request();
    const url = new URL(req.url()).pathname + new URL(req.url()).search;
    let body: unknown = null;
    try { body = req.postDataJSON(); } catch { /* sem corpo */ }
    calls.push({ method: req.method(), url, body });
    const key = Object.keys(handlers).find((k) => url.includes(k));
    const data = key ? handlers[key](req.method(), body) : url.endsWith('/systems') ? [system] : [];
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(data) });
  });
  return calls;
}
