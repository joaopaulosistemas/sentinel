import { Button, InlineNotification, SkeletonText, Tag, Tile } from '@carbon/react';
import { useRemoveSuppression, useSuppressions } from '../hooks/useLifecycle';
import type { SuppressionReason } from '../types';

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
  if (!data || data.length === 0) return null;

  return (
    <Tile style={{ marginBottom: '2rem', padding: '1.25rem' }}>
      <h3 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '0.75rem' }}>
        Findings suprimidos ({data.length})
      </h3>
      {remove.isError && (
        <InlineNotification
          kind="error"
          title="Não foi possível remover"
          subtitle={remove.error instanceof Error ? remove.error.message : 'Erro desconhecido'}
          style={{ marginBottom: '0.75rem' }}
        />
      )}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
        {data.map((s) => (
          <div
            key={s.id}
            style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', padding: '0.75rem', background: 'var(--s-layer)', borderRadius: '4px' }}
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
    </Tile>
  );
}
