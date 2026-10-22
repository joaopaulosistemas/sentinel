import type { AnalyzerProgress, AuditProgress, AuditRunResponse, Severity } from '../types';
import { PHASES, clock, secondsBetween, type PhaseView } from '../utils/auditProgress';
import { CheckIcon, ClockIcon, CrossIcon, DashIcon, SpinnerIcon } from './StatusIcons';

const mono = "'IBM Plex Mono', ui-monospace, monospace";

/** Cinco fases em linha: concluída (verde com check), ativa (azul com número) ou pendente (contorno). */
export function PhaseStrip({ phases }: { phases: PhaseView[] }) {
  const style = {
    done: { bg: '#198038', fg: '#ffffff', border: '#198038', title: '#161616' },
    active: { bg: '#0f62fe', fg: '#ffffff', border: '#0f62fe', title: '#0f62fe' },
    failed: { bg: '#da1e28', fg: '#ffffff', border: '#da1e28', title: '#da1e28' },
    pending: { bg: '#ffffff', fg: '#525252', border: '#8d8d8d', title: '#161616' },
  } as const;
  return (
    <ol aria-label="Fases da auditoria" style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexWrap: 'wrap', gap: '8px 0' }}>
      {phases.map((p) => {
        const c = style[p.state];
        return (
          <li key={p.id} aria-current={p.state === 'active' ? 'step' : undefined}
            style={{ flex: '1 1 180px', minWidth: 0, display: 'flex', alignItems: 'flex-start', gap: 10, paddingRight: 12 }}>
            <span style={{ flex: 'none', width: 28, height: 28, borderRadius: 14, display: 'inline-flex', alignItems: 'center',
              justifyContent: 'center', fontSize: 12, fontWeight: 600, background: c.bg, color: c.fg, border: `2px solid ${c.border}` }}>
              {p.state === 'done' ? <CheckIcon size={14} color="#ffffff" strokeWidth={3.5} /> : p.state === 'failed' ? <CrossIcon size={14} color="#ffffff" /> : p.n}
            </span>
            <span style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 }}>
              <span style={{ fontWeight: 500, color: c.title }}>{p.name}</span>
              <span style={{ fontSize: 12, color: '#525252', overflowWrap: 'anywhere' }}>{p.sub}</span>
            </span>
          </li>
        );
      })}
    </ol>
  );
}

const state = (a: AnalyzerProgress) => {
  switch (a.status) {
    case 'OK':
      return { icon: <CheckIcon label="Concluído" />, accent: '#198038', bar: '#198038', width: 100,
        text: a.durationMs > 0 ? `concluído em ${Math.max(1, Math.round(a.durationMs / 1000))} s` : 'concluído' };
    case 'EXECUTANDO':
      return { icon: <SpinnerIcon label="Executando" />, accent: '#0f62fe', bar: '#0f62fe', width: 45, text: 'executando', pulse: true };
    case 'FALHOU':
      return { icon: <CrossIcon label="Falhou" />, accent: '#da1e28', bar: '#da1e28', width: 100, text: 'falhou' };
    case 'ESPERANDO':
      return { icon: <ClockIcon label="Na fila" />, accent: '#c6c6c6', bar: '#0f62fe', width: 0, text: 'na fila' };
    case 'INDISPONIVEL':
      return { icon: <DashIcon label="Ferramenta ausente" />, accent: '#c6c6c6', bar: '#8d8d8d', width: 0, text: 'ferramenta ausente' };
    case 'NAO_SUPORTADO':
      return { icon: <DashIcon label="Não se aplica" />, accent: '#c6c6c6', bar: '#8d8d8d', width: 0, text: 'não se aplica' };
    case 'SUBSTITUIDO':
      return { icon: <DashIcon label="Coberto por outro" />, accent: '#c6c6c6', bar: '#8d8d8d', width: 0, text: 'coberto por outro' };
    default:
      return { icon: <DashIcon label="Sem evidência" />, accent: '#c6c6c6', bar: '#8d8d8d', width: 0, text: 'sem evidência' };
  }
};

/** Cartão de um analisador: nome, o que ele verifica, barra, achados e situação. */
export function AnalyzerCard({ a }: { a: AnalyzerProgress }) {
  const s = state(a);
  return (
    <li style={{ background: '#fff', border: '1px solid #e0e0e0', borderTop: `3px solid ${s.accent}`, padding: '12px 14px',
      display: 'flex', flexDirection: 'column', gap: 8, minWidth: 0 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, justifyContent: 'space-between' }}>
        <span style={{ fontWeight: 500, fontSize: 13, minWidth: 0 }}>{a.name}</span>
        <span style={{ flex: 'none', display: 'inline-flex', alignItems: 'center' }}>{s.icon}</span>
      </div>
      <div style={{ fontSize: 12, color: '#525252' }}>{a.note ?? a.description ?? ' '}</div>
      <div style={{ height: 4, background: '#e0e0e0' }} aria-hidden="true">
        <div className={s.pulse ? 'sentinel-pulse' : undefined} style={{ height: 4, background: s.bar, width: `${s.width}%` }} />
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, fontSize: 12, color: '#525252' }}>
        <span><strong style={{ color: '#161616', fontWeight: 600, fontFamily: mono }}>{a.findings}</strong> {a.findings === 1 ? 'achado' : 'achados'}</span>
        <span>{s.text}</span>
      </div>
    </li>
  );
}

