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

import { startLabel } from './timeAgo';

describe('startLabel', () => {
  const ref = new Date(2026, 9, 22, 12, 0, 0).getTime();
  it('mostra o início como na lista de auditorias', () => {
    expect(startLabel(new Date(2026, 9, 22, 11, 48).toISOString(), ref)).toBe('há 12 min');
    expect(startLabel(new Date(2026, 9, 22, 9, 30).toISOString(), ref)).toBe('hoje 09:30');
    expect(startLabel(new Date(2026, 9, 21, 14, 0).toISOString(), ref)).toBe('ontem 14:00');
    expect(startLabel(new Date(2026, 9, 19, 14, 0).toISOString(), ref)).toBe('19/10 14:00');
  });
});
