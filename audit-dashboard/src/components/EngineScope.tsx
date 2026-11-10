import { Button, InlineNotification, Toggle } from '@carbon/react';
import { useState } from 'react';
import type { IEngineConfig, EngineSide } from '../types';
import { SIDE_NAME, packsBySide, presetOf, sideTotals, sidesOffOf, type ScopePreset } from '../utils/ruleFamilies';

type OffSide = 'back' | 'front';

const PRESETS: Array<{ key: ScopePreset; label: string }> = [
  { key: 'full', label: 'Full-stack' },
  { key: 'back', label: 'Só back-end' },
  { key: 'front', label: 'Só front-end' },
];

const SIDE_TEXT: Record<OffSide, { badge: string; about: string }> = {
  back: { badge: 'BACK', about: 'Java, Quarkus, Spring, JPA, SQL, APIs e Node/Express.' },
  front: { badge: 'FRONT', about: 'Angular, React, Vue, TypeScript, HTML, CSS/SCSS e acessibilidade.' },
};

const BADGE_COLORS: Record<EngineSide, { bg: string; fg: string }> = {
  back: { bg: '#e6eefb', fg: '#0f3f99' },
  front: { bg: '#f1e8ff', fg: '#491d8b' },
  common: { bg: '#e3e6ea', fg: '#262b33' },
};

/** Etiqueta do lado (texto + cor, para não depender só da cor). */
export function SideBadge({ side }: { side: EngineSide }) {
  const colors = BADGE_COLORS[side];
  const text = side === 'back' ? 'BACK' : side === 'front' ? 'FRONT' : 'COMUM';
  return <span style={{ padding: '2px 8px', background: colors.bg, color: colors.fg, fontSize: 11, fontWeight: 600 }}>{text}</span>;
}

interface ICardProps {
  side: OffSide;
  on: boolean;
  rules: number;
  packs: number;
  analyzers: number;
  onToggle: (on: boolean) => void;
}

