import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { CheckIcon, ClockIcon, SpinnerIcon } from './StatusIcons';

describe('StatusIcons', () => {
  it('o spinner usa a classe animada e expõe o rótulo', () => {
    render(<SpinnerIcon label="Executando" />);
    const svg = screen.getByRole('img', { name: 'Executando' });
    expect(svg.getAttribute('class')).toContain('sentinel-spin');
  });

  it('sem rótulo o ícone é decorativo', () => {
    const { container } = render(
      <>
        <CheckIcon />
        <ClockIcon />
      </>,
    );
    for (const svg of container.querySelectorAll('svg')) {
      expect(svg.getAttribute('aria-hidden')).toBe('true');
    }
  });
});
