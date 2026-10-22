import { InlineNotification, SkeletonText } from '@carbon/react';
import { Link } from 'react-router-dom';
import { Fragment, useState } from 'react';
import { useFixPlan } from '../hooks/useAuditRuns';
import type { AuditRunResponse, Category, Severity } from '../types';
import { TARGET_SCORE, categoriesByScore, categoryLabels, verdict } from '../utils/resultVerdict';

const toneColor = { good: '#198038', warn: '#8a6a00', bad: '#a2191f' } as const;
const sevBadge: Record<Severity, { label: string; bg: string; fg: string }> = {
  CRITICAL: { label: 'CRÍTICA', bg: '#a2191f', fg: '#ffffff' },
  HIGH: { label: 'ALTA', bg: '#ff832b', fg: '#161616' },
  MEDIUM: { label: 'MÉDIA', bg: '#f1c21b', fg: '#161616' },
  LOW: { label: 'BAIXA', bg: '#c6c6c6', fg: '#161616' },
  INFO: { label: 'INFO', bg: '#a6c8ff', fg: '#161616' },
};

/** Texto com trechos entre ** ** em negrito. */
function Emphasis({ text }: { text: string }) {
  return (
    <>
      {text.split('**').map((part, i) => (i % 2 === 1 ? <strong key={i} style={{ fontWeight: 600, color: '#161616' }}>{part}</strong> : <Fragment key={i}>{part}</Fragment>))}
    </>
  );
}

const scoreColor = (n: number) => (n >= TARGET_SCORE ? '#198038' : n >= 50 ? '#8a6a00' : '#a2191f');

