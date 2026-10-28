import { SkeletonText } from '@carbon/react';
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useTrend } from '../hooks/useLifecycle';
import { PageSection } from './PageSection';

/** Evolução do score geral e da segurança ao longo das auditorias concluídas. */
export function TrendChart({ systemId }: { systemId: number }) {
  const { data, isLoading } = useTrend(systemId);

  const points = (data ?? []).map((p) => ({
    label: p.completedAt ? new Date(p.completedAt).toLocaleDateString('pt-BR') : `#${p.auditId}`,
    audit: p.auditId,
    Geral: p.overall,
    Segurança: p.security,
    Dependências: p.dependencies,
  }));

  return (
    <PageSection id="tendencia" title="Evolução da nota" hint="Nota geral, segurança e dependências nas auditorias concluídas.">
      {isLoading ? (
        <SkeletonText lineCount={4} />
      ) : points.length < 2 ? (
        <p style={{ margin: 0, color: 'var(--s-text-3)' }}>
          {points.length === 0
            ? 'Nenhuma auditoria concluída ainda.'
            : 'Rode mais uma auditoria para ver a evolução ao longo do tempo.'}
        </p>
      ) : (
        <div style={{ width: '100%', height: 240 }}>
          <ResponsiveContainer>
            <LineChart data={points} margin={{ top: 8, right: 16, bottom: 0, left: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--s-border)" />
              <XAxis dataKey="label" tick={{ fontSize: 12 }} />
              <YAxis domain={[0, 100]} tick={{ fontSize: 12 }} width={32} />
              <Tooltip labelFormatter={(_l, payload) => `Auditoria #${payload?.[0]?.payload?.audit ?? ''}`} />
              <Legend />
              <Line type="monotone" dataKey="Geral" stroke="#0f62fe" strokeWidth={2} dot />
              <Line type="monotone" dataKey="Segurança" stroke="#da1e28" strokeWidth={1.5} dot={false} />
              <Line type="monotone" dataKey="Dependências" stroke="#8a3ffc" strokeWidth={1.5} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      )}
    </PageSection>
  );
}
