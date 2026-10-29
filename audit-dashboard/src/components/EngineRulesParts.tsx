import { Button, Checkbox, InlineNotification } from '@carbon/react';
import type { EngineConfig, RuleAdjustment } from '../types';
import { isNoopAdjustment, type BulkAction, type RuleRow, type RuleScope } from '../utils/ruleCatalog';

export const mono = "'IBM Plex Mono', ui-monospace, monospace";
export const SEVERITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'] as const;
export const PAGE = 25;
export const fieldStyle = { height: 36, padding: '0 8px', border: 0, borderBottom: '1px solid var(--s-border-strong)', background: 'var(--s-layer)', color: 'var(--s-text)' } as const;
const THRESHOLD_LABEL: Record<string, string> = {
  'max-function-lines': 'Linhas por função',
  'max-component-lines': 'Linhas por componente',
  'max-parameters': 'Parâmetros por função',
  'max-complexity': 'Complexidade',
  'max-nesting': 'Aninhamento',
  'max-findings': 'Achados por regra de clean code',
};
const labelStyle = { display: 'grid', gap: 4, fontSize: 12, color: 'var(--s-text-3)' } as const;
const cellStyle = { padding: '6px 8px', verticalAlign: 'top' } as const;
const groupStyle = { border: 0, padding: 0, margin: 0, display: 'grid', gap: 8 } as const;
const groupLegend = { fontSize: 14, fontWeight: 600, marginBottom: 4 } as const;

export function LoadError({ error }: { error: unknown }) {
  return (
    <div style={{ padding: 24 }}>
      <InlineNotification kind="error" title="Não foi possível carregar" subtitle={error instanceof Error ? error.message : 'Erro desconhecido'} hideCloseButton />
    </div>
  );
}

/** Avisos de erro ou sucesso do último salvamento. */
export function SaveNotices({ save, dirty }: { save: { isError: boolean; isSuccess: boolean; isPending: boolean; error: unknown }; dirty: boolean }) {
  return (
    <>
      {save.isError && <InlineNotification kind="error" title="Não foi possível salvar" subtitle={(save.error as Error).message} hideCloseButton />}
      {save.isSuccess && !save.isPending && !dirty && <InlineNotification kind="success" title="Ajustes salvos" subtitle="Valem na próxima auditoria." hideCloseButton lowContrast />}
    </>
  );
}

export function SaveBar({ pending, disabled, dirty, onSave }: { pending: boolean; disabled: boolean; dirty: boolean; onSave: () => void }) {
  return (
    <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
      <Button kind="primary" size="md" disabled={disabled} onClick={onSave}>
        {pending ? 'Salvando…' : 'Salvar ajustes'}
      </Button>
      {dirty && <span role="status" style={{ fontSize: 13, color: 'var(--s-warn-text)' }}>Alterações não salvas</span>}
    </div>
  );
}

export function PacksFieldset({ cfg, packs, setPacks }: { cfg: EngineConfig; packs: Record<string, boolean>; setPacks: (p: Record<string, boolean>) => void }) {
  return (
    <fieldset style={groupStyle}>
      <legend style={groupLegend}>Pacotes de regras</legend>
      {cfg.packs.map((p) => (
        <Checkbox
          key={p.id}
          id={`eng-pack-${p.id}`}
          labelText={`${p.name || p.id}${p.inProfile ? '' : ' (fora do perfil)'}`}
          helperText={p.description ?? undefined}
          checked={!!packs[p.id]}
          onChange={(_, { checked }) => setPacks({ ...packs, [p.id]: checked })}
        />
      ))}
    </fieldset>
  );
}

export function LimitsFieldset({ cfg, limits, setLimits, limitError }: { cfg: EngineConfig; limits: Record<string, string>; setLimits: (l: Record<string, string>) => void; limitError: (key: string) => boolean }) {
  return (
    <fieldset style={groupStyle}>
      <legend style={groupLegend}>Limites de clean code</legend>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(220px, 100%), 1fr))', gap: 12 }}>
        {cfg.thresholds.map((t) => (
          <label key={t.key} style={labelStyle}>
            {THRESHOLD_LABEL[t.key] ?? t.key}
            <input
              aria-label={THRESHOLD_LABEL[t.key] ?? t.key}
              inputMode="numeric"
              value={limits[t.key] ?? ''}
              placeholder={t.profileValue === null ? 'padrão do motor' : `perfil: ${t.profileValue}`}
              onChange={(e) => setLimits({ ...limits, [t.key]: e.target.value })}
              style={{ ...fieldStyle, borderBottom: `1px solid ${limitError(t.key) ? '#da1e28' : 'var(--s-border-strong)'}`, fontFamily: mono }}
            />
            {limitError(t.key) && <span style={{ color: 'var(--s-danger-text)' }}>Use um inteiro maior que zero.</span>}
          </label>
        ))}
      </div>
    </fieldset>
  );
}

