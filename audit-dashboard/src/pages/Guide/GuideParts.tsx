import { Search, Tag } from '@carbon/react';
import { useState } from 'react';
import type { GuidePractice, GuideRule, GuideStack } from '../../types';
import { SEVERITY_LABEL, coverageOf, criteriaOf, filterRules, formatGrade, gradeOf, type SeverityFilter } from '../../utils/guide';

const card = { background: 'var(--s-surface)', border: '1px solid var(--s-border)', padding: '1rem 1.25rem' } as const;
const muted = { color: 'var(--s-text-3)', fontSize: '0.8125rem' } as const;
const TONE_COLOR = { good: 'var(--s-success-text)', warn: 'var(--s-warn-text)', bad: 'var(--s-danger-text)' } as const;
const SEVERITY_TAG = { alta: 'red', media: 'warm-gray', baixa: 'gray' } as const;
const PAGE_SIZE = 15;

function PillButton({ on, onClick, children }: { on: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={on}
      onClick={onClick}
      style={{
        minHeight: 36,
        padding: '0 1rem',
        border: '1px solid var(--s-border-strong)',
        background: on ? 'var(--s-text)' : 'var(--s-surface)',
        color: on ? 'var(--s-surface)' : 'var(--s-text)',
        cursor: 'pointer',
        font: 'inherit',
        fontSize: '0.875rem',
      }}
    >
      {children}
    </button>
  );
}

export function StackPicker({ stacks, current, onPick }: { stacks: GuideStack[]; current: string; onPick: (id: string) => void }) {
  return (
    <div role="group" aria-label="Tecnologia" style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
      {stacks.map((s) => (
        <PillButton key={s.id} on={s.id === current} onClick={() => onPick(s.id)}>
          {s.name} <span style={{ opacity: 0.8 }}>· {s.ownRules + s.inheritedRules}</span>
        </PillButton>
      ))}
    </div>
  );
}

export function AudienceSwitch({ ai, onChange }: { ai: boolean; onChange: (ai: boolean) => void }) {
  return (
    <div role="group" aria-label="Quem vai usar" style={{ display: 'flex' }}>
      <PillButton on={!ai} onClick={() => onChange(false)}>Para pessoas</PillButton>
      <PillButton on={ai} onClick={() => onChange(true)}>Para a IA (CLAUDE.md)</PillButton>
    </div>
  );
}

function Stat({ label, value, note, color }: { label: string; value: string; note: string; color?: string }) {
  return (
    <div style={{ ...card, display: 'grid', gap: 4 }}>
      <span style={muted}>{label}</span>
      <strong style={{ fontSize: '1.75rem', fontWeight: 400, color }}>{value}</strong>
      <span style={muted}>{note}</span>
    </div>
  );
}

export function SummaryCards({ stack }: { stack: GuideStack }) {
  const coverage = coverageOf(stack);
  const grade = gradeOf(criteriaOf(stack));
  const scope = stack.baseLanguage ? 'linguagem base' : `${stack.ownRules} específicas`;
  return (
    <section aria-label="Resumo" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
      <Stat label="Regras que valem aqui" value={String(stack.ownRules + stack.inheritedRules)} note={`${scope} · ${stack.inheritedRules} herdadas`} />
      <Stat label="Cobertura do Sentinel" value={coverage.label} note={stack.summary} color={TONE_COLOR[coverage.tone]} />
      <div style={{ ...card, display: 'grid', gap: 4 }}>
        <span style={muted}>Ferramentas que rodam</span>
        <ul style={{ margin: 0, paddingLeft: '1.1rem', fontSize: '0.875rem' }}>
          {stack.tools.map((t) => <li key={t}>{t}</li>)}
        </ul>
      </div>
      <Stat label="Nota do guia desta tecnologia" value={`${formatGrade(grade)} / 10`} note="Veja o que falta no fim da página" />
    </section>
  );
}

export function PracticeGrid({ stack }: { stack: GuideStack }) {
  return (
    <section aria-labelledby="guia-praticas" style={{ ...card, display: 'grid', gap: 12 }}>
      <h2 id="guia-praticas" style={{ margin: 0, fontSize: '1.25rem', fontWeight: 600 }}>Boas práticas de {stack.name}</h2>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 12 }}>
        {stack.practices.map((p) => <Practice key={p.title} practice={p} />)}
      </div>
      <p style={muted}>
        Estas práticas vêm do arquivo guia/stacks.yml. Confira cada uma na documentação oficial e marque review.docs quando terminar.
      </p>
    </section>
  );
}

function Practice({ practice }: { practice: GuidePractice }) {
  return (
    <article style={{ border: '1px solid var(--s-border)', padding: '0.75rem 1rem', display: 'grid', gap: 6 }}>
      <h3 style={{ margin: 0, fontSize: '0.9375rem', fontWeight: 600 }}>{practice.title}</h3>
      <p style={{ margin: 0, fontSize: '0.875rem' }}><strong style={{ color: 'var(--s-success-text)' }}>Faça</strong> {practice.doIt}</p>
      <p style={{ margin: 0, fontSize: '0.875rem' }}><strong style={{ color: 'var(--s-danger-text)' }}>Evite</strong> {practice.avoid}</p>
    </article>
  );
}

export function GapsNotice({ stack }: { stack: GuideStack }) {
  if (stack.gaps.length === 0) return null;
  return (
    <section aria-labelledby="guia-lacunas" style={{ ...card, background: 'var(--s-warn-bg)' }}>
      <h2 id="guia-lacunas" style={{ margin: '0 0 6px', fontSize: '1rem', fontWeight: 600, color: 'var(--s-warn-text)' }}>
        O que o Sentinel ainda não verifica em {stack.name}
      </h2>
      <ul style={{ margin: 0, paddingLeft: '1.1rem', fontSize: '0.875rem' }}>
        {stack.gaps.map((g) => <li key={g}>{g}</li>)}
      </ul>
    </section>
  );
}

