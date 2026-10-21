import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { RiskBadge } from './RiskBadge';

describe('RiskBadge', () => {
  it.each([
    ['CRITICAL', 'Crítico'],
    ['HIGH', 'Alto'],
    ['MEDIUM', 'Médio'],
    ['LOW', 'Baixo'],
    ['INFO', 'Info'],
  ] as const)('mostra o rótulo em português para %s', (severity, rotulo) => {
    render(<RiskBadge severity={severity} />);
    expect(screen.getByText(rotulo)).toBeInTheDocument();
  });
});
