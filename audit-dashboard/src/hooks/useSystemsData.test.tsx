import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../api/client';
import type { IAuditRunResponse, IScheduleRequest, ISystemRequest, ISystemSummaryResponse } from '../types';
import { useCancelAudit } from './useAuditRuns';
import { useTriggerAudit } from './useStartAudit';
import { persistSystem, useRemoveSystem } from './useSystems';

vi.mock('../api/client', () => ({
  api: {
    createSystem: vi.fn(),
    updateSystem: vi.fn(),
    updateSchedule: vi.fn(),
    deleteSystem: vi.fn(),
    triggerAudit: vi.fn(),
    cancelAudit: vi.fn(),
  },
}));

const body = { name: 'Pagamentos' } as ISystemRequest;
const schedule = { auditIntervalHours: 24 } as IScheduleRequest;
const saved = { id: 7, name: 'Pagamentos' } as ISystemSummaryResponse;

function wrapperWith(client: QueryClient) {
  return ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe('persistSystem', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.createSystem).mockResolvedValue(saved);
    vi.mocked(api.updateSystem).mockResolvedValue(saved);
    vi.mocked(api.updateSchedule).mockResolvedValue({ ...saved, auditIntervalHours: 24 });
  });

  it('cadastra quando não há sistema atual e não mexe no agendamento sem mudança', async () => {
    const result = await persistSystem({ body, schedule: null });

    expect(api.createSystem).toHaveBeenCalledWith(body);
    expect(api.updateSystem).not.toHaveBeenCalled();
    expect(api.updateSchedule).not.toHaveBeenCalled();
    expect(result).toBe(saved);
  });

  it('atualiza o sistema existente e grava o agendamento no id devolvido', async () => {
    const result = await persistSystem({ currentId: 7, body, schedule });

    expect(api.updateSystem).toHaveBeenCalledWith(7, body);
    expect(api.createSystem).not.toHaveBeenCalled();
    expect(api.updateSchedule).toHaveBeenCalledWith(7, schedule);
    expect(result).toMatchObject({ auditIntervalHours: 24 });
  });
});

describe('useRemoveSystem', () => {
  it('remove e limpa o cache do sistema e das listas', async () => {
    vi.mocked(api.deleteSystem).mockResolvedValue(undefined as never);
    const client = new QueryClient();
    client.setQueryData(['systems', 7], saved);
    const invalidate = vi.spyOn(client, 'invalidateQueries');
    const { result } = renderHook(() => useRemoveSystem(), { wrapper: wrapperWith(client) });

    await act(async () => {
      await result.current.mutateAsync(saved);
    });

    expect(api.deleteSystem).toHaveBeenCalledWith(7);
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['systems'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['audits'] });
    expect(client.getQueryData(['systems', 7])).toBeUndefined();
  });
});

describe('useTriggerAudit', () => {
  it('atualiza as listas e entrega a auditoria criada a quem chamou', async () => {
    const run = { id: 42 } as IAuditRunResponse;
    vi.mocked(api.triggerAudit).mockResolvedValue(run);
    const client = new QueryClient();
    const invalidate = vi.spyOn(client, 'invalidateQueries');
    const onDone = vi.fn();
    const { result } = renderHook(() => useTriggerAudit(onDone), { wrapper: wrapperWith(client) });

    act(() => result.current.mutate({ systemId: 7, branch: 'main' }));

    await waitFor(() => expect(onDone).toHaveBeenCalledTimes(1));
    expect(api.triggerAudit).toHaveBeenCalledWith({ systemId: 7, branch: 'main' });
    expect(onDone.mock.calls[0][0]).toBe(run);
    expect(typeof onDone.mock.calls[0][1]).toBe('function');
    for (const key of ['systems', 'auditRuns', 'recentAudits']) {
      expect(invalidate).toHaveBeenCalledWith({ queryKey: [key] });
    }
  });
});

describe('useCancelAudit', () => {
  it('cancela a auditoria e atualiza a tela dela e a lista recente', async () => {
    vi.mocked(api.cancelAudit).mockResolvedValue({ id: 9 } as IAuditRunResponse);
    const client = new QueryClient();
    const invalidate = vi.spyOn(client, 'invalidateQueries');
    const { result } = renderHook(() => useCancelAudit(9), { wrapper: wrapperWith(client) });

    await act(async () => {
      await result.current.mutateAsync();
    });

    expect(api.cancelAudit).toHaveBeenCalledWith(9);
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['audit', 9] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['recentAudits'] });
  });
});
