import { describe, expect, it } from 'vitest';
import type { McpCall, PendingAction } from '../types';
import { callsSince, mcpCallEvents, mergeEvents, pendingCount } from './mcpActivity';

const call = (tool: string, at: string, ok = true, detail: string | null = null): McpCall => ({ tool, ok, durationMs: 12, detail, at });

describe('mcpActivity', () => {
  const started = '2026-10-26T10:00:00';

  it('ignora chamadas anteriores ao início da auditoria e ordena da mais recente para a mais antiga', () => {
    const calls = [call('status_auditoria', '2026-10-26T09:59:59'), call('buscar_achados', '2026-10-26T10:01:00'), call('diff_auditoria', '2026-10-26T10:02:00')];
    expect(callsSince(calls, started).map((c) => c.tool)).toEqual(['diff_auditoria', 'buscar_achados']);
  });

  it('sem horário de início, mantém todas as chamadas', () => {
    expect(callsSince([call('a', '2026-10-26T09:00:00')], null)).toHaveLength(1);
  });

  it('resposta que não é lista vira lista vazia', () => {
    expect(callsSince({ id: 1 }, started)).toEqual([]);
    expect(mcpCallEvents(undefined, started)).toEqual([]);
    expect(pendingCount('x')).toBe(0);
  });

  it('chamadas viram eventos; as recusadas dizem o motivo', () => {
    const events = mcpCallEvents([call('buscar_achados', '2026-10-26T10:01:00'), call('suprimir_achado', '2026-10-26T10:02:00', false, 'ferramenta desligada')], started);
    expect(events.map((e) => e.message)).toEqual([
      'Claude (MCP): suprimir_achado recusada (ferramenta desligada)',
      'Claude (MCP) chamou buscar_achados',
    ]);
  });

  it('junta os eventos da auditoria e os do Claude por horário', () => {
    const merged = mergeEvents(
      [{ at: '2026-10-26T10:00:05', message: 'Clone concluído' }, { at: '2026-10-26T10:03:00', message: 'Semgrep concluído' }],
      [{ at: '2026-10-26T10:01:00', message: 'Claude (MCP) chamou status_auditoria' }],
    );
    expect(merged.map((e) => e.message)).toEqual(['Clone concluído', 'Claude (MCP) chamou status_auditoria', 'Semgrep concluído']);
  });

  it('sem eventos do Claude devolve a mesma lista', () => {
    const base = [{ at: '2026-10-26T10:00:05', message: 'x' }];
    expect(mergeEvents(base, [])).toBe(base);
  });

  it('conta só os pedidos que esperam aprovação', () => {
    const action = (status: PendingAction['status']): PendingAction => ({
      id: 1, kind: 'START_AUDIT', summary: 's', status, createdAt: '', expiresAt: '', decidedAt: null, resultNote: null, auditRunId: null,
    });
    expect(pendingCount([action('PENDING'), action('APPROVED'), action('PENDING'), action('EXPIRED')])).toBe(2);
  });
});