function SideCard({ side, on, rules, packs, analyzers, onToggle }: ICardProps) {
  const name = SIDE_NAME[side];
  return (
    <div
      role="group"
      aria-label={`Escopo ${name}`}
      style={{ padding: 16, display: 'grid', gap: 10, border: `1px solid ${on ? '#0f62fe' : 'var(--s-border-subtle)'}`, background: on ? 'var(--s-surface)' : 'var(--s-layer)', opacity: on ? 1 : 0.85 }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
        <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <SideBadge side={side} />
          <strong style={{ fontSize: 15 }}>{name}</strong>
        </span>
        <Toggle id={`scope-${side}`} size="sm" hideLabel labelText={`Auditar o ${name.toLowerCase()}`} labelA="" labelB="" toggled={on} onToggle={onToggle} />
      </div>
      <div style={{ fontSize: 13, color: 'var(--s-text-2)' }}>{SIDE_TEXT[side].about}</div>
      <div style={{ display: 'flex', gap: 16, fontSize: 13, flexWrap: 'wrap' }}>
        <span><strong>{rules}</strong> regras</span>
        <span><strong>{packs}</strong> pacotes</span>
        <span><strong>{analyzers}</strong> {analyzers === 1 ? 'analisador' : 'analisadores'}</span>
      </div>
      <div style={{ fontSize: 12, fontWeight: 600, color: on ? 'var(--s-success-text)' : 'var(--s-warn-text)' }}>
        {on ? 'Ligado: entra na próxima auditoria' : 'Desligado: fora da próxima auditoria'}
      </div>
    </div>
  );
}

interface ICommonCardProps {
  rules: number;
  packs: number;
  analyzers: number;
}

/** Infra e comum nunca desliga: o cartão só mostra quanto vale para qualquer linguagem. */
function CommonCard({ rules, packs, analyzers }: ICommonCardProps) {
  return (
    <div role="group" aria-label="Escopo Infra e comum" style={{ padding: 16, display: 'grid', gap: 10, border: '1px solid var(--s-border)', background: 'var(--s-header-row)' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
        <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}><SideBadge side="common" /><strong style={{ fontSize: 15 }}>Infra e comum</strong></span>
        <span style={{ fontSize: 12, color: 'var(--s-text-3)' }}>sempre ligado</span>
      </div>
      <div style={{ fontSize: 13, color: 'var(--s-text-2)' }}>Docker, Kubernetes e as regras que valem para qualquer linguagem. Segredos, dependências e Git são analisadores comuns.</div>
      <div style={{ display: 'flex', gap: 16, fontSize: 13, flexWrap: 'wrap' }}>
        <span><strong>{rules}</strong> regras</span>
        <span><strong>{packs}</strong> pacotes</span>
        <span><strong>{analyzers}</strong> analisadores</span>
      </div>
    </div>
  );
}

type ScopeSide = OffSide | 'common';

interface IProps {
  cfg: IEngineConfig;
  sidesOff: string[];
  onChange: (next: OffSide[]) => void;
}

/** Escopo da auditoria: liga ou desliga o back-end ou o front-end do sistema de uma vez (regras, pacotes e analisadores do lado). */
export function ScopeSection({ cfg, sidesOff, onChange }: IProps) {
  const [refused, setRefused] = useState(false);
  const totals = sideTotals(cfg.catalog);
  const groups = packsBySide(cfg.packs, cfg.catalog);
  const packsOf = (side: ScopeSide) => groups.find((g) => g.side === side)?.packs.length ?? 0;
  const analyzersOf = (side: ScopeSide) => cfg.analyzers.filter((a) => (a.side ?? 'common') === side).length;
  const preset = presetOf(sidesOff);
  const apply = (next: OffSide[]) => {
    setRefused(false);
    onChange(next);
  };
  const turnSideOn = (side: OffSide) => apply(sidesOff.filter((s) => s !== side) as OffSide[]);
  const turnSideOff = (side: OffSide) => {
    const other: OffSide = side === 'back' ? 'front' : 'back';
    if (sidesOff.includes(other)) {
      setRefused(true);
      return;
    }
    apply([...sidesOff.filter((s) => s !== side), side] as OffSide[]);
  };
  const off = (['back', 'front'] as const).filter((s) => sidesOff.includes(s));

  return (
    <section aria-label="Escopo da auditoria" style={{ display: 'grid', gap: 12 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', gap: 12, flexWrap: 'wrap' }}>
        <div>
          <h3 style={{ margin: 0, fontSize: 16, fontWeight: 600 }}>Escopo da auditoria</h3>
          <p style={{ margin: '4px 0 0', fontSize: 13, color: 'var(--s-text-3)', maxWidth: 760 }}>
            Desligar um lado tira de uma vez as regras, os analisadores e os achados de arquivo dele. Serve para um projeto só de Java (sem front) ou só de interface (sem back).
            Infra e comum (Docker, Kubernetes, segredos, dependências) continua ligado.
          </p>
        </div>
        <div role="group" aria-label="Atalhos de escopo" style={{ display: 'flex', gap: 0 }}>
          {PRESETS.map((p) => (
            <Button key={p.key} size="sm" kind={preset === p.key ? 'primary' : 'tertiary'} aria-pressed={preset === p.key} onClick={() => apply(sidesOffOf(p.key))}>
              {p.label}
            </Button>
          ))}
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 12 }}>
        <SideCard side="back" on={!sidesOff.includes('back')} rules={totals.back} packs={packsOf('back')} analyzers={analyzersOf('back')} onToggle={(on) => (on ? turnSideOn('back') : turnSideOff('back'))} />
        <SideCard side="front" on={!sidesOff.includes('front')} rules={totals.front} packs={packsOf('front')} analyzers={analyzersOf('front')} onToggle={(on) => (on ? turnSideOn('front') : turnSideOff('front'))} />
        <CommonCard rules={totals.common} packs={packsOf('common')} analyzers={analyzersOf('common')} />
      </div>

      {refused && (
        <InlineNotification
          kind="error"
          role="alert"
          title="Não dá para desligar os dois lados"
          subtitle="Sobraria só o que é comum. Ligue um lado ou desligue o sistema."
          hideCloseButton
          lowContrast
        />
      )}
      {off.map((side) => (
        <div key={side} role="status" style={{ padding: '10px 14px', background: 'var(--s-warn-bg)', borderLeft: '3px solid var(--s-warn-text)', fontSize: 13 }}>
          {SIDE_NAME[side]} desligado: {totals[side]} regras e {analyzersOf(side)} {analyzersOf(side) === 1 ? 'analisador' : 'analisadores'} não rodam. O relatório mostra
          “{SIDE_NAME[side]}: não verificado (desligado pelo escopo)”, para ninguém ler a nota como se esse lado estivesse limpo.
        </div>
      ))}
    </section>
  );
}
