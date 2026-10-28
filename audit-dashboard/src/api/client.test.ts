import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from './client';

function stub(res: Response) {
  vi.stubGlobal('fetch', vi.fn(async () => res));
}

afterEach(() => vi.unstubAllGlobals());

describe('apiFetch: mensagem de erro', () => {
  it('usa a mensagem do JSON da API', async () => {
    stub(new Response(JSON.stringify({ message: 'Para guardar o token, defina SECRETS_KEY' }), { status: 400, headers: { 'Content-Type': 'application/json' } }));
    await expect(api.getMcp(1)).rejects.toThrow('Para guardar o token, defina SECRETS_KEY');
  });

  it('usa o texto curto quando o corpo não é JSON', async () => {
    stub(new Response('Perfil não encontrado: x', { status: 400 }));
    await expect(api.getMcp(1)).rejects.toThrow('Perfil não encontrado: x');
  });

  it('não mostra página HTML de erro', async () => {
    stub(new Response('<html><body>Bad gateway</body></html>', { status: 502, statusText: 'Bad Gateway' }));
    await expect(api.getMcp(1)).rejects.toThrow('HTTP 502: Bad Gateway');
  });

  it('sem corpo cai no código HTTP', async () => {
    stub(new Response(null, { status: 400, statusText: 'Bad Request' }));
    await expect(api.getMcp(1)).rejects.toThrow('HTTP 400: Bad Request');
  });
});

describe('apiFetch: resposta de sucesso sem corpo', () => {
  it('202 sem corpo (reinício da API) não tenta ler JSON', async () => {
    stub(new Response(null, { status: 202 }));
    await expect(api.restartApi()).resolves.toBeUndefined();
  });

  it('204 continua sem corpo', async () => {
    stub(new Response(null, { status: 204 }));
    await expect(api.restartApi()).resolves.toBeUndefined();
  });

  it('200 com corpo vazio também não quebra', async () => {
    stub(new Response('', { status: 200 }));
    await expect(api.restartApi()).resolves.toBeUndefined();
  });

  it('200 com JSON continua devolvendo o objeto', async () => {
    stub(new Response(JSON.stringify({ restartRequired: true, settings: [] }), { status: 200 }));
    await expect(api.getSettings()).resolves.toEqual({ restartRequired: true, settings: [] });
  });
});
