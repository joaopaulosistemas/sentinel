import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { useRefreshListsWhenFinished } from './AuditRunPage';

function Probe({ finished, runId }: { finished: boolean; runId: number }) {
  useRefreshListsWhenFinished({ finished, runId });
  return null;
}

function montar(finished: boolean) {
  const client = new QueryClient();
  const spy = vi.spyOn(client, 'invalidateQueries');
  const ui = (f: boolean) => (
    <QueryClientProvider client={client}>
      <Probe finished={f} runId={27} />
    </QueryClientProvider>
  );
  const view = render(ui(finished));
  return { spy, rerender: (f: boolean) => view.rerender(ui(f)) };
}

const chaves = (spy: { mock: { calls: unknown[][] } }) => spy.mock.calls.map((c) => JSON.stringify((c[0] as { queryKey: unknown[] }).queryKey));

describe('useRefreshListsWhenFinished', () => {
  it('enquanto a auditoria roda não invalida nada', () => {
    const { spy } = montar(false);
    expect(spy).not.toHaveBeenCalled();
  });

  it('ao concluir refaz os insights e os achados desta auditoria, para a lista de analisadores não ficar vazia', () => {
    const { spy, rerender } = montar(false);
    rerender(true);
    expect(chaves(spy)).toEqual(expect.arrayContaining([JSON.stringify(['insights', 27]), JSON.stringify(['findings', 27]), JSON.stringify(['systems'])]));
  });
});
