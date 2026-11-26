import type { CSSProperties, ReactNode } from 'react';

interface IDataTableColumn {
  label: string;
  align?: 'left' | 'right';
  /** Largura fixa da coluna, em px. */
  width?: number;
}

interface IDataTableProps {
  /** Nome acessível da tabela (o que ela lista). */
  label: string;
  columns: IDataTableColumn[];
  /** "faixa": cabeçalho com fundo; "linha": só uma linha embaixo. */
  header?: 'faixa' | 'linha';
  /** Respiro do cabeçalho e das colunas. */
  density?: 'compacta' | 'confortavel';
  minWidth?: number;
  fontSize?: number;
  className?: string;
  children: ReactNode;
}

const PADDING: Record<NonNullable<IDataTableProps['density']>, string> = { compacta: '6px 8px', confortavel: '10px 12px' };

/**
 * Tabela das telas: nome acessível, `scope="col"` em todo cabeçalho e rolagem horizontal em tela estreita.
 * As linhas (`<tr>`) ficam por conta de quem usa; assim cada tela mantém o seu corpo.
 */
export function DataTable({ label, columns, header = 'faixa', density = 'compacta', minWidth, fontSize, className, children }: IDataTableProps) {
  const th = (c: IDataTableColumn): CSSProperties => ({
    padding: PADDING[density], textAlign: c.align ?? 'left', width: c.width, fontSize: 12, fontWeight: 600,
    color: header === 'linha' ? 'var(--s-text-3)' : undefined,
  });
  return (
    <div style={{ overflowX: 'auto' }}>
      <table aria-label={label} className={className} style={{ width: '100%', borderCollapse: 'collapse', minWidth, fontSize }}>
        <thead>
          <tr style={header === 'faixa' ? { background: 'var(--s-header-row)' } : { borderBottom: '1px solid var(--s-border)' }}>
            {columns.map((c) => <th key={c.label} scope="col" style={th(c)}>{c.label}</th>)}
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  );
}
