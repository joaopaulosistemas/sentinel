import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { IncompleteNotice } from './IncompleteNotice';

describe('IncompleteNotice', () => {
  it('não mostra nada quando a auditoria está completa', () => {
    const { container } = render(<IncompleteNotice incomplete={false} reasons={[]} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('avisa que a nota pode estar otimista e lista os analisadores', () => {
    render(<IncompleteNotice incomplete reasons={['trivy: falhou', 'osv: parcial']} />);
    expect(screen.getByRole('status')).toHaveTextContent(/resultado incompleto/i);
    expect(screen.getByText('trivy: falhou')).toBeInTheDocument();
    expect(screen.getByText('osv: parcial')).toBeInTheDocument();
  });

  it('ignora campos ausentes de auditorias antigas', () => {
    const { container } = render(<IncompleteNotice incomplete={undefined} reasons={undefined} />);
    expect(container).toBeEmptyDOMElement();
  });
});
