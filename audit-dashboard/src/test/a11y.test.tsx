import { describe, it } from 'vitest';
import { render } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { AppShell } from '../components/Layout/AppShell';
import { RiskBadge } from '../components/RiskBadge';
import { ScoreGauge } from '../components/ScoreGauge';
import { expectNoAxeViolations } from './axe';

describe('acessibilidade (axe)', () => {
  it('casca da aplicação sem violações', async () => {
    const { container } = render(
      <QueryClientProvider client={new QueryClient()}><MemoryRouter>
        <AppShell>
          <h1>Visão geral</h1>
        </AppShell>
      </MemoryRouter></QueryClientProvider>,
    );
    await expectNoAxeViolations(container);
  });

  it('badge e nota sem violações', async () => {
    const { container } = render(
      <div>
        <RiskBadge severity="HIGH" />
        <ScoreGauge score={55} />
      </div>,
    );
    await expectNoAxeViolations(container);
  });
});
