import { describe, expect, it } from 'vitest';
import { TARGET_SCORE, scoreBand, scoreColor, scoreTagType } from './scoreBand';

describe('scoreBand', () => {
  it('usa a meta de 75 como limite da faixa boa, em qualquer tela', () => {
    expect(TARGET_SCORE).toBe(75);
    expect(scoreBand(75)).toBe('good');
    expect(scoreBand(74.6)).toBe('good'); // arredonda como a tela mostra
    expect(scoreBand(74)).toBe('warn');
    expect(scoreBand(50)).toBe('warn');
    expect(scoreBand(49)).toBe('bad');
  });

  it('sem nota não é nem boa nem ruim', () => {
    expect(scoreBand(null)).toBe('none');
    expect(scoreBand(undefined)).toBe('none');
    expect(scoreTagType(null)).toBe('gray');
  });

  it('a mesma nota tem a mesma cor e a mesma etiqueta', () => {
    expect(scoreColor(77)).toBe('var(--s-success-text)');
    expect(scoreTagType(77)).toBe('green');
    expect(scoreColor(60)).toBe('var(--s-warn-text)');
    expect(scoreTagType(60)).toBe('warm-gray');
    expect(scoreColor(10)).toBe('var(--s-danger-text)');
    expect(scoreTagType(10)).toBe('red');
  });
});
