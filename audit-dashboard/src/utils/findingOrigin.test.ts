import { describe, expect, it } from 'vitest';
import { originLabel } from './findingOrigin';

describe('originLabel', () => {
  it('usa a grafia de exibição dos analisadores conhecidos, sem diferenciar maiúsculas', () => {
    expect(originLabel('semgrep')).toBe('Semgrep');
    expect(originLabel('SONARQUBE')).toBe('SonarQube');
    expect(originLabel('jscpd')).toBe('jscpd');
  });

  it('mostra uma origem nova como veio, para não escondê-la', () => {
    expect(originLabel('novo-analisador')).toBe('novo-analisador');
  });

  it('avisa quando não há origem', () => {
    expect(originLabel(null)).toBe('Origem desconhecida');
    expect(originLabel('  ')).toBe('Origem desconhecida');
  });
});
