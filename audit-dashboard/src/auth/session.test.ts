import { afterEach, describe, expect, it } from 'vitest';
import { authHeader, setAccessToken } from './session';

afterEach(() => setAccessToken(undefined));

describe('session', () => {
  it('sem token não manda cabeçalho de autorização', () => {
    expect(authHeader()).toEqual({});
  });

  it('com token manda Bearer', () => {
    setAccessToken('abc');
    expect(authHeader()).toEqual({ Authorization: 'Bearer abc' });
  });

  it('limpar o token (sair) remove o cabeçalho', () => {
    setAccessToken('abc');
    setAccessToken(undefined);
    expect(authHeader()).toEqual({});
  });
});
