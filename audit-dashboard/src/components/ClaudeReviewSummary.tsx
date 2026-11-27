import { Link } from 'react-router-dom';
import { useClaudeReview } from '../hooks/useClaudeReview';
import { usePendingActions } from '../hooks/usePendingActions';
import type { IClaudeReview, ISystemSummaryResponse } from '../types';

const STAGES: Array<{ id: string; label: string }> = [
  { id: 'PROPOSED', label: 'Proposta' },
  { id: 'REVIEWED', label: 'Revisada' },
  { id: 'SAMPLE_PASSED', label: 'Amostra ok' },
  { id: 'OBSERVING', label: 'Observando' },
  { id: 'PROMOTED', label: 'Promovida' },
];

/** Quantas regras há em cada etapa do funil (descartadas ficam de fora: é o que ainda anda). */
export function funnelCounts(proposals: IClaudeReview['proposals']): Array<{ id: string; label: string; count: number }> {
  return STAGES.map((s) => ({ ...s, count: proposals.filter((p) => p.stage === s.id).length }));
}

/** O primeiro passo que depende de uma pessoa: texto curto, ou nulo se nada espera. */
export function nextStep(review: IClaudeReview): string | null {
  const order = ['PROPOSED', 'REVIEWED', 'SAMPLE_PASSED'];
  const text: Record<string, string> = {
    PROPOSED: 'revisar a regra antes de testar',
    REVIEWED: 'dizer se o teste de amostra passou',
    SAMPLE_PASSED: 'iniciar a observação',
  };
  for (const stage of order) {
    const rule = review.proposals.find((p) => p.stage === stage);
    if (rule) return `Regra ${rule.title}: ${text[stage]}.`;
  }
  const ready = review.proposals.find((p) => p.stage === 'OBSERVING' && p.evidence.hits >= p.minHits && p.evidence.precision >= p.minPrecision);
  if (ready) return `Regra ${ready.title}: pronta para promover.`;
  return review.openSuggestions > 0 ? `${review.openSuggestions} sugestão(ões) aberta(s) do Claude.` : null;
}

function Tile({ label, value }: { label: string; value: number }) {
  return (
    <div style={{ background: 'var(--s-layer)', padding: '10px 14px', display: 'grid', gap: 2 }}>
      <span style={{ fontSize: 12, color: 'var(--s-text-3)' }}>{label}</span>
      <span style={{ fontSize: 24 }}>{value}</span>
    </div>
  );
}

/**
 * Resumo da revisão do Claude de um sistema, para a página de achados. As decisões e os detalhes ficam na área Claude (MCP):
 * aqui só se vê o tamanho do que espera e para onde ir.
 */
export function ClaudeReviewSummary({ system }: { system: ISystemSummaryResponse | undefined }) {
  const systemId = system?.id;
  const { review } = useClaudeReview(systemId);
  const { list } = usePendingActions(systemId ?? 0);
  if (!system) return <div style={{ fontSize: 14, color: 'var(--s-text-3)' }}>Escolha um sistema para ver o que o Claude deixou para revisar.</div>;
  const data = review.data;
  const pedidos = (list.data ?? []).filter((a) => a.status === 'PENDING').length;
  const inFunnel = data ? data.proposals.filter((p) => p.stage !== 'PROMOTED' && p.stage !== 'DISCARDED').length : 0;
  const step = data ? nextStep(data) : null;
  return (
    <section aria-label="Resumo da revisão do Claude" style={{ display: 'grid', gap: 14 }}>
      <h2 style={{ margin: 0, fontSize: 18, fontWeight: 400 }}>Revisão do Claude · {system.name}</h2>
      {review.isLoading && <div>Carregando…</div>}
      {data && (
        <>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 12 }}>
            <Tile label="Pedidos esperando" value={pedidos} />
            <Tile label="Regras no funil" value={inFunnel} />
            <Tile label="Sugestões abertas" value={data.openSuggestions} />
          </div>
          <div aria-label="Funil das regras" style={{ display: 'flex', gap: 8, flexWrap: 'wrap', fontSize: 13 }}>
            {funnelCounts(data.proposals).map((s) => (
              <span key={s.id} style={{ padding: '2px 10px', background: 'var(--s-layer)' }}>{s.label} {s.count}</span>
            ))}
          </div>
          <div style={{ fontSize: 14 }}>{step ? <>Próximo passo: {step}</> : 'Nada esperando você neste sistema.'}</div>
        </>
      )}
      <div style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
        <Link to={`/claude?aba=decisoes&systemId=${system.id}`}>Abrir revisão completa em Claude (MCP)</Link>
        <span style={{ fontSize: 13, color: 'var(--s-text-3)' }}>Aqui é só o resumo: decidir e ver o histórico fica na área Claude (MCP).</span>
      </div>
    </section>
  );
}