interface FilterProps {
  text: string;
  scope: RuleScope;
  packFilter: string;
  packNames: string[];
  count: number;
  /** Qualquer mudança de filtro volta a lista para a primeira página. */
  onText: (v: string) => void;
  onScope: (v: RuleScope) => void;
  onPack: (v: string) => void;
}

export function RuleFilters({ text, scope, packFilter, packNames, count, onText, onScope, onPack }: FilterProps) {
  return (
    <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'end' }}>
      <label style={labelStyle}>
        Buscar regra
        <input
          aria-label="Buscar regra"
          type="search"
          placeholder="id, texto, pacote ou categoria"
          value={text}
          onChange={(e) => onText(e.target.value)}
          style={{ ...fieldStyle, minWidth: 240 }}
        />
      </label>
      <label style={labelStyle}>
        Mostrar
        <select aria-label="Mostrar regras" value={scope} onChange={(e) => onScope(e.target.value as RuleScope)} style={fieldStyle}>
          <option value="all">Todas</option>
          <option value="seen">As que apareceram na última auditoria</option>
          <option value="adjusted">As que eu ajustei</option>
        </select>
      </label>
      {packNames.length > 0 && (
        <label style={labelStyle}>
          Pacote
          <select aria-label="Filtrar por pacote" value={packFilter} onChange={(e) => onPack(e.target.value)} style={fieldStyle}>
            <option value="">Todos</option>
            {packNames.map((p) => (
              <option key={p} value={p}>{p}</option>
            ))}
          </select>
        </label>
      )}
      <span role="status" style={{ fontSize: 12, color: 'var(--s-text-3)' }}>
        {count} {count === 1 ? 'regra' : 'regras'}
      </span>
    </div>
  );
}

export function BulkBar({ count, gain, bulk }: { count: number; gain: number; bulk: (action: BulkAction) => void }) {
  if (count === 0) return null;
  return (
    <div role="group" aria-label="Ações em lote" style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center', background: 'var(--s-selected)', padding: '8px 12px' }}>
      <strong style={{ fontSize: 13 }}>{count} {count === 1 ? 'selecionada' : 'selecionadas'}</strong>
      <Button kind="tertiary" size="sm" onClick={() => bulk({ kind: 'shadow', on: true })}>Só observar</Button>
      <Button kind="tertiary" size="sm" onClick={() => bulk({ kind: 'disable', on: true })}>Desligar</Button>
      <Button kind="tertiary" size="sm" onClick={() => bulk({ kind: 'disable', on: false })}>Ligar de novo</Button>
      <label style={{ fontSize: 12, display: 'inline-flex', gap: 6, alignItems: 'center' }}>
        Severidade
        <select
          aria-label="Severidade em lote"
          value=""
          onChange={(e) => e.target.value && bulk({ kind: 'severity', value: (e.target.value === 'perfil' ? null : e.target.value) as RuleAdjustment['severity'] })}
        >
          <option value="">escolher…</option>
          <option value="perfil">do perfil</option>
          {SEVERITIES.map((sv) => (
            <option key={sv} value={sv}>{sv}</option>
          ))}
        </select>
      </label>
      <Button kind="ghost" size="sm" onClick={() => bulk({ kind: 'reset' })}>Limpar ajustes</Button>
      {gain > 0 && (
        <span style={{ fontSize: 12, color: 'var(--s-text-3)' }} title="Estimativa; regras diferentes podem se sobrepor">
          Se deixarem de pesar, a nota sobe cerca de +{gain.toFixed(1)}
        </span>
      )}
    </div>
  );
}

function gainText(row: RuleRow): string {
  if (row.gain) return `+${row.gain.toFixed(1)}`;
  return row.count > 0 ? '—' : 'não apareceu';
}

interface RowProps {
  row: RuleRow;
  adjustment: RuleAdjustment;
  inProfile: boolean;
  selected: boolean;
  onSelect: (id: string, on: boolean) => void;
  onChange: (id: string, patch: Partial<RuleAdjustment>) => void;
}

