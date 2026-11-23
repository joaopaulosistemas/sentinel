import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { AuditAgeBadge, auditAgeText } from './AuditAgeBadge';

describe('AuditAgeBadge', () => {
  it('diz há quantos dias o sistema não é auditado, no nível de atenção', () => {
    render(<AuditAgeBadge age={{ level: 'attention', days: 9 }} />);
    const badge = screen.getByTestId('audit-age-badge');
    expect(badge).toHaveTextContent('Sem auditar há 9 dias');
    expect(badge).toHaveAttribute('data-level', 'attention');
    expect(badge).toHaveAttribute('title', expect.stringContaining('há 9 dias'));
  });

  it('usa o nível crítico para mais de duas semanas', () => {
    render(<AuditAgeBadge age={{ level: 'critical', days: 23 }} />);
    expect(screen.getByTestId('audit-age-badge')).toHaveAttribute('data-level', 'critical');
  });

  it('mostra "Nunca auditado" sem número de dias', () => {
    render(<AuditAgeBadge age={{ level: 'never', days: null }} />);
    expect(screen.getByTestId('audit-age-badge')).toHaveTextContent('Nunca auditado');
    expect(auditAgeText({ level: 'never', days: null })).toBe('Nunca auditado');
  });
});
