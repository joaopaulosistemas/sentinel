import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { abrirComToken, aoClicarLink } from './abrirComToken';
import { setAccessToken } from './session';

const URL_RELATORIO = '/api/v1/audits/7/report';
let aba: { location: { href: string }; document: { body: { textContent: string } } };

beforeEach(() => {
  aba = { location: { href: '' }, document: { body: { textContent: '' } } };
  vi.stubGlobal('open', vi.fn(() => aba));
  URL.createObjectURL = vi.fn(() => 'blob:relatorio');
});
afterEach(() => {
  setAccessToken(undefined);
  vi.unstubAllGlobals();
});

describe('abrirComToken', () => {
  it('sem login abre o endereço direto, sem baixar nada', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    await abrirComToken(URL_RELATORIO);
    expect(window.open).toHaveBeenCalledWith(URL_RELATORIO, '_blank', 'noreferrer');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('com login baixa com o token e abre o arquivo recebido na aba já aberta', async () => {
    setAccessToken('tok-123');
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, blob: () => Promise.resolve(new Blob(['<html>'])) });
    vi.stubGlobal('fetch', fetchMock);
    await abrirComToken(URL_RELATORIO);
    expect(fetchMock).toHaveBeenCalledWith(URL_RELATORIO, { headers: { Authorization: 'Bearer tok-123' }, signal: expect.any(AbortSignal) });
    expect(aba.location.href).toBe('blob:relatorio');
  });

  it('com login e erro da API mostra o motivo na aba em vez de uma página em branco', async () => {
    setAccessToken('tok-123');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 403 }));
    await abrirComToken(URL_RELATORIO);
    expect(aba.document.body.textContent).toContain('HTTP 403');
    expect(aba.location.href).toBe('');
  });
});

describe('aoClicarLink', () => {
  it('sem login deixa o navegador seguir o link', () => {
    const preventDefault = vi.fn();
    aoClicarLink(URL_RELATORIO)({ preventDefault });
    expect(preventDefault).not.toHaveBeenCalled();
  });

  it('com login cancela a navegação e baixa com o token', () => {
    setAccessToken('tok-123');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, blob: () => Promise.resolve(new Blob(['x'])) }));
    const preventDefault = vi.fn();
    aoClicarLink(URL_RELATORIO)({ preventDefault });
    expect(preventDefault).toHaveBeenCalled();
    expect(fetch).toHaveBeenCalled();
  });
});
