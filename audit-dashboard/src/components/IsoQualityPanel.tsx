import { InlineNotification, SkeletonText } from '@carbon/react';
import { useState } from 'react';
import { useIsoQuality, useIsoTrend } from '../hooks/useAuditRuns';
import type { IAuditRunResponse, IIsoCharacteristic, IsoProfile } from '../types';
import { isoDelta, isoDeltaLabel, isoProfileLabels, orderedCharacteristics } from '../utils/isoQuality';
import { scoreColor } from '../utils/scoreBand';

const box = { background: 'var(--s-surface)', border: '1px solid var(--s-border)', borderRadius: 8, padding: '1.25rem 1.5rem' } as const;
const cell = { padding: '6px 8px', textAlign: 'left', verticalAlign: 'top', fontSize: 13 } as const;
const num = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 });

function Row({ c, delta }: { c: IIsoCharacteristic; delta: number | null }) {
  return (
    <tr style={{ borderTop: '1px solid var(--s-border)' }}>
      <td style={cell}>{c.nome}</td>
      {c.avaliada && c.nota !== null ? (
        <>
          <td style={{ ...cell, fontWeight: 600, color: scoreColor(c.nota) }}>{num.format(c.nota)}</td>
          <td style={cell}>{c.achados}</td>
          <td style={{ ...cell, color: 'var(--s-text-3)' }}>{isoDeltaLabel(delta)}</td>
        </>
      ) : (
        <td colSpan={3} style={{ ...cell, color: 'var(--s-text-3)' }}>não avaliada: o Sentinel não tem regras para esta característica</td>
      )}
    </tr>
  );
}

/** Qualidade por característica da ISO/IEC 25010, com as não avaliadas à vista e a variação desde a auditoria anterior. */
export function IsoQualityPanel({ run }: { run: IAuditRunResponse }) {
  const [profile, setProfile] = useState<IsoProfile>('padrao');
  const done = run.status === 'COMPLETED';
  const { data, isLoading, error } = useIsoQuality({ auditId: run.id, perfil: profile, enabled: done });
  const trend = useIsoTrend({ systemId: run.systemId, enabled: done });
  if (!done) return null;

  return (
    <section aria-label="Qualidade ISO/IEC 25010" style={box}>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'center', justifyContent: 'space-between' }}>
        <h3 style={{ margin: 0, fontSize: 16, fontWeight: 600 }}>Qualidade ISO/IEC 25010</h3>
        <label style={{ fontSize: 13, display: 'flex', gap: 8, alignItems: 'center' }}>
          Perfil
          <select value={profile} onChange={(e) => setProfile(e.target.value as IsoProfile)} aria-label="Perfil de pesos">
            {(Object.keys(isoProfileLabels) as IsoProfile[]).map((p) => <option key={p} value={p}>{isoProfileLabels[p]}</option>)}
          </select>
        </label>
      </div>
      {isLoading && <SkeletonText paragraph lineCount={4} />}
      {error && <InlineNotification kind="error" title="Não foi possível calcular a qualidade ISO" subtitle={error instanceof Error ? error.message : 'Erro desconhecido'} hideCloseButton />}
      {data && Array.isArray(data.caracteristicas) && (
        <>
          <p style={{ margin: '0.75rem 0', fontSize: 14 }}>
            Nota ponderada <strong style={{ color: scoreColor(data.notaPonderada) }}>{num.format(data.notaPonderada)}</strong>
            <span style={{ color: 'var(--s-text-3)' }}> · alinhamento de vocabulário, não certificação</span>
          </p>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr>{['Característica', 'Nota', 'Achados', 'Desde a anterior'].map((h) => <th key={h} style={{ ...cell, color: 'var(--s-text-3)', fontWeight: 400 }}>{h}</th>)}</tr>
            </thead>
            <tbody>
              {orderedCharacteristics(data.caracteristicas, data.prioridade).map((c) => <Row key={c.id} c={c} delta={isoDelta(trend.data, c.id)} />)}
            </tbody>
          </table>
        </>
      )}
    </section>
  );
}
