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

describe('chamadas com vários valores recebem um objeto', () => {
  function stubCapture() {
    const fetchMock = vi.fn(async (_url: string, _init?: RequestInit) => new Response('{}', { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
  }

  it('checkRepo envia só o que foi preenchido', async () => {
    const fetchMock = stubCapture();
    await api.checkRepo({ repoUrl: 'https://git/x', branch: '', systemId: 7, gitToken: '', gitTokenHosts: 'git' });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/v1/systems/check-repo');
    expect(JSON.parse(String(init?.body))).toEqual({ repoUrl: 'https://git/x', systemId: 7, gitTokenHosts: 'git' });
  });

  it('reviewAction usa o sistema e o caminho na URL e o corpo no POST', async () => {
    const fetchMock = stubCapture();
    await api.reviewAction({ systemId: 3, path: 'run', body: { a: 1 } });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/v1/claude-review/3/run');
    expect(init?.body).toBe('{"a":1}');
  });

  it('decidePendingAction monta a URL com a decisão', async () => {
    const fetchMock = stubCapture();
    await api.decidePendingAction({ systemId: 2, actionId: 9, decision: 'reject' });
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/pending-actions/2/9/reject');
  });
});