function RuleIdentityCell({ row, inProfile }: { row: RuleRow; inProfile: boolean }) {
  const origin = [row.pack, row.category, row.declared ? `declara ${row.declared}` : null].filter(Boolean);
  return (
    <td style={{ padding: '6px 8px', maxWidth: 520 }}>
      <div style={{ fontFamily: mono, wordBreak: 'break-all' }}>
        {row.id}
        {row.count > 0 && <span style={{ color: 'var(--s-text-3)' }}> · {row.count}</span>}
      </div>
      {row.title && <div style={{ color: 'var(--s-text-2)', marginTop: 2 }}>{row.title}</div>}
      <div style={{ color: 'var(--s-text-3)', marginTop: 2 }}>
        {origin.join(' · ')}
        {inProfile && <span>{origin.length > 0 ? ' · ' : ''}perfil já ajusta esta regra</span>}
      </div>
    </td>
  );
}

function SeverityCell({ id, value, onChange }: { id: string; value: RuleAdjustment['severity']; onChange: RowProps['onChange'] }) {
  return (
    <td style={cellStyle}>
      <select
        aria-label={`Severidade de ${id}`}
        value={value ?? ''}
        onChange={(e) => onChange(id, { severity: (e.target.value || null) as RuleAdjustment['severity'] })}
      >
        <option value="">do perfil</option>
        {SEVERITIES.map((sv) => (
          <option key={sv} value={sv}>{sv}</option>
        ))}
      </select>
    </td>
  );
}

function RuleTableRow({ row, adjustment: r, inProfile, selected, onSelect, onChange }: RowProps) {
  const id = row.id;
  return (
    <tr style={{ borderTop: '1px solid var(--s-border)', background: isNoopAdjustment(r) ? undefined : 'var(--s-selected)' }}>
      <td style={cellStyle}>
        <input type="checkbox" aria-label={`Selecionar ${id}`} checked={selected} onChange={(e) => onSelect(id, e.target.checked)} />
      </td>
      <RuleIdentityCell row={row} inProfile={inProfile} />
      <td style={{ ...cellStyle, color: row.gain ? 'var(--s-success-text)' : 'var(--s-text-3)' }}>{gainText(row)}</td>
      <SeverityCell id={id} value={r.severity} onChange={onChange} />
      <td style={cellStyle}>
        <input type="checkbox" aria-label={`Desligar ${id}`} checked={r.disabled} onChange={(e) => onChange(id, { disabled: e.target.checked })} />
      </td>
      <td style={cellStyle}>
        <input type="checkbox" aria-label={`Só observar ${id}`} checked={r.shadow === true} onChange={(e) => onChange(id, { shadow: e.target.checked ? true : null })} />
      </td>
    </tr>
  );
}

interface TableProps {
  rows: RuleRow[];
  allShownPicked: boolean;
  isPicked: (id: string) => boolean;
  current: (id: string) => RuleAdjustment;
  inProfile: (id: string) => boolean;
  onPickAll: (on: boolean) => void;
  onSelect: (id: string, on: boolean) => void;
  onChange: (id: string, patch: Partial<RuleAdjustment>) => void;
}

export function RulesTable({ rows, allShownPicked, isPicked, current, inProfile, onPickAll, onSelect, onChange }: TableProps) {
  return (
    <div style={{ overflowX: 'auto' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
        <thead>
          <tr style={{ textAlign: 'left', background: 'var(--s-header-row)' }}>
            <th style={{ padding: '6px 8px', width: 28 }}>
              <input type="checkbox" aria-label="Selecionar as regras mostradas" checked={allShownPicked} onChange={(e) => onPickAll(e.target.checked)} />
            </th>
            <th style={{ padding: '6px 8px' }}>Regra</th>
            <th style={{ padding: '6px 8px' }}>Ganho na nota</th>
            <th style={{ padding: '6px 8px' }}>Severidade</th>
            <th style={{ padding: '6px 8px' }}>Desligada</th>
            <th style={{ padding: '6px 8px' }}>Só observar</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <RuleTableRow key={row.id} row={row} adjustment={current(row.id)} inProfile={inProfile(row.id)} selected={isPicked(row.id)} onSelect={onSelect} onChange={onChange} />
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function AddRuleRow({ value, onChange, onAdd }: { value: string; onChange: (v: string) => void; onAdd: () => void }) {
  return (
    <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
      <input
        aria-label="Id da regra"
        placeholder="id da regra ou padrão com *"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && onAdd()}
        style={{ ...fieldStyle, fontFamily: mono, minWidth: 240 }}
      />
      <Button kind="tertiary" size="sm" onClick={onAdd}>Adicionar regra</Button>
    </div>
  );
}