function RuleRow({ rule, open, onToggle }: { rule: GuideRule & { own: boolean }; open: boolean; onToggle: () => void }) {
  return (
    <li style={{ border: '1px solid var(--s-border)' }}>
      <button
        type="button"
        aria-expanded={open}
        onClick={onToggle}
        style={{ all: 'unset', boxSizing: 'border-box', width: '100%', cursor: 'pointer', display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 10, padding: '0.625rem 0.875rem', minHeight: 48 }}
      >
        <Tag type={SEVERITY_TAG[rule.severity]} size="sm">{SEVERITY_LABEL[rule.severity]}</Tag>
        <span style={{ flex: '1 1 260px', fontWeight: 500 }}>{rule.title}</span>
        {rule.own && <Tag type="blue" size="sm">específica</Tag>}
        <code style={{ ...muted, fontFamily: 'var(--cds-code-01, monospace)' }}>{rule.id}</code>
      </button>
      {open && <RuleDetail rule={rule} />}
    </li>
  );
}

function RuleDetail({ rule }: { rule: GuideRule }) {
  return (
    <div style={{ padding: '0 0.875rem 0.875rem', display: 'grid', gap: 8 }}>
      <p style={{ margin: 0, fontSize: '0.875rem' }}><strong>O que fazer.</strong> {rule.action || 'Veja a mensagem da regra no relatório da auditoria.'}</p>
      {rule.before && rule.after && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 8 }}>
          <CodeBlock label="Antes" code={rule.before} />
          <CodeBlock label="Depois" code={rule.after} />
        </div>
      )}
      <span style={muted}>pacote {rule.pack}{rule.cwe ? ` · ${rule.cwe}` : ''}</span>
    </div>
  );
}

function CodeBlock({ label, code }: { label: string; code: string }) {
  return (
    <div>
      <div style={{ ...muted, fontWeight: 600, marginBottom: 2 }}>{label}</div>
      <pre style={{ margin: 0, padding: 10, background: 'var(--s-chrome)', color: '#f4f4f4', fontSize: '0.8125rem', whiteSpace: 'pre-wrap', overflowX: 'auto' }}>{code}</pre>
    </div>
  );
}

const SEVERITIES: SeverityFilter[] = ['todas', 'alta', 'media', 'baixa'];

export function RulesList({ rules }: { rules: Array<GuideRule & { own: boolean }> }) {
  const [severity, setSeverity] = useState<SeverityFilter>('todas');
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState('');
  const [limit, setLimit] = useState(PAGE_SIZE);
  const shown = filterRules(rules, severity, query);
  const count = (s: SeverityFilter) => (s === 'todas' ? rules.length : rules.filter((r) => r.severity === s).length);
  return (
    <section aria-labelledby="guia-regras" style={{ ...card, display: 'grid', gap: 12 }}>
      <h2 id="guia-regras" style={{ margin: 0, fontSize: '1.25rem', fontWeight: 600 }}>Regras ({shown.length} de {rules.length})</h2>
      <Search size="md" labelText="Buscar regra" placeholder="Buscar por palavra, id ou CWE" value={query} onChange={(e) => { setQuery(e.target.value); setLimit(PAGE_SIZE); }} />
      <div role="group" aria-label="Severidade" style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
        {SEVERITIES.map((s) => (
          <PillButton key={s} on={severity === s} onClick={() => { setSeverity(s); setLimit(PAGE_SIZE); }}>
            {s === 'todas' ? 'Todas' : SEVERITY_LABEL[s]} · {count(s)}
          </PillButton>
        ))}
      </div>
      <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'grid', gap: 6 }}>
        {shown.slice(0, limit).map((r) => (
          <RuleRow key={r.id} rule={r} open={open === r.id} onToggle={() => setOpen(open === r.id ? '' : r.id)} />
        ))}
      </ul>
      {shown.length === 0 && <p style={muted}>Nenhuma regra com esse filtro. Limpe a busca ou escolha outra severidade.</p>}
      {shown.length > limit && (
        <div><PillButton on={false} onClick={() => setLimit(limit + PAGE_SIZE)}>Mostrar mais ({shown.length - limit})</PillButton></div>
      )}
    </section>
  );
}

export function GradePanel({ stack }: { stack: GuideStack }) {
  const criteria = criteriaOf(stack);
  return (
    <section aria-labelledby="guia-nota" style={{ ...card, display: 'grid', gap: 12 }}>
      <h2 id="guia-nota" style={{ margin: 0, fontSize: '1.25rem', fontWeight: 600 }}>O que falta para a nota 10 em {stack.name}</h2>
      <p style={{ ...muted, margin: 0 }}>Cada critério vale pontos; só conta o que foi feito de fato.</p>
      <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: 8 }}>
        {criteria.map((c) => (
          <li key={c.title} style={{ border: '1px solid var(--s-border)', padding: '0.625rem 0.875rem', display: 'flex', gap: 10 }}>
            <Tag type={c.done ? 'green' : 'warm-gray'} size="sm">{c.done ? 'Feito' : 'Falta'}</Tag>
            <span style={{ display: 'grid', gap: 2 }}>
              <strong style={{ fontWeight: 500, fontSize: '0.875rem' }}>{c.title} <span style={muted}>({formatGrade(c.points)} pt)</span></strong>
              <span style={muted}>{c.detail}</span>
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}
