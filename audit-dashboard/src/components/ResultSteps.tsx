import { InlineNotification, SkeletonText } from '@carbon/react';
import { Link } from 'react-router-dom';
import { Fragment, useState } from 'react';
import { useFixPlan } from '../hooks/useAuditRuns';
import { useInsights } from '../hooks/useLifecycle';
import { ScoreExplanationPanel } from './ScoreExplanationPanel';
import type { AnalyzerRunInfo, AuditRunResponse, Category, Severity } from '../types';
import { categoriesByScore, categoryLabels, verdict } from '../utils/resultVerdict';
import { TARGET_SCORE, scoreColor } from '../utils/scoreBand';

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

const box = { background: 'var(--s-surface)', border: '1px solid var(--s-border)', borderRadius: 8, padding: '1.25rem 1.5rem' } as const;
const SEV_ORDER: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];
const SEV_NAME: Record<Severity, string> = { CRITICAL: 'críticos', HIGH: 'altos', MEDIUM: 'médios', LOW: 'baixos', INFO: 'informativos' };
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

/** Etapa 1: o veredito em uma frase e o atalho para a lista de correções. */
function VerdictSection({ plan }: { plan: Plan }) {
  const v = verdict(plan);
  return (
    <section
      aria-labelledby="etapa-1"
      style={{ ...box, background: 'var(--s-selected)', borderLeft: `4px solid ${toneColor[v.tone]}`, display: 'flex', gap: '1rem 2rem', alignItems: 'center', flexWrap: 'wrap', padding: '16px 24px' }}
    >
      <div style={{ flex: 1, minWidth: 320, display: 'flex', flexDirection: 'column', gap: 4 }}>
        <span id="etapa-1" style={eyebrow}>Etapa 1 · Veredito</span>
        <span style={{ fontSize: 20 }}><Emphasis text={v.headline} /></span>
        <span style={{ color: 'var(--s-text-2)' }}><Emphasis text={v.detail} /></span>
      </div>
      {plan.items.length > 0 && (
        <a href="#criticos" style={{ height: 40, display: 'inline-flex', alignItems: 'center', padding: '0 20px', background: '#161616', color: '#fff', textDecoration: 'none', borderRadius: 6 }}>
          Ver o que corrigir primeiro
        </a>
      )}
    </section>
  );
}

