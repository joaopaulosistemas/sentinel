import { InlineNotification, SkeletonText } from '@carbon/react';
import { useState } from 'react';
import { useScoreExplanation } from '../hooks/useAuditRuns';
import type { AuditRunResponse, ScoreDimension, ScoreExplanation, ScoreGroup } from '../types';
import { categoryLabels } from '../utils/resultVerdict';
import { deltaLabel, groupHint, toggleRule } from '../utils/scoreExplanation';
import { scoreColor } from '../utils/scoreBand';

const box = { background: 'var(--s-surface)', border: '1px solid var(--s-border)', padding: '1.25rem 1.5rem' } as const;
const mono = "'IBM Plex Mono', ui-monospace, monospace";
const cell = { padding: '6px 8px', textAlign: 'left', verticalAlign: 'top', fontSize: 13 } as const;
const num = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 });

function RemovedChips({ rules, onUndo }: { rules: string[]; onUndo: (rule: string) => void }) {
  if (rules.length === 0) return null;
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center', fontSize: 13 }}>
      <span style={{ color: 'var(--s-text-3)' }}>Simulando sem:</span>
      {rules.map((rule) => (
        <button key={rule} type="button" onClick={() => onUndo(rule)} aria-label={`Voltar a contar a regra ${rule}`}
          style={{ border: '1px solid var(--s-border-strong)', background: 'transparent', color: 'var(--s-text)', padding: '2px 8px', cursor: 'pointer', fontFamily: mono, fontSize: 12 }}>
          {rule} ✕
        </button>
      ))}
    </div>
  );
}

function Summary({ data }: { data: ScoreExplanation }) {
  const simulated = data.removedRules.length > 0;
  const delta = deltaLabel(data.overallBefore, data.overall);
  return (
    <p style={{ margin: 0, fontSize: 14 }}>
      {simulated ? 'Nota real ' : 'Nota '}
      <strong style={{ color: scoreColor(data.overallBefore) }}>{num.format(data.overallBefore)}</strong>
      {simulated && (
        <>
          {' → sem essas regras '}
          <strong style={{ color: scoreColor(data.overall) }}>{num.format(data.overall)}</strong>
          {delta && <span style={{ color: 'var(--s-text-3)' }}> ({delta})</span>}
        </>
      )}
      <span style={{ color: 'var(--s-text-3)' }}>
        {' · curva '}{data.curve}
        {data.sizeFactor < 1 && ` · projeto grande: achados abaixo de crítico contam ${Math.round(data.sizeFactor * 100)}%`}
      </span>
    </p>
  );
}

function GroupRow({ group, onSimulate }: { group: ScoreGroup; onSimulate: (rule: string) => void }) {
  return (
    <tr style={{ borderTop: '1px solid var(--s-border)' }}>
      <td style={{ ...cell, fontFamily: mono, fontSize: 12, overflowWrap: 'anywhere' }}>{group.ruleId}</td>
      <td style={cell}>{group.severity}</td>
      <td style={{ ...cell, fontFamily: mono }}>{group.count}</td>
      <td style={{ ...cell, fontFamily: mono }}>−{num.format(group.counted)}{group.capped && ' (teto)'}</td>
      <td style={{ ...cell, color: 'var(--s-text-3)' }}>{groupHint(group)}</td>
      <td style={cell}>
        <button type="button" onClick={() => onSimulate(group.ruleId)} aria-label={`Simular sem a regra ${group.ruleId}`}
          style={{ border: '1px solid var(--s-border-strong)', background: 'transparent', color: 'var(--s-link)', padding: '2px 8px', cursor: 'pointer' }}>
          E se…
        </button>
      </td>
    </tr>
  );
}

function DimensionBlock({ dimension, onSimulate }: { dimension: ScoreDimension; onSimulate: (rule: string) => void }) {
  return (
    <section aria-label={categoryLabels[dimension.category]} style={{ display: 'grid', gap: 6 }}>
      <h3 style={{ margin: 0, fontSize: 14, fontWeight: 600 }}>
        {categoryLabels[dimension.category]}{' '}
        <span style={{ color: scoreColor(dimension.score) }}>{num.format(dimension.score)}</span>
        <span style={{ color: 'var(--s-text-3)', fontWeight: 400 }}> · peso {Math.round(dimension.weight * 100)}% · penalidade {num.format(dimension.penalty)}</span>
      </h3>
      {dimension.groups.length === 0 ? (
        <p style={{ margin: 0, fontSize: 13, color: 'var(--s-text-3)' }}>Nenhuma regra tirou pontos aqui.</p>
      ) : (
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr>
                {['Regra', 'Severidade', 'Ocorrências', 'Tirou', 'Como conta', ''].map((h) => (
                  <th key={h || 'acao'} style={{ ...cell, fontWeight: 600, color: 'var(--s-text-3)' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {dimension.groups.map((g) => <GroupRow key={`${g.ruleId}|${g.severity}`} group={g} onSimulate={onSimulate} />)}
            </tbody>
          </table>
        </div>
      )}
      {dimension.hidden > 0 && <p style={{ margin: 0, fontSize: 12, color: 'var(--s-text-3)' }}>+ {dimension.hidden} regras menores</p>}
    </section>
  );
}

function Body({ data, onSimulate }: { data: ScoreExplanation; onSimulate: (rule: string) => void }) {
  return (
    <div style={{ display: 'grid', gap: '1.25rem' }}>
      <Summary data={data} />
      <RemovedChips rules={data.removedRules} onUndo={onSimulate} />
      {data.dimensions.map((d) => <DimensionBlock key={d.category} dimension={d} onSimulate={onSimulate} />)}
    </div>
  );
}

/** "Por que esta nota?": quanto cada regra tirou em cada dimensão, onde o teto cortou e a simulação "e se". */
export function ScoreExplanationPanel({ run }: { run: AuditRunResponse }) {
  const [open, setOpen] = useState(false);
  const [without, setWithout] = useState<string[]>([]);
  const { data, isLoading, error } = useScoreExplanation(run.id, open && run.status === 'COMPLETED', without);
  const simulate = (rule: string) => setWithout((current) => toggleRule(current, rule));

  return (
    <section aria-label="Por que esta nota" style={box}>
      <button type="button" aria-expanded={open} onClick={() => setOpen((o) => !o)}
        style={{ background: 'none', border: 0, padding: 0, color: 'var(--s-text)', fontSize: 16, fontWeight: 600, cursor: 'pointer' }}>
        {open ? '▾' : '▸'} Por que esta nota?
      </button>
      {open && (
        <div style={{ marginTop: '1rem' }}>
          {isLoading && <SkeletonText paragraph lineCount={4} />}
          {error && <InlineNotification kind="error" title="Não foi possível explicar a nota" subtitle={error instanceof Error ? error.message : 'Erro desconhecido'} hideCloseButton />}
          {data && <Body data={data} onSimulate={simulate} />}
        </div>
      )}
    </section>
  );
}
