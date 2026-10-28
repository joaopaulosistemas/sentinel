import { InlineNotification, SkeletonText } from '@carbon/react';
import { Link } from 'react-router-dom';
import { Fragment, useState } from 'react';
import { useFixPlan } from '../hooks/useAuditRuns';
import type { AuditRunResponse, Category, Severity } from '../types';
import { categoriesByScore, categoryLabels, verdict } from '../utils/resultVerdict';
import { scoreColor } from '../utils/scoreBand';

const toneColor = { good: 'var(--s-success-text)', warn: 'var(--s-warn-text)', bad: 'var(--s-danger-text)' } as const;
const sevBadge: Record<Severity, { label: string; bg: string; fg: string }> = {
  CRITICAL: { label: 'CRÍTICA', bg: '#a2191f', fg: '#ffffff' },
  HIGH: { label: 'ALTA', bg: '#ff832b', fg: '#161616' },
  MEDIUM: { label: 'MÉDIA', bg: '#f1c21b', fg: '#161616' },
  LOW: { label: 'BAIXA', bg: '#c6c6c6', fg: '#161616' },
  INFO: { label: 'INFO', bg: '#a6c8ff', fg: '#161616' },
};

/** Divide o texto nos pares de ** **; cada trecho leva a posição em que começa, que é uma chave estável (o texto não muda). */
function splitEmphasis(text: string): Array<{ start: number; part: string; bold: boolean }> {
  let start = 0;
  return text.split('**').map((part, index) => {
    const segment = { start, part, bold: index % 2 === 1 };
    start += part.length + 2;
    return segment;
  });
}

/** Texto com trechos entre ** ** em negrito. */
function Emphasis({ text }: { text: string }) {
  return (
    <>
      {splitEmphasis(text).map((s) => (s.bold ? <strong key={s.start} style={{ fontWeight: 600, color: 'var(--s-text)' }}>{s.part}</strong> : <Fragment key={s.start}>{s.part}</Fragment>))}
    </>
  );
}

const box = { background: 'var(--s-surface)', border: '1px solid var(--s-border)', padding: '1.25rem 1.5rem' } as const;
const eyebrow = { fontSize: '0.75rem', fontWeight: 600, letterSpacing: '0.04em', textTransform: 'uppercase', color: 'var(--s-text-3)' } as const;

type Plan = NonNullable<ReturnType<typeof useFixPlan>['data']>;
type PlanItem = Plan['items'][number];

function DeltaVsPrevious({ run }: { run: AuditRunResponse }) {
  if (run.scoreDelta == null || run.previousAuditId == null) return null;
  return (
    <>
      {' · '}
      <span style={{ color: run.scoreDelta >= 0 ? 'var(--s-success-text)' : 'var(--s-danger-text)', fontWeight: 600 }}>
        {run.scoreDelta >= 0 ? '+' : ''}{Math.round(run.scoreDelta)}
      </span>{' '}
      vs #{run.previousAuditId}
    </>
  );
}

/** Etapa 1: a nota, o veredito em uma frase e o atalho para a lista de correções. */
function VerdictSection({ run, plan }: { run: AuditRunResponse; plan: Plan }) {
  const v = verdict(plan);
  return (
    <section aria-labelledby="etapa-1" style={{ ...box, borderLeft: `4px solid ${toneColor[v.tone]}`, display: 'flex', gap: '2rem', alignItems: 'center', flexWrap: 'wrap' }}>
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', minWidth: 120 }}>
        <span style={{ fontSize: 56, lineHeight: 1, fontWeight: 300, color: toneColor[v.tone] }}>{Math.round(plan.scoreNow)}</span>
        <span style={{ fontSize: 12, color: 'var(--s-text-3)' }}>
          de 100
          <DeltaVsPrevious run={run} />
        </span>
      </div>
      <div style={{ flex: 1, minWidth: 320, display: 'flex', flexDirection: 'column', gap: 6 }}>
        <span id="etapa-1" style={eyebrow}>Etapa 1 · Veredito</span>
        <span style={{ fontSize: 20 }}><Emphasis text={v.headline} /></span>
        <span style={{ color: 'var(--s-text-3)' }}><Emphasis text={v.detail} /></span>
      </div>
      {plan.items.length > 0 && (
        <a href="#criticos" style={{ height: 40, display: 'inline-flex', alignItems: 'center', padding: '0 20px', background: '#161616', color: '#fff', textDecoration: 'none' }}>
          Ver o que corrigir primeiro
        </a>
      )}
    </section>
  );
}