/** Nota em medidor circular, com a meta marcada no anel. */
function ScoreCard({ run, plan }: { run: AuditRunResponse; plan: Plan }) {
  const score = Math.round(plan.scoreNow);
  const pct = Math.max(0, Math.min(100, score));
  const color = scoreColor(score);
  const missing = Math.max(0, TARGET_SCORE - score);
  return (
    <section aria-label="Nota geral" style={{ ...box, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12 }}>
      <div role="img" aria-label={`Nota ${score} de 100, meta ${TARGET_SCORE}`} style={{ position: 'relative', width: 168, height: 168 }}>
        <div style={{ width: '100%', height: '100%', borderRadius: '50%', background: `conic-gradient(${color} 0 ${pct}%, var(--s-border) ${pct}% 100%)` }} />
        <div style={{ position: 'absolute', inset: 16, borderRadius: '50%', background: 'var(--s-surface)', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
          <span style={{ fontSize: 52, lineHeight: 1, fontWeight: 300, color }}>{score}</span>
          <span style={{ fontSize: 12, color: 'var(--s-text-3)' }}>de 100</span>
        </div>
        <span aria-hidden="true" style={{ position: 'absolute', inset: 0, transform: `rotate(${TARGET_SCORE * 3.6}deg)` }}>
          <span style={{ position: 'absolute', left: '50%', top: -3, width: 3, height: 18, marginLeft: -1.5, background: 'var(--s-text)' }} />
        </span>
      </div>
      <div style={{ textAlign: 'center', fontSize: 13, color: 'var(--s-text-2)' }}>
        {missing === 0 ? `Dentro da meta de ${TARGET_SCORE}` : `Meta ${TARGET_SCORE} · faltam ${missing} ${missing === 1 ? 'ponto' : 'pontos'}`}
        <DeltaVsPrevious run={run} />
      </div>
    </section>
  );
}

/** Achados divididos por gravidade, numa barra e numa legenda com os números. */
function SeverityCard({ run }: { run: AuditRunResponse }) {
  const total = SEV_ORDER.reduce((acc, sev) => acc + (run.findingsSummary?.[sev] ?? 0), 0);
  return (
    <section aria-label="Achados por gravidade" style={{ ...box, display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div>
        <div style={eyebrow}>Achados por gravidade</div>
        <div style={{ fontSize: 40, fontWeight: 300, lineHeight: 1.1 }}>{total}</div>
      </div>
      {total > 0 && (
        <div style={{ display: 'flex', height: 12, borderRadius: 6, overflow: 'hidden', background: 'var(--s-border)' }}>
          {SEV_ORDER.map((sev) => {
            const n = run.findingsSummary?.[sev] ?? 0;
            return n > 0 ? <span key={sev} style={{ display: 'block', width: `${(n / total) * 100}%`, background: sevBadge[sev].bg }} /> : null;
          })}
        </div>
      )}
      <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '6px 16px', fontSize: 13 }}>
        {SEV_ORDER.map((sev) => (
          <li key={sev}>
            <span aria-hidden="true" style={{ display: 'inline-block', width: 10, height: 10, borderRadius: 2, background: sevBadge[sev].bg, marginRight: 6 }} />
            <strong style={{ fontWeight: 600 }}>{run.findingsSummary?.[sev] ?? 0}</strong> {SEV_NAME[sev]}
          </li>
        ))}
      </ul>
    </section>
  );
}

function ChangeStat({ value, label, color }: { value: number; label: string; color?: string }) {
  return (
    <div>
      <div style={{ fontSize: 36, fontWeight: 300, lineHeight: 1, color }}>{value}</div>
      <div style={{ fontSize: 13, color: 'var(--s-text-3)', marginTop: 4 }}>{label}</div>
    </div>
  );
}

function persistingCount(run: AuditRunResponse): number | null {
  if (run.totalFindings == null) return null;
  return Math.max(0, run.totalFindings - (run.newFindings ?? 0));
}

function ChangesBody({ run }: { run: AuditRunResponse }) {
  const persisting = persistingCount(run);
  const novos = run.newFindings ?? 0;
  return (
    <>
      <div style={{ display: 'flex', gap: '12px 28px', flexWrap: 'wrap' }}>
        <ChangeStat value={novos} label="novos" color={novos ? 'var(--s-danger-text)' : 'var(--s-success-text)'} />
        <ChangeStat value={run.resolvedFindings ?? 0} label="corrigidos" color="var(--s-success-text)" />
        {persisting !== null && <ChangeStat value={persisting} label="continuam" />}
      </div>
      {novos > 0 && <Link to={`/findings?auditId=${run.id}&onlyNew=1`} style={{ fontSize: 13 }}>Ver só os novos</Link>}
    </>
  );
}

/** O que mudou desde a auditoria anterior da mesma branch. */
function ChangesCard({ run }: { run: AuditRunResponse }) {
  const hasBase = run.previousAuditId != null && run.newFindings != null && run.resolvedFindings != null;
  return (
    <section aria-label="O que mudou" style={{ ...box, display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div style={eyebrow}>O que mudou desde #{run.previousAuditId ?? 'a anterior'}</div>
      {hasBase ? (
        <ChangesBody run={run} />
      ) : (
        <p style={{ margin: 0, color: 'var(--s-text-3)', fontSize: 14 }}>Primeira auditoria concluída desta branch: ainda não há com o que comparar.</p>
      )}
    </section>
  );
}

const RADAR_ORDER: Category[] = ['SECURITY', 'DEPENDENCY', 'ARCHITECTURE', 'QUALITY', 'PERFORMANCE', 'OBSERVABILITY'];

/** Radar das categorias: o anel tracejado é a meta; quanto mais perto da borda, melhor a nota. */
function Radar({ cats }: { cats: ReturnType<typeof categoriesByScore> }) {
  const byCat = new Map(cats.map((c) => [c.category, c]));
  const axes = RADAR_ORDER.filter((c) => byCat.has(c));
  if (axes.length < 3) return null;
  const size = 260;
  const mid = size / 2;
  const radius = 84;
  const point = (i: number, value: number, extra = 0) => {
    const angle = -Math.PI / 2 + (i * 2 * Math.PI) / axes.length;
    const r = (radius * Math.max(0, Math.min(100, value))) / 100 + extra;
    return [mid + r * Math.cos(angle), mid + r * Math.sin(angle)] as const;
  };
  const ring = (value: number) => axes.map((_, i) => point(i, value).join(',')).join(' ');
  const shape = axes.map((c, i) => point(i, byCat.get(c)?.score ?? 0).join(',')).join(' ');
  return (
    <svg aria-hidden="true" viewBox={`-60 0 ${size + 120} ${size}`} style={{ width: '100%', maxWidth: 380, height: 'auto', alignSelf: 'center' }}>
      {[25, 50, 100].map((v) => <polygon key={v} points={ring(v)} fill="none" stroke="var(--s-border)" strokeWidth="1" />)}
      <polygon points={ring(TARGET_SCORE)} fill="none" stroke="var(--s-success-text)" strokeWidth="1.5" strokeDasharray="4 3" />
      <polygon points={shape} fill="var(--s-link)" fillOpacity="0.2" stroke="var(--s-link)" strokeWidth="2" />
      {axes.map((c, i) => {
        const [x, y] = point(i, 100, 16);
        return (
          <text key={c} x={x} y={y} fontSize="11" fill="var(--s-text-2)" textAnchor={x < mid - 4 ? 'end' : x > mid + 4 ? 'start' : 'middle'} dominantBaseline="middle">
            {categoryLabels[c]}
          </text>
        );
      })}
    </svg>
  );
}

/** Etapa 2: categorias da pior para a melhor; clicar filtra a lista da etapa 3. */
function CategorySection({ cats, category, onPick }: { cats: ReturnType<typeof categoriesByScore>; category: Category | null; onPick: (c: Category | null) => void }) {
  return (
    <section aria-labelledby="etapa-2" style={{ ...box, display: 'grid', gap: '0.5rem', alignContent: 'start' }}>
      <div>
        <div id="etapa-2" style={eyebrow}>Etapa 2 · Onde está o risco</div>
        <div style={{ fontWeight: 600 }}>Por categoria, da pior para a melhor</div>
      </div>
      <Radar cats={cats} />
      {cats.map((c) => {
        const active = category === c.category;
        return (
          <button
            key={c.category}
            type="button"
            aria-pressed={active}
            className="sentinel-linha-hover"
            onClick={() => onPick(active ? null : c.category)}
            style={{
              display: 'grid',
              gridTemplateColumns: '130px 1fr 40px',
              gap: '0.75rem',
              alignItems: 'center',
              padding: '0.5rem 0.625rem',
              borderRadius: 6,
              background: active ? 'var(--s-selected)' : 'transparent',
              border: `1px solid ${active ? 'var(--s-link)' : 'transparent'}`,
              cursor: 'pointer',
              font: 'inherit',
              textAlign: 'left',
              color: 'var(--s-text)',
            }}
          >
            <span style={{ fontWeight: 500 }}>{c.label}</span>
            <span style={{ height: 8, borderRadius: 4, background: 'var(--s-border)' }}>
              <span style={{ display: 'block', height: 8, borderRadius: 4, width: `${Math.max(0, Math.min(100, c.score))}%`, background: scoreColor(c.score) }} />
            </span>
            <span style={{ textAlign: 'right', fontWeight: 600, color: scoreColor(c.score) }}>{Math.round(c.score)}</span>
          </button>
        );
      })}
      <span style={{ fontSize: '0.75rem', color: 'var(--s-text-3)' }}>Clique numa categoria para filtrar a lista ao lado.</span>
    </section>
  );
}

const TOOL_STATUS: Record<string, { text: string; color: string; ok: boolean }> = {
  OK: { text: 'concluído', color: 'var(--s-success-text)', ok: true },
  PARCIAL: { text: 'parcial: resultado pode estar incompleto', color: 'var(--s-warn-text)', ok: true },
  FALHOU: { text: 'falhou', color: 'var(--s-danger-text)', ok: false },
  INDISPONIVEL: { text: 'ferramenta ausente', color: 'var(--s-text-3)', ok: false },
  NAO_SUPORTADO: { text: 'não se aplica', color: 'var(--s-text-3)', ok: false },
  DESLIGADO: { text: 'desligado neste sistema', color: 'var(--s-text-3)', ok: false },
  SUBSTITUIDO: { text: 'coberto por outro', color: 'var(--s-text-3)', ok: false },
};

function toolView(a: AnalyzerRunInfo) {
  const known = TOOL_STATUS[a.status] ?? { text: 'sem evidência', color: 'var(--s-text-3)', ok: false };
  const seconds = a.durationMs > 0 ? ` em ${Math.max(1, Math.round(a.durationMs / 1000))} s` : '';
  return { ...known, text: a.status === 'OK' ? `concluído${seconds}` : known.text };
}

/** Ferramentas de análise que rodaram nesta auditoria: situação, achados e tempo de cada uma. */
function ToolsSection({ runId }: { runId: number }) {
  const { data } = useInsights(runId);
  const tools = Array.isArray(data?.analyzers) ? data.analyzers : [];
  if (tools.length === 0) return null;
  const done = tools.filter((t) => toolView(t).ok).length;
  return (
    <section aria-label="Ferramentas de análise" style={box}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', flexWrap: 'wrap', gap: 8, marginBottom: 12 }}>
        <span style={{ fontSize: 16, fontWeight: 600 }}>Ferramentas de análise</span>
        <span style={{ fontSize: 13, color: 'var(--s-text-3)' }}>{done} de {tools.length} concluídas</span>
      </div>
      <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(min(220px, 100%), 1fr))', gap: 12 }}>
        {tools.map((t) => {
          const v = toolView(t);
          return (
            <li key={t.name} className="sentinel-linha-hover" title={t.note ?? undefined} style={{ borderLeft: `4px solid ${v.color}`, padding: '6px 12px', borderRadius: 4 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                <strong style={{ fontWeight: 600 }}>{t.name}</strong>
                <span style={{ fontSize: 13, color: 'var(--s-text-2)' }}>{t.findings} {t.findings === 1 ? 'achado' : 'achados'}</span>
              </div>
              <div style={{ fontSize: 12, color: v.color }}>{v.text}</div>
            </li>
          );
        })}
      </ul>
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
      className="sentinel-linha-hover"
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
        <span style={{ background: sevBadge[item.severity].bg, color: sevBadge[item.severity].fg, padding: '2px 8px', borderRadius: 4, fontSize: 12, fontWeight: 600 }}>
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

  const items = plan.items.filter((i) => category === null || i.category === category).slice(0, 5);
  return (
    <div style={{ display: 'grid', gap: '1.5rem' }}>
      <VerdictSection plan={plan} />
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(300px, 100%), 1fr))', gap: '1.5rem' }}>
        <ScoreCard run={run} plan={plan} />
        <SeverityCard run={run} />
        <ChangesCard run={run} />
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(440px, 100%), 1fr))', gap: '1.5rem', alignItems: 'start' }}>
        <CategorySection cats={categoriesByScore(run.riskScore)} category={category} onPick={setCategory} />
        <FixSection run={run} plan={plan} category={category} items={items} onOpenFindings={onOpenFindings} />
      </div>
      <ToolsSection runId={run.id} />
      <ScoreExplanationPanel run={run} />
    </div>
  );
}
