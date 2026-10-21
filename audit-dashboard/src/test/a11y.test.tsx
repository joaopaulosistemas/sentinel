import { describe, expect, it } from 'vitest';
import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { axe } from 'vitest-axe';
import * as matchers from 'vitest-axe/matchers';
import { AppShell } from '../components/Layout/AppShell';
import { RiskBadge } from '../components/RiskBadge';
import { ScoreGauge } from '../components/ScoreGauge';

expect.extend(matchers);

declare module 'vitest' {
  interface Assertion {
    toHaveNoViolations(): void;
  }
}

describe('acessibilidade (axe)', () => {
  it('casca da aplicação sem violações', async () => {
    const { container } = render(
      <MemoryRouter>
        <AppShell>
          <h1>Visão geral</h1>
        </AppShell>
      </MemoryRouter>,
    );
    expect(await axe(container)).toHaveNoViolations();
  });

  it('badge e nota sem violações', async () => {
    const { container } = render(
      <div>
        <RiskBadge severity="HIGH" />
        <ScoreGauge score={55} />
      </div>,
    );
    expect(await axe(container)).toHaveNoViolations();
  });
});
