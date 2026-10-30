import { SkeletonText } from '@carbon/react';
import { useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import { Link } from 'react-router-dom';
import { useAuditRuns } from '../hooks/useAuditRuns';
import { buildHistory, historySummary, type HistoryPoint } from '../utils/auditHistory';
import { TARGET_SCORE, WARN_SCORE, type ScoreBand } from '../utils/scoreBand';

const box = { background: 'var(--s-surface)', border: '1px solid var(--s-border)', borderRadius: 8, padding: '1.25rem 1.5rem' } as const;
const TICKS = [0, 25, 50, 75, 100];
const BAND_TEXT: Record<ScoreBand, string> = { good: 'Boa', warn: 'Atenção', bad: 'Ruim', none: 'Sem nota' };
const KIND_TEXT = { failed: 'Falhou', cancelled: 'Cancelada', active: 'Em andamento' } as const;

interface Tip {
  point: HistoryPoint;
  /** Centro da coluna e topo/base dela na janela, para posicionar a dica (que fica no body, fora do cartão). */
  x: number;
  top: number;
  bottom: number;
}

const pointsText = (n: number) => `${Math.abs(n)} ${Math.abs(n) === 1 ? 'ponto' : 'pontos'}`;
const signed = (n: number) => (n > 0 ? `+${n}` : `${n}`);

/** Frase lida por leitores de tela: o destino, a nota e a distância da meta. */
function columnLabel(p: HistoryPoint, currentId: number): string {
  const here = p.id === currentId ? ' Esta é a auditoria aberta.' : '';
  if (p.score === null) return `Abrir a auditoria ${p.id}, ${p.dayLabel}. ${p.kind === 'scored' ? 'Sem nota' : KIND_TEXT[p.kind]}.${here}`;
  const gap = p.score - TARGET_SCORE;
  const where = gap >= 0 ? `${pointsText(gap)} acima da meta` : `${pointsText(gap)} abaixo da meta`;
  return `Abrir a auditoria ${p.id}, ${p.dayLabel}. Nota ${p.score}, ${BAND_TEXT[p.band]}, ${where}.${here}`;
}

function ColumnBody({ point }: { point: HistoryPoint }) {
  if (point.score !== null) {
    return (
      <span className={`sentinel-grafico__barra sentinel-grafico__barra--${point.band}`} style={{ height: `${Math.max(0, Math.min(100, point.score))}%` }}>
        <span className={`sentinel-grafico__valor sentinel-grafico__valor--${point.band}`}>{point.score}</span>
      </span>
    );
  }
  const text = point.kind === 'scored' ? 'Sem nota' : KIND_TEXT[point.kind];
  return (
    <span className={`sentinel-grafico__sem-nota sentinel-grafico__sem-nota--${point.kind}`}>
      <span className="sentinel-grafico__sem-nota-texto">{text}</span>
    </span>
  );
}

function TipBody({ tip, currentId }: { tip: Tip; currentId: number }) {
  const { point } = tip;
  const gap = point.score === null ? null : point.score - TARGET_SCORE;
  return (
    <>
      <h3>Auditoria #{point.id}</h3>
      <div className="sentinel-grafico__dica-sub">{point.dayLabel}{point.timeLabel && ` ${point.timeLabel}`}</div>
      {point.score === null ? (
        <div>{point.kind === 'scored' ? 'A auditoria não trouxe nota.' : point.kind === 'active' ? 'Em andamento: a nota aparece quando terminar.' : `${KIND_TEXT[point.kind as 'failed' | 'cancelled']}: não gerou nota.`}</div>
      ) : (
        <dl>
          <dt>Nota</dt><dd>{point.score} · {BAND_TEXT[point.band]}</dd>
          <dt>Meta ({TARGET_SCORE})</dt><dd className={gap! >= 0 ? 'sentinel-grafico__sobe' : 'sentinel-grafico__desce'}>{signed(gap!)}</dd>
          <dt>Anterior</dt>
          <dd className={point.delta ? (point.delta > 0 ? 'sentinel-grafico__sobe' : 'sentinel-grafico__desce') : undefined}>
            {point.delta === null ? 'primeira' : point.delta === 0 ? 'igual' : signed(point.delta)}
          </dd>
          <dt>Críticos / altos</dt><dd>{point.critical} / {point.high}</dd>
        </dl>
      )}
      <div className="sentinel-grafico__dica-ir">{point.id === currentId ? 'Você está nesta auditoria' : 'Clique para abrir o resultado'}</div>
    </>
  );
}

/**
 * Nota das últimas auditorias do sistema, da mais antiga para a mais recente, contra a meta e o limite de atenção.
 * Clicar numa coluna (ou no valor) abre o resultado daquela auditoria.
 */
export function AuditHistoryChart({ systemId, systemName, currentId }: { systemId: number; systemName: string; currentId: number }) {
  const { data, isLoading } = useAuditRuns(systemId);
  const points = useMemo(() => buildHistory(data ?? []), [data]);
  const [tip, setTip] = useState<Tip | null>(null);
  const { onTarget, scored } = historySummary(points);

  const show = (point: HistoryPoint, el: HTMLElement) => {
    const r = el.getBoundingClientRect();
    setTip({ point, x: r.left + r.width / 2, top: r.top, bottom: r.bottom });
  };
  const hide = () => setTip(null);
  const goTop = () => { hide(); window.scrollTo({ top: 0 }); };

  return (
    <section aria-labelledby="grafico-auditorias-titulo" className="sentinel-grafico" style={box}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', flexWrap: 'wrap', gap: 8, marginBottom: 4 }}>
        <span id="grafico-auditorias-titulo" style={{ fontSize: 16, fontWeight: 600 }}>Últimas auditorias</span>
        {scored > 0 && <span style={{ fontSize: 13, color: 'var(--s-text-3)' }}>{systemName} · {onTarget} de {scored} na meta</span>}
      </div>
      {isLoading ? (
        <SkeletonText lineCount={4} />
      ) : points.length < 2 ? (
        <p style={{ margin: 0, color: 'var(--s-text-3)', fontSize: 14 }}>Esta é a primeira auditoria do sistema: rode outra para ver a evolução da nota.</p>
      ) : (
        <>
          <p style={{ margin: '0 0 16px', color: 'var(--s-text-3)', fontSize: 14 }}>Clique numa coluna para abrir aquela auditoria.</p>
          <div className="sentinel-grafico__area" role="group" aria-label={`Gráfico de colunas com a nota das últimas auditorias de ${systemName}`}>
            <div className="sentinel-grafico__eixo-y" aria-hidden="true">
              {TICKS.map((v) => <span key={v} style={{ bottom: `${v}%` }}>{v}</span>)}
            </div>
            <div className="sentinel-grafico__plano">
              <div aria-hidden="true">
                {[25, 100].map((v) => <div key={v} className="sentinel-grafico__grade" style={{ bottom: `${v}%` }} />)}
              </div>
              <div className="sentinel-grafico__colunas">
                {points.map((p) => (
                  <Link
                    key={p.id}
                    to={`/audits/${p.id}`}
                    className="sentinel-grafico__coluna"
                    aria-current={p.id === currentId ? 'page' : undefined}
                    aria-label={columnLabel(p, currentId)}
                    onClick={goTop}
                    onMouseEnter={(e) => show(p, e.currentTarget)}
                    onMouseLeave={hide}
                    onFocus={(e) => show(p, e.currentTarget)}
                    onBlur={hide}
                  >
                    <ColumnBody point={p} />
                  </Link>
                ))}
              </div>
              <div className="sentinel-grafico__ref" aria-hidden="true" style={{ bottom: `${TARGET_SCORE}%` }}><i /></div>
              <div className="sentinel-grafico__ref sentinel-grafico__ref--atencao" aria-hidden="true" style={{ bottom: `${WARN_SCORE}%` }}><i /></div>
            </div>
            <div className="sentinel-grafico__rotulos-ref" aria-hidden="true">
              <span style={{ top: `${100 - TARGET_SCORE}%` }}>Meta {TARGET_SCORE}</span>
              <span style={{ top: `${100 - WARN_SCORE}%` }}>Atenção {WARN_SCORE}</span>
            </div>
          </div>
          <div className="sentinel-grafico__eixo-x" aria-hidden="true">
            <span />
            <div className="sentinel-grafico__rotulos">
              {points.map((p) => (
                <Link key={p.id} to={`/audits/${p.id}`} tabIndex={-1} className="sentinel-grafico__rotulo" onClick={goTop}>
                  <b>#{p.id}</b>
                  <span className="sentinel-grafico__rotulo-dia">{p.dayLabel}</span>
                  {p.id === currentId && <span className="sentinel-grafico__aqui">esta</span>}
                </Link>
              ))}
            </div>
            <span />
          </div>
          <div className="sentinel-grafico__legenda">
            <span><i className="sentinel-grafico__amostra sentinel-grafico__barra--good" />Boa: {TARGET_SCORE} ou mais</span>
            <span><i className="sentinel-grafico__amostra sentinel-grafico__barra--warn" />Atenção: {WARN_SCORE} a {TARGET_SCORE - 1}</span>
            <span><i className="sentinel-grafico__amostra sentinel-grafico__barra--bad" />Ruim: abaixo de {WARN_SCORE}</span>
            {points.some((p) => p.kind !== 'scored') && <span><i className="sentinel-grafico__amostra sentinel-grafico__amostra--sem-nota" />Sem nota</span>}
          </div>
        </>
      )}
      {tip && createPortal(
        <div
          role="status"
          className="sentinel-grafico__dica"
          style={{ left: Math.max(8, Math.min(tip.x - 110, window.innerWidth - 228)), top: tip.top > 220 ? tip.top - 12 : tip.bottom + 12, transform: tip.top > 220 ? 'translateY(-100%)' : undefined }}
        >
          <TipBody tip={tip} currentId={currentId} />
        </div>,
        document.body,
      )}
    </section>
  );
}