/** Resultado em três etapas, do geral para o detalhe: veredito, onde está o risco, o que corrigir primeiro. */
export function ResultSteps({ run, onOpenFindings }: { run: AuditRunResponse; onOpenFindings: () => void }) {
  const { data: plan, isLoading, error } = useFixPlan(run.id, run.status === 'COMPLETED');
  const [category, setCategory] = useState<Category | null>(null);

  if (isLoading) return <SkeletonText paragraph lineCount={5} />;
  if (error || !plan) {
    return (
      <InlineNotification
        kind="error"
        title="Não foi possível montar o resultado"
        subtitle={error instanceof Error ? error.message : 'Erro desconhecido'}
        hideCloseButton
      />
    );
  }

  const v = verdict(plan);
  const cats = categoriesByScore(run.riskScore);
  const items = plan.items.filter((i) => category === null || i.category === category).slice(0, 4);
  const box = { background: '#fff', border: '1px solid #e0e0e0', padding: '1.25rem 1.5rem' } as const;
  const eyebrow = { fontSize: '0.75rem', fontWeight: 600, letterSpacing: '0.04em', textTransform: 'uppercase', color: '#525252' } as const;

  return (
    <div style={{ display: 'grid', gap: '1.5rem' }}>
      <section aria-labelledby="etapa-1" style={{ ...box, borderLeft: `4px solid ${toneColor[v.tone]}`, display: 'flex', gap: '2rem', alignItems: 'center', flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', minWidth: 120 }}>
          <span style={{ fontSize: 56, lineHeight: 1, fontWeight: 300, color: toneColor[v.tone] }}>{Math.round(plan.scoreNow)}</span>
          <span style={{ fontSize: 12, color: '#525252' }}>
            de 100
            {run.scoreDelta != null && run.previousAuditId != null && (
              <>
                {' · '}
                <span style={{ color: run.scoreDelta >= 0 ? '#198038' : '#a2191f', fontWeight: 600 }}>
                  {run.scoreDelta >= 0 ? '+' : ''}{Math.round(run.scoreDelta)}
                </span>{' '}
                vs #{run.previousAuditId}
              </>
            )}
          </span>
        </div>
        <div style={{ flex: 1, minWidth: 320, display: 'flex', flexDirection: 'column', gap: 6 }}>
          <span id="etapa-1" style={eyebrow}>Etapa 1 · Veredito</span>
          <span style={{ fontSize: 20 }}><Emphasis text={v.headline} /></span>
          <span style={{ color: '#525252' }}><Emphasis text={v.detail} /></span>
        </div>
        {plan.items.length > 0 && (
          <a href="#criticos" style={{ height: 40, display: 'inline-flex', alignItems: 'center', padding: '0 20px', background: '#161616', color: '#fff', textDecoration: 'none' }}>
            Ver o que corrigir primeiro
          </a>
        )}
      </section>

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 5fr) minmax(0, 7fr)', gap: '1.5rem' }}>
        <section aria-labelledby="etapa-2" style={{ ...box, display: 'grid', gap: '0.75rem', alignContent: 'start' }}>
          <div>
            <div id="etapa-2" style={eyebrow}>Etapa 2 · Onde está o risco</div>
            <div style={{ fontWeight: 600 }}>Por categoria, da pior para a melhor</div>
          </div>
          {cats.map((c) => {
            const active = category === c.category;
            return (
              <button
                key={c.category}
                type="button"
                aria-pressed={active}
                onClick={() => setCategory(active ? null : c.category)}
                style={{
                  display: 'grid',
                  gridTemplateColumns: '150px 1fr 40px',
                  gap: '0.75rem',
                  alignItems: 'center',
                  padding: '0.5rem 0.625rem',
                  background: active ? '#edf5ff' : '#fff',
                  border: `1px solid ${active ? '#0f62fe' : 'transparent'}`,
                  cursor: 'pointer',
                  font: 'inherit',
                  textAlign: 'left',
                }}
              >
                <span style={{ fontWeight: 500 }}>{c.label}</span>
                <span style={{ height: 8, background: '#e0e0e0' }}>
                  <span style={{ display: 'block', height: 8, width: `${Math.max(0, Math.min(100, c.score))}%`, background: scoreColor(c.score) }} />
                </span>
                <span style={{ textAlign: 'right', fontWeight: 600, color: scoreColor(c.score) }}>{Math.round(c.score)}</span>
              </button>
            );
          })}
          <span style={{ fontSize: '0.75rem', color: '#525252' }}>Clique numa categoria para filtrar a lista ao lado.</span>
        </section>

        <section id="criticos" aria-labelledby="etapa-3" style={box}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', gap: 12, flexWrap: 'wrap', marginBottom: '0.5rem' }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              <span id="etapa-3" style={eyebrow}>Etapa 3 · O que corrigir primeiro</span>
              <span style={{ fontSize: 16, fontWeight: 600 }}>
                {category
                  ? `Achados de ${categoryLabels[category]}`
                  : plan.criticalCount > 0
                    ? `${plan.criticalCount === 1 ? 'O crítico primeiro' : `Os ${plan.criticalCount} críticos primeiro`}, por ganho de nota`
                    : 'Em ordem de ganho de nota'}
              </span>
            </div>
            <Link to={`/findings?auditId=${run.id}`} onClick={onOpenFindings} style={{ fontSize: 13 }}>
              {run.totalFindings != null ? `Ver todos os ${run.totalFindings} achados` : 'Ver todos os achados'}
            </Link>
          </div>
          {items.length === 0 ? (
            <p style={{ color: '#525252' }}>Nada a corrigir nesta categoria.</p>
          ) : (
            <ol style={{ listStyle: 'none', margin: 0, padding: 0 }}>
              {items.map((item, i) => (
                <li
                  key={item.ruleId ?? item.title}
                  style={{ display: 'grid', gridTemplateColumns: '28px minmax(0, 1fr) auto', gap: '0.75rem', padding: '0.75rem 0', borderTop: '1px solid #e0e0e0' }}
                >
                  <span style={{ width: 22, height: 22, borderRadius: 11, background: '#161616', color: '#fff', fontSize: '0.75rem', fontWeight: 600, display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}>
                    {i + 1}
                  </span>
                  <div style={{ display: 'grid', gap: 2, minWidth: 0 }}>
                    <strong style={{ fontWeight: 600 }}>{item.title}</strong>
                    {item.exampleFile && (
                      <code style={{ fontSize: '0.75rem', color: '#525252', wordBreak: 'break-all' }}>
                        {item.exampleFile}
                        {item.exampleLine ? `:${item.exampleLine}` : ''}
                        {item.occurrences > 1 ? `  (+${item.occurrences - 1} ocorrências)` : ''}
                      </code>
                    )}
                    {item.howToFix && <span style={{ color: '#393939' }}>Como corrigir: {item.howToFix}</span>}
                  </div>
                  <div style={{ display: 'grid', gap: 4, justifyItems: 'end' }}>
                    <span style={{ background: sevBadge[item.severity].bg, color: sevBadge[item.severity].fg, padding: '2px 8px', fontSize: 12, fontWeight: 600 }}>
                      {sevBadge[item.severity].label}
                    </span>
                    <span style={{ fontSize: 12, fontWeight: 600, color: '#198038' }}>nota +{item.scoreGain.toFixed(1)}</span>
                  </div>
                </li>
              ))}
            </ol>
          )}
        </section>
      </div>
    </div>
  );
}
