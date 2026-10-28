import { Button, SkeletonText, Tag } from '@carbon/react';
import { useNavigate } from 'react-router-dom';
import { useDiff } from '../hooks/useLifecycle';
import { PageSection } from './PageSection';
import { RiskBadge } from './RiskBadge';

function Stat({ value, label, color }: { value: number; label: string; color?: string }) {
  return (
    <div>
      <div style={{ fontSize: 40, fontWeight: 300, lineHeight: 1, color }}>{value}</div>
      <div style={{ fontSize: 13, color: 'var(--s-text-3)', marginTop: 4 }}>{label}</div>
    </div>
  );
}

function scoreTagType(delta: number): 'green' | 'red' | 'warm-gray' {
  if (delta > 0) return 'green';
  return delta < 0 ? 'red' : 'warm-gray';
}

/** O que mudou desde a auditoria anterior: novos, corrigidos e variação do score. */
export function DiffSummary({ auditId }: { auditId: number }) {
  const { data, isLoading } = useDiff(auditId);
  const navigate = useNavigate();

  return (
    <PageSection id="mudou" title="O que mudou desde a auditoria anterior">
      {isLoading || !data ? (
        <SkeletonText lineCount={3} />
      ) : !data.hasBaseline ? (
        <p style={{ margin: 0, color: 'var(--s-text-3)' }}>Esta é a primeira auditoria concluída desta branch: ainda não há com o que comparar.</p>
      ) : (
        <>
          <div style={{ display: 'flex', gap: '24px 56px', alignItems: 'flex-end', marginBottom: 20, flexWrap: 'wrap' }}>
            <Stat value={data.newFindings.length} label="novos" color={data.newFindings.length ? 'var(--s-danger-text)' : 'var(--s-success-text)'} />
            <Stat value={data.resolvedFindings.length} label="corrigidos" color="var(--s-success-text)" />
            <Stat value={data.persistingCount} label="continuam" />
            {data.scoreDelta !== null && (
              <Tag type={scoreTagType(data.scoreDelta)}>
                Score {data.scoreDelta > 0 ? '+' : ''}
                {data.scoreDelta} (auditoria #{data.baseAuditId})
              </Tag>
            )}
            {data.newFindings.length > 0 && (
              <Button kind="tertiary" size="sm" onClick={() => navigate(`/findings?auditId=${auditId}&onlyNew=1`)}>
                Ver só os novos
              </Button>
            )}
          </div>
          {data.newFindings.length > 0 && (
            <ul style={{ margin: 0, padding: 0, listStyle: 'none' }}>
              {data.newFindings.slice(0, 5).map((f) => (
                <li key={f.id} className="sentinel-linha-hover" style={{ display: 'flex', gap: 12, alignItems: 'center', padding: '8px 0', borderTop: '1px solid var(--s-border)' }}>
                  <RiskBadge severity={f.severity} />
                  <span style={{ fontSize: 14 }}>{f.title}</span>
                </li>
              ))}
              {data.newFindings.length > 5 && (
                <li style={{ padding: '8px 0', borderTop: '1px solid var(--s-border)', fontSize: 13, color: 'var(--s-text-3)' }}>…e mais {data.newFindings.length - 5}</li>
              )}
            </ul>
          )}
        </>
      )}
    </PageSection>
  );
}
