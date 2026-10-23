import { InlineNotification } from '@carbon/react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../api/client';
import type { RuleProposalView, SuggestionStatus, SystemSummaryResponse } from '../types';
import { startLabel } from '../utils/timeAgo';

const mono = "'IBM Plex Mono', ui-monospace, monospace";
const btn = { height: 32, padding: '0 12px', border: '1px solid #0f62fe', background: '#fff', color: '#0f62fe', cursor: 'pointer', fontSize: 13 } as const;
const btnDanger = { ...btn, border: '1px solid #da1e28', color: '#da1e28' } as const;

export const pct = (v: number) => `${Math.round(v * 100)}%`;

const STAGE_COLOR: Record<string, string> = {
  PROPOSED: '#6f6f6f',
  REVIEWED: '#0043ce',
  SAMPLE_PASSED: '#0043ce',
  OBSERVING: '#8a3800',
  PROMOTED: '#198038',
  DISCARDED: '#6f6f6f',
};

function Proposal({ p, systems, systemId, act, busy }: {
  p: RuleProposalView;
  systems: SystemSummaryResponse[];
  systemId: number;
  act: (path: string, body?: unknown) => void;
  busy: boolean;
}) {
  const [note, setNote] = useState('');
  const [target, setTarget] = useState('');
  const e = p.evidence;
  return (
    <article aria-label={`Regra ${p.slug}`} style={{ border: '1px solid #e0e0e0', background: '#fff', padding: 16, display: 'grid', gap: 10 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
        <div>
          <strong>{p.title}</strong>
          <div style={{ fontFamily: mono, fontSize: 12, color: '#525252' }}>local-{p.slug}</div>
        </div>
        <span style={{ fontSize: 12, fontWeight: 600, color: STAGE_COLOR[p.stage] }}>{p.stageLabel}</span>
      </div>
      {p.rationale && <p style={{ margin: 0, fontSize: 13, color: '#393939' }}>{p.rationale}</p>}
      <details>
        <summary style={{ cursor: 'pointer', fontSize: 13 }}>Ver a regra Semgrep</summary>
        <pre style={{ margin: '8px 0 0', padding: 12, background: '#f4f4f4', fontFamily: mono, fontSize: 12, overflowX: 'auto' }}>{p.semgrepYaml}</pre>
      </details>
      {p.sampleNote && <div style={{ fontSize: 12, color: '#525252' }}>Teste de amostra: {p.sampleNote}</div>}
      {p.observationStartedAt && (
        <div role="status" style={{ fontSize: 13, background: '#f4f4f4', padding: '8px 12px' }}>
          Observação desde {startLabel(p.observationStartedAt)}: {e.hits} {e.hits === 1 ? 'achado' : 'achados'}, {e.falsePositives} falso(s) positivo(s)
          {e.hits > 0 && <>, precisão {pct(e.precision)}</>}, {e.auditsSinceStart} {e.auditsSinceStart === 1 ? 'auditoria' : 'auditorias'}.
          {p.stage === 'OBSERVING' && <> Para promover: precisão de {pct(p.minPrecision)} ou mais e {p.minHits}+ achados. Marque falso positivo suprimindo o achado.</>}
        </div>
      )}
      {p.decisionNote && <div style={{ fontSize: 12, color: '#525252' }}>Decisão: {p.decisionNote}</div>}
      {p.sharedEligible && (
        <div style={{ fontSize: 13, color: '#198038' }}>Funcionou em outro sistema: pronta para ir ao pacote compartilhado (a inclusão no pacote é feita no repositório dos pacotes).</div>
      )}

      {!['PROMOTED', 'DISCARDED'].includes(p.stage) && (
        <input
          aria-label={`Anotação sobre ${p.slug}`}
          placeholder="Anotação (opcional)"
          value={note}
          onChange={(ev) => setNote(ev.target.value)}
          style={{ height: 32, padding: '0 8px', border: 0, borderBottom: '1px solid #8d8d8d', background: '#f4f4f4' }}
        />
      )}
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        {p.stage === 'PROPOSED' && <button type="button" style={btn} disabled={busy} onClick={() => act(`rules/${p.id}/review`)}>Revisei a regra</button>}
        {p.stage === 'REVIEWED' && (
          <>
            <button type="button" style={btn} disabled={busy} onClick={() => act(`rules/${p.id}/sample`, { passed: true, note })}>Teste de amostra passou</button>
            <button type="button" style={btn} disabled={busy} onClick={() => act(`rules/${p.id}/sample`, { passed: false, note })}>Teste de amostra falhou</button>
          </>
        )}
        {p.stage === 'SAMPLE_PASSED' && <button type="button" style={btn} disabled={busy} onClick={() => act(`rules/${p.id}/observe`)}>Iniciar observação</button>}
        {p.stage === 'OBSERVING' && <button type="button" style={btn} disabled={busy} onClick={() => act(`rules/${p.id}/promote`, { note })}>Promover</button>}
        {!['PROMOTED', 'DISCARDED'].includes(p.stage) && (
          <button type="button" style={btnDanger} disabled={busy} onClick={() => act(`rules/${p.id}/discard`, { note })}>Descartar</button>
        )}
        {p.stage === 'PROMOTED' && (
          <>
            <select aria-label={`Copiar ${p.slug} para`} value={target} onChange={(ev) => setTarget(ev.target.value)} style={{ height: 32 }}>
              <option value="">Copiar para outro sistema…</option>
              {systems.filter((s) => s.id !== systemId).map((s) => (
                <option key={s.id} value={s.id}>{s.name}</option>
              ))}
            </select>
            <button type="button" style={btn} disabled={busy || target === ''} onClick={() => act(`rules/${p.id}/copy`, { targetSystemId: Number(target) })}>Copiar em observação</button>
          </>
        )}
      </div>
    </article>
  );
}

/**
 * Aba "Revisão do Claude": o que o Claude sugeriu e as regras que propôs. Tudo aqui fica fora da nota; uma regra
 * só pesa nela depois de passar pelo funil e ser promovida por uma pessoa.
 */
export function ClaudeReviewPanel({ systems, initialSystemId }: { systems: SystemSummaryResponse[]; initialSystemId?: number }) {
  const queryClient = useQueryClient();
  const [systemId, setSystemId] = useState<number | undefined>(initialSystemId ?? systems[0]?.id);
  const key = ['claude-review', systemId];
  const review = useQuery({ queryKey: key, queryFn: () => api.getClaudeReview(systemId as number), enabled: systemId !== undefined });
  const mutation = useMutation({
    mutationFn: (v: { path: string; body?: unknown }) => api.reviewAction(systemId as number, v.path, v.body),
    onSuccess: (data) => queryClient.setQueryData(key, data),
  });
  const act = (path: string, body?: unknown) => mutation.mutate({ path, body });
  const setStatus = (id: number, status: SuggestionStatus) => act(`suggestions/${id}/status`, { status });
  const data = review.data;

  return (
    <section aria-label="Revisão do Claude" style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <p style={{ margin: 0, color: '#525252', fontSize: 14 }}>
        O que o Claude sugeriu e as regras que propôs. Nada daqui entra na nota: uma regra só passa a pesar depois de revisada, testada,
        observada fora da nota e promovida por uma pessoa.
      </p>
      <label style={{ display: 'grid', gap: 4, fontSize: 12, color: '#525252', maxWidth: 320 }}>
        Sistema
        <select value={systemId ?? ''} onChange={(e) => setSystemId(e.target.value === '' ? undefined : Number(e.target.value))} style={{ height: 40, padding: '0 8px', border: 0, borderBottom: '2px solid #0f62fe', background: '#f4f4f4', fontSize: 14 }}>
          {systems.map((s) => (
            <option key={s.id} value={s.id}>{s.name}</option>
          ))}
        </select>
      </label>
      {mutation.isError && <InlineNotification kind="error" title="Não foi possível" subtitle={(mutation.error as Error).message} hideCloseButton />}
      {review.isLoading && <div>Carregando…</div>}
      {review.isError && <InlineNotification kind="error" title="Não foi possível carregar" subtitle={(review.error as Error).message} hideCloseButton />}
      {data && (
        <>
          <div style={{ display: 'grid', gap: 10 }}>
            <h2 style={{ margin: 0, fontSize: 18, fontWeight: 400 }}>Sugestões ({data.openSuggestions} {data.openSuggestions === 1 ? 'aberta' : 'abertas'})</h2>
            {data.suggestions.length === 0 ? (
              <span style={{ fontSize: 13, color: '#525252' }}>Nenhuma sugestão. Libere a ferramenta registrar_sugestao na aba Claude (MCP) do sistema para o Claude registrar as dele.</span>
            ) : (
              data.suggestions.map((s) => (
                <article key={s.id} aria-label={`Sugestão ${s.id}`} style={{ border: '1px solid #e0e0e0', background: s.status === 'OPEN' ? '#fff' : '#f4f4f4', padding: 14, display: 'grid', gap: 6 }}>
                  <strong>{s.title}</strong>
                  {s.detail && <p style={{ margin: 0, fontSize: 13, color: '#393939' }}>{s.detail}</p>}
                  <div style={{ fontSize: 12, color: '#525252', fontFamily: mono }}>
                    {[s.ruleRef, s.file ? `${s.file}${s.line ? `:${s.line}` : ''}` : null].filter(Boolean).join(' · ')}
                  </div>
                  <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                    <span style={{ fontSize: 12, color: '#525252' }}>{s.status === 'OPEN' ? 'Aberta' : s.status === 'ADDRESSED' ? 'Tratada' : 'Dispensada'}</span>
                    {s.status !== 'ADDRESSED' && <button type="button" style={btn} disabled={mutation.isPending} onClick={() => setStatus(s.id, 'ADDRESSED')}>Tratada</button>}
                    {s.status !== 'DISMISSED' && <button type="button" style={btnDanger} disabled={mutation.isPending} onClick={() => setStatus(s.id, 'DISMISSED')}>Dispensar</button>}
                    {s.status !== 'OPEN' && <button type="button" style={btn} disabled={mutation.isPending} onClick={() => setStatus(s.id, 'OPEN')}>Reabrir</button>}
                  </div>
                </article>
              ))
            )}
          </div>

          <div style={{ display: 'grid', gap: 10 }}>
            <h2 style={{ margin: 0, fontSize: 18, fontWeight: 400 }}>Regras propostas</h2>
            {data.proposals.length === 0 ? (
              <span style={{ fontSize: 13, color: '#525252' }}>Nenhuma regra proposta. O Claude propõe pela ferramenta propor_regra, quando liberada.</span>
            ) : (
              data.proposals.map((p) => (
                <Proposal key={p.id} p={p} systems={systems} systemId={systemId as number} act={act} busy={mutation.isPending} />
              ))
            )}
          </div>
        </>
      )}
    </section>
  );
}
