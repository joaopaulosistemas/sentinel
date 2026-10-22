import { describe, expect, it } from 'vitest';
import { timeAgo } from './timeAgo';

const now = Date.parse('2026-10-22T12:00:00');

describe('timeAgo', () => {
  it('cobre agora, minutos, horas, ontem e dias', () => {
    expect(timeAgo('2026-10-22T11:59:40', now)).toBe('agora');
    expect(timeAgo('2026-10-22T11:48:00', now)).toBe('há 12 min');
    expect(timeAgo('2026-10-22T10:00:00', now)).toBe('há 2 h');
    expect(timeAgo('2026-10-21T10:00:00', now)).toBe('ontem');
    expect(timeAgo('2026-10-16T12:00:00', now)).toBe('há 6 dias');
  });

  it('sem data devolve traço', () => {
    expect(timeAgo(null, now)).toBe('-');
    expect(timeAgo('lixo', now)).toBe('-');
  });
});
