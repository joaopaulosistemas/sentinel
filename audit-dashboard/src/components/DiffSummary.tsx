import { Button, SkeletonText, Tag, Tile } from '@carbon/react';
import { useNavigate } from 'react-router-dom';
import { useDiff } from '../hooks/useLifecycle';
import { RiskBadge } from './RiskBadge';

/** O que mudou desde a auditoria anterior: novos, corrigidos e variação do score. */
export function DiffSummary({ auditId }: { auditId: number }) {
  const { data, isLoading } = useDiff(auditId);
  const navigate = useNavigate();

  return (
    <Tile style={{ marginBottom: '2rem', padding: '1.25rem' }}>
      <h3 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '0.75rem' }}>
        O que mudou desde a auditoria anterior
      </h3>
      {isLoading || !data ? (
        <SkeletonText lineCount={3} />
      ) : !data.hasBaseline ? (
        <p style={{ color: 'var(--s-text-4)' }}>Esta é a primeira auditoria concluída desta branch: ainda não há com o que comparar.</p>
      ) : (
        <>
          <div style={{ display: 'flex', gap: '2rem', alignItems: 'center', marginBottom: '1rem', flexWrap: 'wrap' }}>
            <div>
              <div style={{ fontSize: '1.5rem', fontWeight: 700, color: data.newFindings.length ? '#da1e28' : '#24a148' }}>
                {data.newFindings.length}
              </div>
              <div style={{ fontSize: '0.75rem', color: 'var(--s-text-4)' }}>novos</div>
            </div>
            <div>
              <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#24a148' }}>{data.resolvedFindings.length}</div>
              <div style={{ fontSize: '0.75rem', color: 'var(--s-text-4)' }}>corrigidos</div>
            </div>
            <div>
              <div style={{ fontSize: '1.5rem', fontWeight: 700 }}>{data.persistingCount}</div>
              <div style={{ fontSize: '0.75rem', color: 'var(--s-text-4)' }}>continuam</div>
            </div>
            {data.scoreDelta !== null && (
              <Tag type={data.scoreDelta > 0 ? 'green' : data.scoreDelta < 0 ? 'red' : 'warm-gray'}>
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
            <ul style={{ margin: 0, paddingLeft: 0, listStyle: 'none' }}>
              {data.newFindings.slice(0, 5).map((f) => (
                <li key={f.id} style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', marginBottom: '0.25rem' }}>
                  <RiskBadge severity={f.severity} />
                  <span style={{ fontSize: '0.875rem' }}>{f.title}</span>
                </li>
              ))}
              {data.newFindings.length > 5 && (
                <li style={{ fontSize: '0.8125rem', color: 'var(--s-text-4)' }}>…e mais {data.newFindings.length - 5}</li>
              )}
            </ul>
          )}
        </>
      )}
    </Tile>
  );
}
