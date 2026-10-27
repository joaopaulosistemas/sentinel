import type { ReactNode } from 'react';

/** Altura reservada acima do título: é a da trilha da jornada, para o título ficar na mesma altura em todas as telas. */
export const TRAIL_HEIGHT = 34;

/**
 * Moldura padrão das telas: mesma margem, mesma largura e uma faixa fixa acima do título.
 * A faixa leva a trilha da jornada e/ou o caminho (breadcrumb); sem nenhum dos dois, fica vazia só para manter a altura.
 */
export function PageShell({ trail, children }: { trail?: ReactNode; children: ReactNode }) {
  return (
    <div style={{ padding: '1.5rem 2rem 3rem', maxWidth: 1440, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 24 }}>
      <div
        data-testid="page-trail"
        style={{ minHeight: TRAIL_HEIGHT, display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', columnGap: 24 }}
      >
        {trail}
      </div>
      {children}
    </div>
  );
}

/** Caminho (breadcrumb) no estilo das telas de detalhe. */
export function Crumbs({ children }: { children: ReactNode }) {
  return (
    <nav aria-label="Caminho" style={{ fontSize: 12, color: 'var(--s-text-3)' }}>
      {children}
    </nav>
  );
}
