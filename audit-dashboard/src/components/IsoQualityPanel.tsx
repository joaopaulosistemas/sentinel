import { InlineNotification, SkeletonText } from '@carbon/react';
import { useState } from 'react';
import { useIsoQuality, useIsoTrend } from '../hooks/useAuditRuns';
import type { IAuditRunResponse, IIsoCharacteristic, IsoProfile } from '../types';
import { isoDelta, isoDeltaLabel, isoDeltaSr, isoProfileHints, isoProfileLabels, isoSituacao, orderedCharacteristics } from '../utils/isoQuality';
import { scoreBand, scoreColor } from '../utils/scoreBand';

const box = { background: 'var(--s-surface)', border: '1px solid var(--s-border)', borderRadius: 8, padding: '1.25rem 1.5rem' } as const;
const cell = { padding: '8px', textAlign: 'left', verticalAlign: 'top', fontSize: 13 } as const;
const right = { ...cell, textAlign: 'right' } as const;
const num = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 });

function Row({ c, delta, first }: { c: IIsoCharacteristic; delta: number | null; first: boolean }) {
  const band = scoreBand(c.nota);
  return (
    <tr style={{ borderTop: '1px solid var(--s-border)' }}>
      <th scope="row" style={{ ...cell, fontWeight: first ? 600 : 400 }}>{c.nome}</th>
      <td style={{ ...right, fontWeight: 600, color: scoreColor(c.nota) }}>{c.nota === null ? '—' : num.format(c.nota)}</td>
      <td style={{ ...cell, color: scoreColor(c.nota) }}>{isoSituacao(band)}</td>
      <td style={right}>{c.achados}</td>
      <td style={{ ...right, color: 'var(--s-text-3)' }}>
        <span aria-hidden="true">{isoDeltaLabel(delta)}</span>
        <span className="sentinel-sr-only">{isoDeltaSr(delta)}</span>
      </td>
    </tr>
  );
}

/** Qualidade por característica da ISO/IEC 25010: a que mais merece atenção em destaque e as não avaliadas recolhidas. */
export function IsoQualityPanel({ run }: { run: IAuditRunResponse }) {
  const [profile, setProfile] = useState<IsoProfile>('padrao');
  const done = run.status === 'COMPLETED';
  const { data, isLoading, error } = useIsoQuality({ auditId: run.id, perfil: profile, enabled: done });
  const trend = useIsoTrend({ systemId: run.systemId, enabled: done });
  if (!done) return null;

  const ok = data && Array.isArray(data.caracteristicas);
  const ordered = ok ? orderedCharacteristics(data.caracteristicas, data.prioridade) : [];
  const rated = ordered.filter((c) => c.avaliada && c.nota !== null);
  const unrated = ordered.filter((c) => !c.avaliada || c.nota === null);

  return (
    <section aria-label="Qualidade ISO/IEC 25010" style={box}>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'flex-start', justifyContent: 'space-between' }}>
        <div>
          <h3 style={{ margin: 0, fontSize: 16, fontWeight: 600 }}>Qualidade ISO/IEC 25010</h3>
          <p style={{ margin: '4px 0 0', fontSize: 13, color: 'var(--s-text-3)' }}>Alinhamento de vocabulário, não certificação.</p>
        </div>
        <label style={{ fontSize: 13, display: 'grid', gap: 4 }}>
          Perfil de pesos
          <select value={profile} onChange={(e) => setProfile(e.target.value as IsoProfile)} style={{ height: 40, padding: '0 8px' }}>
            {(Object.keys(isoProfileLabels) as IsoProfile[]).map((p) => <option key={p} value={p}>{isoProfileLabels[p]}</option>)}
          </select>
        </label>
      </div>
      <p style={{ margin: '8px 0 0', fontSize: 13, color: 'var(--s-text-3)' }}>{isoProfileHints[profile]}</p>
      {isLoading && <SkeletonText paragraph lineCount={4} />}
      {error && <InlineNotification kind="error" title="Não foi possível calcular a qualidade ISO" subtitle={error instanceof Error ? error.message : 'Erro desconhecido'} hideCloseButton />}
      {ok && (
        <div aria-live="polite">
          <p style={{ margin: '0.75rem 0', fontSize: 14, display: 'flex', flexWrap: 'wrap', gap: '4px 24px', alignItems: 'baseline' }}>
            <span>Nota ponderada <strong style={{ fontSize: 20, color: scoreColor(data.notaPonderada) }}>{num.format(data.notaPonderada)}</strong></span>
            {rated[0] && (
              <span style={{ background: 'var(--s-selected)', borderRadius: 4, padding: '4px 10px', fontSize: 13 }}>
                <strong>Comece por aqui:</strong> {rated[0].nome}
              </span>
            )}
          </p>
          <table aria-label="Notas por característica de qualidade" style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr>
                {[['Característica', cell], ['Nota', right], ['Situação', cell], ['Achados', right], ['Desde a anterior', right]].map(([h, st]) => (
                  <th key={String(h)} scope="col" style={{ ...(st as typeof cell), color: 'var(--s-text-3)', fontWeight: 400 }}>{String(h)}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rated.map((c, i) => <Row key={c.id} c={c} delta={isoDelta(trend.data, c.id)} first={i === 0} />)}
            </tbody>
          </table>
          {unrated.length > 0 && (
            <details style={{ marginTop: 8, fontSize: 13, color: 'var(--s-text-3)' }}>
              <summary style={{ cursor: 'pointer', padding: '12px 0' }}>
                {unrated.length === 1 ? '1 característica ainda não avaliada' : `${unrated.length} características ainda não avaliadas`} pelo Sentinel
              </summary>
              <ul style={{ margin: '0 0 4px', paddingLeft: 20, lineHeight: 1.7 }}>
                {unrated.map((c) => <li key={c.id}>{c.nome}</li>)}
              </ul>
              <p style={{ margin: 0 }}>Sem regras para elas, o Sentinel não inventa nota.</p>
            </details>
          )}
        </div>
      )}
    </section>
  );
}
