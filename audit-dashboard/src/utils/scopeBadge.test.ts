import { describe, expect, it } from 'vitest';
import { scopeBadgeText } from './scopeBadge';

describe('selo de escopo da auditoria', () => {
  it('diz qual lado estava desligado', () => {
    expect(scopeBadgeText(['front'])).toBe('front-end desligado');
    expect(scopeBadgeText(['back'])).toBe('back-end desligado');
  });
  it('fica vazio quando os dois lados estavam ligados ou o escopo é desconhecido', () => {
    expect(scopeBadgeText([])).toBe('');
    expect(scopeBadgeText(null)).toBe('');
    expect(scopeBadgeText(undefined)).toBe('');
  });
});
