import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';

/** Um nível do caminho. Sem `to`, é a tela atual (último nível). */
export interface Crumb {
  label: string;
  to?: string;
}

/** Altura da linha do caminho; é a mesma em todas as telas, para o título ficar sempre na mesma altura. */
export const TRAIL_HEIGHT = 16;

/**
 * Moldura padrão das telas: mesma margem, mesma largura e o caminho (breadcrumb) acima do título.
 * Espaçamento: 24 px do cabeçalho até o caminho, 8 px até o título, 24 px do título até o conteúdo.
 * Toda tela informa o seu caminho, começando em "Início".
 */
export function PageShell({ crumbs, children }: { crumbs: Crumb[]; children: ReactNode }) {
  return (
    <div style={{ padding: '40px 2rem 3rem', maxWidth: 1440, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 24 }}>
      <div data-testid="page-trail" style={{ minHeight: TRAIL_HEIGHT, marginBottom: -16 }}>
        <Breadcrumb items={crumbs} />
      </div>
      {children}
    </div>
  );
}

/** Caminho (breadcrumb) no estilo das telas de detalhe. */
export function Breadcrumb({ items }: { items: Crumb[] }) {
  return (
    <nav aria-label="Caminho" style={{ fontSize: 12, lineHeight: '16px', color: 'var(--s-text-3)' }}>
      {items.map((c, i) => (
        <span key={`${i}-${c.label}`}>
          {i > 0 && <span aria-hidden="true"> / </span>}
          {c.to ? <Link to={c.to}>{c.label}</Link> : <span aria-current="page">{c.label}</span>}
        </span>
      ))}
    </nav>
  );
}
