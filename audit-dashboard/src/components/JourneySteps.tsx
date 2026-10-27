export const JOURNEY = ['Escolher sistema', 'Iniciar auditoria', 'Acompanhar', 'Entender o resultado', 'Corrigir'] as const;

/** Trilha de 5 passos mostrada no topo das telas do fluxo de auditoria; o passo atual fica em destaque. */
export function JourneySteps({ current }: { current: 0 | 1 | 2 | 3 | 4 }) {
  return (
    <ol
      aria-label="Etapas da auditoria"
      style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexWrap: 'wrap', fontSize: '0.8125rem' }}
    >
      {JOURNEY.map((label, i) => {
        const done = i < current;
        const active = i === current;
        return (
          <li key={label} aria-current={active ? 'step' : undefined} style={{ display: 'flex', alignItems: 'center' }}>
            <span
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.5rem',
                padding: '0.375rem 0.875rem 0.375rem 0.375rem',
                color: active ? 'var(--s-text)' : 'var(--s-text-3)',
                fontWeight: active ? 600 : 400,
              }}
            >
              <span
                aria-hidden="true"
                style={{
                  width: 22,
                  height: 22,
                  borderRadius: 11,
                  display: 'inline-flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: '0.75rem',
                  fontWeight: 600,
                  background: active ? '#0f62fe' : done ? '#198038' : 'var(--s-border)',
                  color: active || done ? '#fff' : 'var(--s-text-3)',
                }}
              >
                {done ? '✓' : i + 1}
              </span>
              {label}
            </span>
            {i < JOURNEY.length - 1 && (
              <span aria-hidden="true" style={{ color: '#8d8d8d', paddingRight: '0.875rem' }}>
                ›
              </span>
            )}
          </li>
        );
      })}
    </ol>
  );
}