const SEVS: Array<{ id: Severity; label: string; bg: string; fg: string }> = [
  { id: 'CRITICAL', label: 'Crítico', bg: '#ffd7d9', fg: '#750e13' },
  { id: 'HIGH', label: 'Alto', bg: '#ffe0cc', fg: '#6b2d00' },
  { id: 'MEDIUM', label: 'Médio', bg: '#fcf4d6', fg: '#5a4200' },
  { id: 'LOW', label: 'Baixo', bg: '#e5f6ff', fg: '#00417a' },
];

const num = new Intl.NumberFormat('pt-BR');

/** Contagem por severidade, arquivos e linhas; durante a execução os números crescem. */
export function PartialResult({ run, progress }: { run: AuditRunResponse; progress: AuditProgress | null }) {
  const finished = run.status === 'COMPLETED';
  const counts = (id: Severity) => (finished ? run.findingsSummary?.[id] : progress?.severities?.[id]) ?? 0;
  const lines = finished ? run.linesOfCode ?? progress?.linesOfCode ?? 0 : progress?.linesOfCode ?? 0;
  const files = progress?.filesRead ?? 0;
  const delta = run.scoreDelta ?? null;
  return (
    <section aria-label={finished ? 'Resultado final' : 'Resultado parcial'}
      style={{ background: '#fff', border: '1px solid #e0e0e0', padding: '16px 20px', display: 'flex', flexDirection: 'column', gap: 14 }}>
      <h2 style={{ margin: 0, fontSize: 16, fontWeight: 600 }}>{finished ? 'Resultado final' : 'Resultado parcial'}</h2>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(0, 1fr))', gap: 8 }}>
        {SEVS.map((s) => (
          <div key={s.id} style={{ background: s.bg, color: s.fg, padding: '10px 8px', display: 'flex', flexDirection: 'column', gap: 2 }}>
            <span style={{ fontSize: 22, fontWeight: 300, fontFamily: mono }}>{counts(s.id)}</span>
            <span style={{ fontSize: 12, fontWeight: 500 }}>{s.label}</span>
          </div>
        ))}
      </div>
      <div style={{ display: 'flex', gap: 24, fontSize: 13, color: '#525252', flexWrap: 'wrap' }}>
        {files > 0 && <span><strong style={{ color: '#161616', fontWeight: 600 }}>{num.format(files)}</strong> arquivos lidos</span>}
        {lines > 0 && <span><strong style={{ color: '#161616', fontWeight: 600 }}>{num.format(lines)}</strong> linhas</span>}
      </div>
      {finished && run.riskScore ? (
        <div style={{ borderTop: '1px solid #e0e0e0', paddingTop: 14, display: 'flex', alignItems: 'baseline', gap: 12, flexWrap: 'wrap' }}>
          <span style={{ fontSize: 40, fontWeight: 300, lineHeight: 1, color: run.riskScore.overall >= 75 ? '#198038' : run.riskScore.overall >= 50 ? '#b28600' : '#a2191f' }}>
            {Math.round(run.riskScore.overall)}
          </span>
          <span style={{ color: '#525252' }}>
            nota de risco
            {delta !== null && run.previousAuditId != null && (
              <> (era {Math.round(run.riskScore.overall - delta)},{' '}
                <strong style={{ color: delta >= 0 ? '#198038' : '#a2191f', fontWeight: 600 }}>{delta >= 0 ? '+' : ''}{Math.round(delta)}</strong>)</>
            )}
            {run.newFindings != null && run.resolvedFindings != null && ` · ${run.newFindings} novos, ${run.resolvedFindings} resolvidos`}
          </span>
        </div>
      ) : !finished ? (
        <div style={{ borderTop: '1px solid #e0e0e0', paddingTop: 12, fontSize: 12, color: '#525252' }}>
          A nota final só aparece depois da pontuação. Os números acima crescem enquanto os analisadores terminam.
        </div>
      ) : null}
    </section>
  );
}

/** Eventos mais recentes primeiro, com o tempo decorrido desde o início. */
export function EventLog({ startedAt, events }: { startedAt: string; events: AuditProgress['events'] }) {
  const rows = [...events].reverse().slice(0, 8);
  return (
    <section style={{ background: '#fff', border: '1px solid #e0e0e0', padding: '16px 20px', display: 'flex', flexDirection: 'column', gap: 10 }}>
      <h2 style={{ margin: 0, fontSize: 16, fontWeight: 600 }}>Eventos</h2>
      {rows.length === 0 ? (
        <p style={{ margin: 0, color: '#525252', fontSize: 13 }}>Aguardando o início.</p>
      ) : (
        <ul aria-live="polite" style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: 8 }}>
          {rows.map((e, i) => (
            <li key={`${e.at}-${i}`} style={{ display: 'flex', gap: 10, fontSize: 13, lineHeight: 1.35 }}>
              <span style={{ flex: 'none', fontFamily: mono, fontSize: 12, color: '#6f6f6f', width: 42 }}>{clock(secondsBetween(startedAt, e.at))}</span>
              <span style={{ color: /falhou/i.test(e.message) ? '#a2191f' : '#161616' }}>{e.message}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

export { PHASES };
