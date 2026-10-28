import { useEffect, useState, type ReactNode } from 'react';

/** Seção da página sem caixa: separada das outras por espaço e uma linha fina. */
export function PageSection({
  id,
  title,
  hint,
  aside,
  first,
  children,
}: {
  id: string;
  title: ReactNode;
  hint?: ReactNode;
  aside?: ReactNode;
  first?: boolean;
  children: ReactNode;
}) {
  return (
    <section
      id={id}
      aria-labelledby={`${id}-titulo`}
      style={{ scrollMarginTop: 72, borderTop: first ? 'none' : '1px solid var(--s-border)', paddingTop: first ? 0 : 32 }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', flexWrap: 'wrap', gap: 8, marginBottom: hint ? 4 : 16 }}>
        <h2 id={`${id}-titulo`} style={{ margin: 0, fontSize: 20, fontWeight: 600 }}>{title}</h2>
        {aside}
      </div>
      {hint && <p style={{ margin: '0 0 16px', color: 'var(--s-text-3)', fontSize: 14 }}>{hint}</p>}
      {children}
    </section>
  );
}

export interface IndexItem {
  id: string;
  label: string;
  badge?: string;
}

/** Seção que está na altura de leitura; sem IntersectionObserver (testes) fica a primeira. */
function useActiveSection(ids: string[]): [string, (id: string) => void] {
  const [active, setActive] = useState(ids[0] ?? '');
  const key = ids.join('|');
  useEffect(() => {
    if (typeof IntersectionObserver === 'undefined') return undefined;
    const sections = ids.map((id) => document.getElementById(id)).filter((el): el is HTMLElement => el !== null);
    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries.filter((e) => e.isIntersecting).sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top);
        if (visible[0]) setActive(visible[0].target.id);
      },
      { rootMargin: '-80px 0px -60% 0px' },
    );
    sections.forEach((s) => observer.observe(s));
    return () => observer.disconnect();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- `key` resume a lista de ids
  }, [key]);
  return [active, setActive];
}

/** Índice fixo ao lado: mostra onde a pessoa está e leva direto a cada seção. */
export function SectionIndex({ items, label = 'Nesta página' }: { items: IndexItem[]; label?: string }) {
  const [active, setActive] = useActiveSection(items.map((i) => i.id));
  return (
    <nav aria-label={label} className="sentinel-indice">
      <div style={{ fontSize: 12, fontWeight: 600, letterSpacing: '0.04em', textTransform: 'uppercase', color: 'var(--s-text-3)', marginBottom: 8 }}>{label}</div>
      {items.map((item) => {
        const on = active === item.id;
        return (
          <a
            key={item.id}
            href={`#${item.id}`}
            aria-current={on ? 'true' : undefined}
            className="sentinel-linha-hover"
            onClick={(e) => {
              const target = document.getElementById(item.id);
              if (!target) return;
              e.preventDefault();
              setActive(item.id);
              target.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }}
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              gap: 8,
              padding: '6px 10px',
              borderLeft: `2px solid ${on ? 'var(--s-link)' : 'transparent'}`,
              color: on ? 'var(--s-link)' : 'var(--s-text-2)',
              fontWeight: on ? 600 : 400,
              textDecoration: 'none',
              fontSize: 14,
            }}
          >
            <span>{item.label}</span>
            {item.badge && <span style={{ color: 'var(--s-text-3)', fontSize: 12, fontWeight: 400 }}>{item.badge}</span>}
          </a>
        );
      })}
    </nav>
  );
}
