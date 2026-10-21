import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ScoreGauge } from './ScoreGauge';

describe('ScoreGauge', () => {
  it('mostra "Sem dados" quando não há nota', () => {
    render(<ScoreGauge score={null} />);
    expect(screen.getByText('Sem dados')).toBeInTheDocument();
  });

  it.each([
    [0, 'Crítico'],
    [39, 'Crítico'],
    [40, 'Regular'],
    [69, 'Regular'],
    [70, 'Bom'],
    [100, 'Bom'],
  ])('nota %i é classificada como %s', (score, rotulo) => {
    render(<ScoreGauge score={score} />);
    expect(screen.getByText(String(score))).toBeInTheDocument();
    expect(screen.getByText(rotulo)).toBeInTheDocument();
  });
});
