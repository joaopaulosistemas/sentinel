import { describe, expect, it } from 'vitest';
import { shouldCheckOnBlur } from './sourceCheck';

describe('shouldCheckOnBlur', () => {
  it('confere quando o valor é válido e nada foi conferido', () => {
    expect(shouldCheckOnBlur(undefined, { data: undefined, isPending: false })).toBe(true);
  });
  it('não confere com erro de validação', () => {
    expect(shouldCheckOnBlur('endereço inválido', { isPending: false })).toBe(false);
  });
  it('não confere de novo quando já há resultado', () => {
    expect(shouldCheckOnBlur(undefined, { data: { ok: true }, isPending: false })).toBe(false);
  });
  it('não confere enquanto outra conferência está em andamento', () => {
    expect(shouldCheckOnBlur(undefined, { isPending: true })).toBe(false);
  });
});
