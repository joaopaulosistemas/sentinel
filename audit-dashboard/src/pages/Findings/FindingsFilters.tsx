import type { SystemSummaryResponse } from '../../types';
import {
  severityChoices,
  situationChoices,
  type FilterChip,
  type SeverityChoice,
  type SituationChoice,
} from '../../utils/findingsView';
import type { Category } from '../../types';

export const label = { fontSize: 12, color: 'var(--s-text-3)' } as const;
export const select = { height: 40, padding: '0 8px', border: 0, borderBottom: '1px solid var(--s-border-strong)', background: 'var(--s-layer)', fontSize: 14 } as const;

export const categoryOptions: Array<{ value: Category | ''; label: string }> = [
  { value: '', label: 'Todas' },
  { value: 'SECURITY', label: 'Segurança' },
  { value: 'QUALITY', label: 'Qualidade' },
  { value: 'ARCHITECTURE', label: 'Arquitetura' },
  { value: 'DEPENDENCY', label: 'Dependências' },
  { value: 'OBSERVABILITY', label: 'Observabilidade' },
  { value: 'PERFORMANCE', label: 'Desempenho' },
];

interface FiltersProps {
  auditMode: boolean;
  systems: SystemSummaryResponse[] | undefined;
  currentSystemValue: string;
  onPickSystem: (id: number | undefined) => void;
  severity: SeverityChoice;
  onSeverity: (v: SeverityChoice) => void;
  category: Category | undefined;
  onCategory: (v: Category | undefined) => void;
  situation: SituationChoice;
  onSituation: (v: SituationChoice) => void;
  grouped: boolean;
  onGrouped: (v: boolean) => void;
  lowTotal: number;
  lowHidden: number;
  showLow: boolean;
  onShowLow: (v: boolean) => void;
  chips: FilterChip[];
  isDefault: boolean;
  onRestore: () => void;
}

const fieldBox = { display: 'flex', flexDirection: 'column', gap: 6 } as const;

function SelectField({ id, text, strong, value, onChange, children }: {
  id: string;
  text: string;
  strong?: boolean;
  value: string;
  onChange: (v: string) => void;
  children: React.ReactNode;
}) {
  return (
    <div style={fieldBox}>
      <label htmlFor={id} style={strong ? { ...label, fontWeight: 600 } : label}>{text}</label>
      <select
        id={id}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        style={strong ? { ...select, borderBottom: '2px solid var(--s-link)' } : select}
      >
        {children}
      </select>
    </div>
  );
}

function SystemField({ systems, value, onPick }: { systems: SystemSummaryResponse[] | undefined; value: string; onPick: (id: number | undefined) => void }) {
  return (
    <SelectField id="f-sis" text="Sistema" strong value={value} onChange={(v) => onPick(v === '' ? undefined : Number(v))}>
      <option value="">Todos os sistemas{systems ? ` (${systems.length})` : ''}</option>
      {(systems ?? []).map((s) => (
        <option key={s.id} value={s.id}>{s.name}</option>
      ))}
    </SelectField>
  );
}

function LowConfidenceToggle({ lowHidden, showLow, onShowLow }: { lowHidden: number; showLow: boolean; onShowLow: (v: boolean) => void }) {
  return (
    <label style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: 13 }}>
      <input type="checkbox" checked={showLow} onChange={(e) => onShowLow(e.target.checked)} style={{ width: 16, height: 16, accentColor: '#0f62fe' }} />
      Mostrar achados de baixa confiança
      {!showLow && <span style={{ color: 'var(--s-text-3)' }}>({lowHidden} ocultos: em testes, exemplos ou de regras com muitos falsos positivos)</span>}
    </label>
  );
}

function ActiveChips({ chips, isDefault, onRestore }: { chips: FilterChip[]; isDefault: boolean; onRestore: () => void }) {
  return (
    <div aria-label="Filtros ativos" style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center', fontSize: 13, color: 'var(--s-text-3)' }}>
      Filtros ativos:
      {chips.map((c) => {
        const blue = c.tone === 'blue';
        return (
          <span key={c.key} style={{ background: blue ? '#d0e2ff' : 'var(--s-border)', color: blue ? '#0043ce' : 'var(--s-text)', padding: '3px 10px', borderRadius: 12 }}>
            {c.label}
          </span>
        );
      })}
      {!isDefault && (
        <button type="button" onClick={onRestore} style={{ background: 'none', border: 0, color: 'var(--s-link)', cursor: 'pointer', fontSize: 13, padding: '0 4px' }}>
          Restaurar padrão
        </button>
      )}
    </div>
  );
}

