import { Button, InlineNotification, SkeletonText, Tag } from '@carbon/react';
import { useRemoveSuppression, useSuppressions } from '../hooks/useLifecycle';
import type { SuppressionReason } from '../types';
import { PageSection } from './PageSection';

const reasonLabels: Record<SuppressionReason, string> = {
  FALSE_POSITIVE: 'Falso positivo',
  ACCEPTED_RISK: 'Risco aceito',
  WONT_FIX: 'Não será corrigido',
};

/** Decisões de falso positivo / risco aceito do sistema, com quem decidiu e até quando. */
export function SuppressionsList({ systemId }: { systemId: number }) {
  const { data, isLoading } = useSuppressions(systemId);
  const remove = useRemoveSuppression();

  if (isLoading) return <SkeletonText lineCount={2} />;
  if (!Array.isArray(data) || data.length === 0) return null;

  return (
    <PageSection id="suprimidos" title={`Achados suprimidos (${data.length})`}>
      {remove.isError && (
        <InlineNotification
          kind="error"
          title="Não foi possível remover"
          subtitle={remove.error instanceof Error ? remove.error.message : 'Erro desconhecido'}
          style={{ marginBottom: '0.75rem' }}
        />
      )}
      <div style={{ display: 'flex', flexDirection: 'column' }}>
        {data.map((s) => (
          <div
            key={s.id}
            className="sentinel-linha-hover"
            style={{ display: 'flex', gap: 16, alignItems: 'center', padding: '14px 0', borderTop: '1px solid var(--s-border)' }}
          >
            <Tag type={s.active ? 'purple' : 'warm-gray'} size="sm">
              {s.active ? reasonLabels[s.reason] : 'Vencida'}
            </Tag>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: '0.875rem', fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {s.title ?? s.ruleId}
              </div>
              <div style={{ fontSize: '0.8125rem', color: 'var(--s-text-3)' }}>{s.justification}</div>
              <div style={{ fontSize: '0.75rem', color: 'var(--s-text-4)' }}>
                {s.createdBy ? `${s.createdBy} · ` : ''}
                {new Date(s.createdAt).toLocaleDateString('pt-BR')}
                {s.expiresAt ? ` · válido até ${new Date(s.expiresAt).toLocaleDateString('pt-BR')}` : ' · sem prazo'}
              </div>
            </div>
            <Button
              kind="ghost"
              size="sm"
              disabled={remove.isPending}
              onClick={() => remove.mutate({ systemId, suppressionId: s.id })}
            >
              Reabrir
            </Button>
          </div>
        ))}
      </div>
    </PageSection>
  );
}