/** Etapa 2: categorias da pior para a melhor; clicar filtra a lista da etapa 3. */
function CategorySection({ cats, category, onPick }: { cats: ReturnType<typeof categoriesByScore>; category: Category | null; onPick: (c: Category | null) => void }) {
  return (
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
            onClick={() => onPick(active ? null : c.category)}
            style={{
              display: 'grid',
              gridTemplateColumns: '150px 1fr 40px',
              gap: '0.75rem',
              alignItems: 'center',
              padding: '0.5rem 0.625rem',
              background: active ? 'var(--s-selected)' : 'var(--s-surface)',
              border: `1px solid ${active ? '#0f62fe' : 'transparent'}`,
              cursor: 'pointer',
              font: 'inherit',
              textAlign: 'left',
            }}
          >
            <span style={{ fontWeight: 500 }}>{c.label}</span>
            <span style={{ height: 8, background: 'var(--s-border)' }}>
              <span style={{ display: 'block', height: 8, width: `${Math.max(0, Math.min(100, c.score))}%`, background: scoreColor(c.score) }} />
            </span>
            <span style={{ textAlign: 'right', fontWeight: 600, color: scoreColor(c.score) }}>{Math.round(c.score)}</span>
          </button>
        );
      })}
      <span style={{ fontSize: '0.75rem', color: 'var(--s-text-3)' }}>Clique numa categoria para filtrar a lista ao lado.</span>
    </section>
  );
}

function stepThreeTitle(category: Category | null, criticalCount: number): string {
  if (category) return `Achados de ${categoryLabels[category]}`;
  if (criticalCount === 0) return 'Em ordem de ganho de nota';
  return `${criticalCount === 1 ? 'O crítico primeiro' : `Os ${criticalCount} críticos primeiro`}, por ganho de nota`;
}

function FixItem({ item, position }: { item: PlanItem; position: number }) {
  return (
    <li
      style={{ display: 'grid', gridTemplateColumns: '28px minmax(0, 1fr) auto', gap: '0.75rem', padding: '0.75rem 0', borderTop: '1px solid var(--s-border)' }}
    >
      <span style={{ width: 22, height: 22, borderRadius: 11, background: '#161616', color: '#fff', fontSize: '0.75rem', fontWeight: 600, display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}>
        {position}
      </span>
      <div style={{ display: 'grid', gap: 2, minWidth: 0 }}>
        <strong style={{ fontWeight: 600 }}>{item.title}</strong>
        {item.exampleFile && (
          <code style={{ fontSize: '0.75rem', color: 'var(--s-text-3)', wordBreak: 'break-all' }}>
            {item.exampleFile}
            {item.exampleLine ? `:${item.exampleLine}` : ''}
            {item.occurrences > 1 ? `  (+${item.occurrences - 1} ocorrências)` : ''}
          </code>
        )}
        {item.howToFix && <span style={{ color: 'var(--s-text-2)' }}>Como corrigir: {item.howToFix}</span>}
      </div>
      <div style={{ display: 'grid', gap: 4, justifyItems: 'end' }}>
        <span style={{ background: sevBadge[item.severity].bg, color: sevBadge[item.severity].fg, padding: '2px 8px', fontSize: 12, fontWeight: 600 }}>
          {sevBadge[item.severity].label}
        </span>
        <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--s-success-text)' }}>nota +{item.scoreGain.toFixed(1)}</span>
      </div>
    </li>
  );
}

/** Etapa 3: o que corrigir primeiro, no máximo quatro itens (da categoria escolhida, se houver). */
function FixSection({ run, plan, category, items, onOpenFindings }: { run: AuditRunResponse; plan: Plan; category: Category | null; items: PlanItem[]; onOpenFindings: () => void }) {
  return (
    <section id="criticos" aria-labelledby="etapa-3" style={box}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', gap: 12, flexWrap: 'wrap', marginBottom: '0.5rem' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <span id="etapa-3" style={eyebrow}>Etapa 3 · O que corrigir primeiro</span>
          <span style={{ fontSize: 16, fontWeight: 600 }}>{stepThreeTitle(category, plan.criticalCount)}</span>
        </div>
        <Link to={`/findings?auditId=${run.id}`} onClick={onOpenFindings} style={{ fontSize: 13 }}>
          {run.totalFindings != null ? `Ver todos os ${run.totalFindings} achados` : 'Ver todos os achados'}
        </Link>
      </div>
      {items.length === 0 ? (
        <p style={{ color: 'var(--s-text-3)' }}>Nada a corrigir nesta categoria.</p>
      ) : (
        <ol style={{ listStyle: 'none', margin: 0, padding: 0 }}>
          {items.map((item, i) => (
            <FixItem key={item.ruleId ?? item.title} item={item} position={i + 1} />
          ))}
        </ol>
      )}
    </section>
  );
}

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

  const items = plan.items.filter((i) => category === null || i.category === category).slice(0, 4);
  return (
    <div style={{ display: 'grid', gap: '1.5rem' }}>
      <VerdictSection run={run} plan={plan} />
      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 5fr) minmax(0, 7fr)', gap: '1.5rem' }}>
        <CategorySection cats={categoriesByScore(run.riskScore)} category={category} onPick={setCategory} />
        <FixSection run={run} plan={plan} category={category} items={items} onOpenFindings={onOpenFindings} />
      </div>
    </div>
  );
}
