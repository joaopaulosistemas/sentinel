import { Button, Checkbox, InlineNotification } from '@carbon/react';
import type { IEngineConfig, IRuleAdjustment } from '../types';
import type { ISidePack } from '../utils/ruleFamilies';
import { isNoopAdjustment, type BulkAction, type IRuleRow, type RuleScope } from '../utils/ruleCatalog';
import { SIDE_NAME, limitSide, packsBySide, sideIsOff, sideOfRule, type IFamilyTab } from '../utils/ruleFamilies';
import { SideBadge } from './EngineScope';

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

interface ILimitsProps {
  cfg: IEngineConfig;
  limits: Record<string, string>;
  setLimits: (l: Record<string, string>) => void;
  limitError: (key: string) => boolean;
}

const LIMITS_BOX: Record<'front' | 'both', { title: string; note: string }> = {
  front: { title: 'Limite de clean code · React', note: 'Vale só para arquivos .tsx e .jsx, que costumam ser mais longos por causa do JSX.' },
  both: {
    title: 'Limites de clean code',
    note: 'Valem para Java e para TypeScript/JavaScript. “Achados por regra de clean code” também limita a duplicação e o código morto.',
  },
};

/** Limites de clean code que valem para um lado; some quando o perfil não traz nenhum deles. */
function LimitsBox({ side, cfg, limits, setLimits, limitError }: ILimitsProps & { side: 'front' | 'both' }) {
  const items = cfg.thresholds.filter((t) => limitSide(t.key) === side);
  if (items.length === 0) return null;
  const text = LIMITS_BOX[side];
  return (
    <div role="group" aria-label={text.title} style={{ display: 'grid', gap: 8, marginTop: 6, padding: '10px 12px', background: 'var(--s-header-row)' }}>
      <div style={{ fontSize: 14, fontWeight: 600 }}>{text.title}</div>
      <div style={{ fontSize: 12, color: 'var(--s-text-3)' }}>{text.note}</div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(220px, 100%), 1fr))', gap: 12 }}>
        {items.map((t) => (
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
    </div>
  );
}

const SIDE_OFF_SUFFIX = ' · lado desligado';

function packLabel(p: ISidePack): string {
  const pk = p.pack;
  const others = p.others.map((o) => `${SIDE_NAME[o.side]}: ${o.count}`).join(', ');
  return `${pk.name || pk.id}${pk.inProfile ? '' : ' (fora do perfil)'}${p.count ? ` · ${p.count} ${p.count === 1 ? 'regra' : 'regras'}` : ''}${others ? ` · também em ${others}` : ''}`;
}

/** Pacotes por lado (back-end, front-end, infra e comum); um pacote misto aparece nos dois lados. Os limites de clean code ficam no grupo a que valem. */
export function PacksFieldset({ cfg, packs, setPacks, sidesOff, limits, setLimits, limitError }: ILimitsProps & {
  packs: Record<string, boolean>;
  setPacks: (p: Record<string, boolean>) => void;
  sidesOff: string[];
}) {
  const groups = packsBySide(cfg.packs, cfg.catalog);
  const present = new Set(groups.map((g) => g.side));
  const limitSideOf = (side: string): 'front' | 'both' | null => (side === 'front' ? 'front' : side === 'common' ? 'both' : null);
  return (
    <fieldset style={groupStyle}>
      <legend style={groupLegend}>Pacotes de regras</legend>
      <p style={{ margin: 0, fontSize: 12, color: 'var(--s-text-3)' }}>
        Um pacote com regras dos dois lados aparece nos dois, com a contagem de cada lado. A caixa do pacote é uma só: desmarcar “Segurança” tira as regras dos dois lados. Para tirar só um lado, use o escopo acima.
      </p>
      {groups.map((g) => {
        const off = sideIsOff(g.side, sidesOff);
        const box = limitSideOf(g.side);
        return (
          <div key={g.side} role="group" aria-label={g.title} style={{ display: 'grid', gap: 8, paddingTop: 6 }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--s-text-3)', borderBottom: '1px solid var(--s-border)', paddingBottom: 4, display: 'flex', gap: 8, alignItems: 'center' }}>
              <SideBadge side={g.side} /> {g.title}{' '}
              <span style={{ fontWeight: 400 }}>· {g.packs.length} {g.packs.length === 1 ? 'pacote' : 'pacotes'} · {g.rules} {g.rules === 1 ? 'regra' : 'regras'}</span>
            </div>
            {g.packs.map((p) => (
              <div key={p.pack.id} style={{ opacity: off ? 0.5 : 1 }}>
                <Checkbox
                  id={`eng-pack-${g.side}-${p.pack.id}`}
                  labelText={`${packLabel(p)}${off ? SIDE_OFF_SUFFIX : ''}`}
                  helperText={p.pack.description ?? undefined}
                  checked={!!packs[p.pack.id]}
                  onChange={(_, { checked }) => setPacks({ ...packs, [p.pack.id]: checked })}
                />
              </div>
            ))}
            {box && <LimitsBox side={box} cfg={cfg} limits={limits} setLimits={setLimits} limitError={limitError} />}
          </div>
        );
      })}
      {/* Se não há pacote do lado a que um limite vale, o limite continua editável aqui. */}
      {(['front', 'both'] as const).filter((side) => !present.has(side === 'both' ? 'common' : 'front')).map((side) => (
        <LimitsBox key={`limits-${side}`} side={side} cfg={cfg} limits={limits} setLimits={setLimits} limitError={limitError} />
      ))}
    </fieldset>
  );
}

/** Abas por lado das regras: Todas, Back-end, Front-end, Infra e comum; lado desligado aparece marcado. */
export function SideTabs({ counts, active, sidesOff, onPick }: { counts: Record<'all' | 'back' | 'front' | 'common', number>; active: string; sidesOff: string[]; onPick: (key: string) => void }) {
  const tabs = [
    { key: 'all', name: 'Todas' },
    { key: 'back', name: SIDE_NAME.back },
    { key: 'front', name: SIDE_NAME.front },
    { key: 'common', name: SIDE_NAME.common },
  ] as const;
  return (
    <div role="tablist" aria-label="Lado da regra" style={{ display: 'flex', flexWrap: 'wrap', borderBottom: '1px solid var(--s-border)' }}>
      {tabs.map((t) => {
        const on = t.key === active;
        const off = t.key !== 'all' && sideIsOff(t.key, sidesOff);
        return (
          <button
            key={t.key}
            type="button"
            role="tab"
            aria-selected={on}
            onClick={() => onPick(t.key)}
            style={{ padding: '10px 16px', border: 0, borderBottom: `2px solid ${on ? '#0f62fe' : 'transparent'}`, background: on ? 'var(--s-surface)' : 'transparent', color: off ? 'var(--s-text-4)' : 'var(--s-text)', fontWeight: on ? 600 : 400, cursor: 'pointer' }}
          >
            {t.name}{off ? ' (desligado)' : ''} <span style={{ fontWeight: 400, color: 'var(--s-text-3)' }}>· {counts[t.key]}</span>
          </button>
        );
      })}
    </div>
  );
}

/** Uma aba por família de regras (a categoria que a regra declara), com quantas regras tem. */
export function FamilyTabs({ tabs, active, onPick }: { tabs: IFamilyTab[]; active: string; onPick: (key: string) => void }) {
  if (tabs.length < 2) return null;
  return (
    <div role="tablist" aria-label="Famílias de regras" style={{ display: 'flex', flexWrap: 'wrap', borderBottom: '1px solid var(--s-border)' }}>
      {tabs.map((t) => {
        const on = t.key === active;
        return (
          <button
            key={t.key}
            type="button"
            role="tab"
            aria-selected={on}
            onClick={() => onPick(t.key)}
            style={{
              display: 'flex', flexDirection: 'column', alignItems: 'flex-start', minHeight: 48, padding: '6px 16px', border: 0,
              borderBottom: `3px solid ${on ? '#0f62fe' : 'transparent'}`, background: on ? 'var(--s-selected)' : 'transparent',
              color: 'var(--s-text)', cursor: 'pointer', textAlign: 'left',
            }}
          >
            <span style={{ fontWeight: 600 }}>{t.name}</span>
            <span style={{ fontSize: 12, color: 'var(--s-text-3)' }}>
              {t.total} {t.total === 1 ? 'regra' : 'regras'} · {t.seen} na auditoria{t.adjusted > 0 ? ` · ${t.adjusted} ${t.adjusted === 1 ? 'ajustada' : 'ajustadas'}` : ''}
            </span>
          </button>
        );
      })}
    </div>
  );
}

interface IFilterProps {
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

export function RuleFilters({ text, scope, packFilter, packNames, count, onText, onScope, onPack }: IFilterProps) {
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
          onChange={(e) => e.target.value && bulk({ kind: 'severity', value: (e.target.value === 'perfil' ? null : e.target.value) as IRuleAdjustment['severity'] })}
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

function gainText(row: IRuleRow): string {
  if (row.gain) return `+${row.gain.toFixed(1)}`;
  return row.count > 0 ? '—' : 'não apareceu';
}

interface IRowProps {
  row: IRuleRow;
  adjustment: IRuleAdjustment;
  inProfile: boolean;
  selected: boolean;
  /** O lado da regra está desligado no escopo: a regra não roda e não se edita. */
  off: boolean;
  onSelect: (id: string, on: boolean) => void;
  onChange: (id: string, patch: Partial<IRuleAdjustment>) => void;
}

function RuleIdentityCell({ row, inProfile, off }: { row: IRuleRow; inProfile: boolean; off: boolean }) {
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
      {off && <div style={{ color: 'var(--s-warn-text)', marginTop: 2 }}>Fora do escopo: não roda</div>}
    </td>
  );
}

function SeverityCell({ id, value, onChange, disabled }: { id: string; value: IRuleAdjustment['severity']; onChange: IRowProps['onChange']; disabled: boolean }) {
  return (
    <td style={cellStyle}>
      <select
        aria-label={`Severidade de ${id}`}
        disabled={disabled}
        value={value ?? ''}
        onChange={(e) => onChange(id, { severity: (e.target.value || null) as IRuleAdjustment['severity'] })}
      >
        <option value="">do perfil</option>
        {SEVERITIES.map((sv) => (
          <option key={sv} value={sv}>{sv}</option>
        ))}
      </select>
    </td>
  );
}

function RuleTableRow({ row, adjustment: r, inProfile, selected, off, onSelect, onChange }: IRowProps) {
  const id = row.id;
  return (
    <tr style={{ borderTop: '1px solid var(--s-border)', background: off ? 'var(--s-layer)' : isNoopAdjustment(r) ? undefined : 'var(--s-selected)', opacity: off ? 0.5 : 1 }}>
      <td style={cellStyle}>
        <input type="checkbox" aria-label={`Selecionar ${id}`} checked={selected} disabled={off} onChange={(e) => onSelect(id, e.target.checked)} />
      </td>
      <td style={cellStyle}><SideBadge side={sideOfRule(row)} /></td>
      <RuleIdentityCell row={row} inProfile={inProfile} off={off} />
      <td style={{ ...cellStyle, color: row.gain ? 'var(--s-success-text)' : 'var(--s-text-3)' }}>{off ? '—' : gainText(row)}</td>
      <SeverityCell id={id} value={r.severity} onChange={onChange} disabled={off} />
      <td style={cellStyle}>
        <input type="checkbox" aria-label={`Desligar ${id}`} checked={r.disabled} disabled={off} onChange={(e) => onChange(id, { disabled: e.target.checked })} />
      </td>
      <td style={cellStyle}>
        <input type="checkbox" aria-label={`Só observar ${id}`} checked={r.shadow === true} disabled={off} onChange={(e) => onChange(id, { shadow: e.target.checked ? true : null })} />
      </td>
    </tr>
  );
}

interface ITableProps {
  rows: IRuleRow[];
  allShownPicked: boolean;
  isPicked: (id: string) => boolean;
  current: (id: string) => IRuleAdjustment;
  inProfile: (id: string) => boolean;
  /** O lado desta regra está desligado no escopo? */
  isOff: (row: IRuleRow) => boolean;
  onPickAll: (on: boolean) => void;
  onSelect: (id: string, on: boolean) => void;
  onChange: (id: string, patch: Partial<IRuleAdjustment>) => void;
}

export function RulesTable({ rows, allShownPicked, isPicked, current, inProfile, isOff, onPickAll, onSelect, onChange }: ITableProps) {
  return (
    <div style={{ overflowX: 'auto' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
        <thead>
          <tr style={{ textAlign: 'left', background: 'var(--s-header-row)' }}>
            <th style={{ padding: '6px 8px', width: 28 }}>
              <input type="checkbox" aria-label="Selecionar as regras mostradas" checked={allShownPicked} onChange={(e) => onPickAll(e.target.checked)} />
            </th>
            <th style={{ padding: '6px 8px' }}>Lado</th>
            <th style={{ padding: '6px 8px' }}>Regra</th>
            <th style={{ padding: '6px 8px' }}>Ganho na nota</th>
            <th style={{ padding: '6px 8px' }}>Severidade</th>
            <th style={{ padding: '6px 8px' }}>Desligada</th>
            <th style={{ padding: '6px 8px' }}>Só observar</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <RuleTableRow key={row.id} row={row} adjustment={current(row.id)} inProfile={inProfile(row.id)} selected={isPicked(row.id)} off={isOff(row)} onSelect={onSelect} onChange={onChange} />
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