/** Filtros da tela de achados: sistema, severidade, categoria, situação, baixa confiança e os filtros ativos. */
export function FindingsFilters(p: FiltersProps) {
  return (
    <section aria-label="Filtros" style={{ background: 'var(--s-surface)', border: '1px solid var(--s-border)', padding: 16, display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(190px, 100%), 1fr))', gap: 14, alignItems: 'end' }}>
        <SystemField systems={p.systems} value={p.currentSystemValue} onPick={p.onPickSystem} />
        <SelectField id="f-sev" text="Severidade" value={p.severity} onChange={(v) => p.onSeverity(v as SeverityChoice)}>
          {severityChoices.map((c) => (
            <option key={c.id} value={c.id}>{c.label}</option>
          ))}
        </SelectField>
        <SelectField id="f-cat" text="Categoria" value={p.category ?? ''} onChange={(v) => p.onCategory((v || undefined) as Category | undefined)}>
          {categoryOptions.map((c) => (
            <option key={c.value} value={c.value}>{c.label}</option>
          ))}
        </SelectField>
        <SelectField id="f-st" text="Situação" value={p.situation} onChange={(v) => p.onSituation(v as SituationChoice)}>
          {situationChoices.map((c) => (
            <option key={c.id} value={c.id}>{c.label}</option>
          ))}
        </SelectField>
        {!p.auditMode && (
          <label style={{ display: 'flex', alignItems: 'center', gap: 10, height: 40 }}>
            <input type="checkbox" checked={p.grouped} onChange={(e) => p.onGrouped(e.target.checked)} style={{ width: 18, height: 18, accentColor: '#0f62fe' }} />
            Agrupar por sistema
          </label>
        )}
      </div>
      {p.auditMode && p.lowTotal > 0 && <LowConfidenceToggle lowHidden={p.lowHidden} showLow={p.showLow} onShowLow={p.onShowLow} />}
      <ActiveChips chips={p.chips} isDefault={p.isDefault} onRestore={p.onRestore} />
    </section>
  );
}

interface FooterProps {
  text: string;
  page: number;
  pages: number;
  pageSize: number;
  onPage: (p: number) => void;
  onPageSize: (n: number) => void;
}

/** Rodapé da lista: texto de contagem, itens por página e navegação. */
export function PaginationFooter({ text, page, pages, pageSize, onPage, onPageSize }: FooterProps) {
  return (
    <div style={{ padding: '12px 16px', display: 'flex', justifyContent: 'space-between', fontSize: 13, color: 'var(--s-text-3)', borderTop: '1px solid var(--s-border)', flexWrap: 'wrap', gap: 8, alignItems: 'center' }}>
      <span>{text}</span>
      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 12 }}>
        <label style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          Por página
          <select aria-label="Itens por página" value={pageSize} onChange={(e) => onPageSize(Number(e.target.value))} style={{ ...select, height: 32 }}>
            {[10, 20, 50].map((n) => (
              <option key={n} value={n}>{n}</option>
            ))}
          </select>
        </label>
        <span>Página {page} de {pages}</span>
        <button type="button" aria-label="Página anterior" disabled={page <= 1} onClick={() => onPage(page - 1)} style={{ border: '1px solid var(--s-border-strong)', background: 'var(--s-surface)', width: 32, height: 32, cursor: page <= 1 ? 'not-allowed' : 'pointer' }}>‹</button>
        <button type="button" aria-label="Próxima página" disabled={page >= pages} onClick={() => onPage(page + 1)} style={{ border: '1px solid var(--s-border-strong)', background: 'var(--s-surface)', width: 32, height: 32, cursor: page >= pages ? 'not-allowed' : 'pointer' }}>›</button>
      </span>
    </div>
  );
}
