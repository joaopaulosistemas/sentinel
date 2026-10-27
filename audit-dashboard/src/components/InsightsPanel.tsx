import { InlineNotification, SkeletonText, Tag, Tile } from '@carbon/react';
import { useState } from 'react';
import { useInsights } from '../hooks/useLifecycle';
import type { AreaState, DebtItem, Severity } from '../types';

type TagType = 'red' | 'magenta' | 'blue' | 'green' | 'gray' | 'warm-gray';

const stateConfig: Record<AreaState, { label: string; type: TagType }> = {
  CRITICO: { label: 'Crítico', type: 'red' },
  ATENCAO: { label: 'Atenção', type: 'magenta' },
  SEM_ACHADOS: { label: 'Sem achados', type: 'green' },
  NAO_VERIFICADO: { label: 'Não verificado', type: 'gray' },
};

const priorityType: Record<DebtItem['priority'], TagType> = {
  P0: 'red',
  P1: 'magenta',
  P2: 'blue',
  P3: 'warm-gray',
};

const severityLabel: Record<Severity, string> = {
  CRITICAL: 'Crítica',
  HIGH: 'Alta',
  MEDIUM: 'Média',
  LOW: 'Baixa',
  INFO: 'Info',
};

const th: React.CSSProperties = { textAlign: 'left', padding: '0.5rem', fontSize: '0.75rem', color: 'var(--s-text-3)' };
const td: React.CSSProperties = { padding: '0.5rem', fontSize: '0.875rem', verticalAlign: 'top', borderTop: '1px solid var(--s-border)' };

function Section({ title, hint, children }: { title: string; hint?: string; children: React.ReactNode }) {
  return (
    <div style={{ marginTop: '1.5rem' }}>
      <h4 style={{ fontSize: '0.9375rem', fontWeight: 600, marginBottom: '0.25rem' }}>{title}</h4>
      {hint && <p style={{ fontSize: '0.8125rem', color: 'var(--s-text-4)', marginBottom: '0.5rem' }}>{hint}</p>}
      {children}
    </div>
  );
}

function DebtTable({ items, limit }: { items: DebtItem[]; limit?: number }) {
  const shown = limit ? items.slice(0, limit) : items;
  return (
    <div style={{ overflowX: 'auto' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse' }}>
        <thead>
          <tr>
            <th style={th}>Prioridade</th>
            <th style={th}>Problema</th>
            <th style={th}>Impacto</th>
            <th style={th}>Esforço</th>
            <th style={th}>Risco de regressão</th>
            <th style={th}>Ocorrências</th>
          </tr>
        </thead>
        <tbody>
          {shown.map((d) => (
            <tr key={d.id}>
              <td style={td}>
                <Tag type={priorityType[d.priority]} size="sm">
                  {d.priority} {d.priorityLabel}
                </Tag>
              </td>
              <td style={td}>
                <strong>{d.problem}</strong>
                <div style={{ fontSize: '0.75rem', color: 'var(--s-text-4)' }}>
                  {d.areaLabel} · {severityLabel[d.severity]}
                  {d.example ? ` · ${d.example}` : ''}
                </div>
                <div style={{ fontSize: '0.8125rem', marginTop: '0.25rem' }}>{d.recommendation}</div>
              </td>
              <td style={td}>{d.impact}</td>
              <td style={td}>
                {d.effort}
                {d.effortEstimated ? ' (estimado)' : ''}
              </td>
              <td style={td}>{d.regressionRisk ?? '—'}</td>
              <td style={td}>{d.occurrences}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {limit && items.length > limit && (
        <p style={{ fontSize: '0.75rem', color: 'var(--s-text-4)', marginTop: '0.5rem' }}>
          Mostrando {limit} de {items.length} problemas.
        </p>
      )}
    </div>
  );
}

/** Auditoria completa de uma execução: parecer, áreas, dívida técnica P0–P3, quick wins, plano em fases e limites. */
export function InsightsPanel({ auditId }: { auditId: number | undefined }) {
  const { data, isLoading, error } = useInsights(auditId);
  const [showAll, setShowAll] = useState(false);

  if (auditId === undefined) return null;

  return (
    <Tile style={{ marginBottom: '2rem', padding: '1.25rem' }}>
      <h3 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '0.25rem' }}>Auditoria completa</h3>
      {isLoading && <SkeletonText paragraph lineCount={4} />}
      {error && (
        <InlineNotification
          kind="error"
          title="Não foi possível carregar a auditoria completa"
          subtitle={error instanceof Error ? error.message : 'Erro desconhecido'}
          hideCloseButton
        />
      )}
      {data && (
        <>
          <p style={{ fontWeight: 600, marginTop: '0.5rem' }}>{data.verdict}</p>
          <p style={{ fontSize: '0.875rem', color: 'var(--s-text-3)' }}>{data.executiveSummary}</p>

          <Section title="Por área">
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(min(260px, 100%), 1fr))', gap: '0.75rem' }}>
              {data.areas.map((a) => (
                <div key={a.area} style={{ background: 'var(--s-layer)', padding: '0.75rem', borderRadius: 4 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '0.5rem' }}>
                    <strong style={{ fontSize: '0.875rem' }}>{a.label}</strong>
                    <Tag type={stateConfig[a.state].type} size="sm">
                      {stateConfig[a.state].label}
                    </Tag>
                  </div>
                  <p style={{ fontSize: '0.8125rem', color: 'var(--s-text-3)', marginTop: '0.25rem' }}>{a.currentState}</p>
                  {a.risks.length > 0 && (
                    <ul style={{ fontSize: '0.75rem', marginTop: '0.25rem', paddingLeft: '1rem' }}>
                      {a.risks.map((r) => (
                        <li key={r}>{r}</li>
                      ))}
                    </ul>
                  )}
                </div>
              ))}
            </div>
          </Section>

          {data.quickWins.length > 0 && (
            <Section title="Quick wins" hint="Esforço pequeno e prioridade até P2: comece por aqui.">
              <DebtTable items={data.quickWins} />
            </Section>
          )}

          <Section title="Dívida técnica" hint="Um problema por linha, agrupado por regra.">
            <DebtTable items={data.debtTable} limit={showAll ? undefined : 15} />
            {data.debtTable.length > 15 && (
              <button
                type="button"
                onClick={() => setShowAll((v) => !v)}
                style={{ marginTop: '0.5rem', background: 'none', border: 'none', color: 'var(--s-link)', cursor: 'pointer', padding: 0 }}
              >
                {showAll ? 'Mostrar menos' : 'Mostrar todos'}
              </button>
            )}
          </Section>

          <Section title="Plano de ação">
            <ol style={{ paddingLeft: '1.25rem', fontSize: '0.875rem' }}>
              {data.roadmap.map((p) => (
                <li key={p.number} style={{ marginBottom: '0.25rem' }}>
                  <strong>
                    Fase {p.number} — {p.name}
                  </strong>{' '}
                  ({p.total} {p.total === 1 ? 'item' : 'itens'}): {p.goal}
                </li>
              ))}
            </ol>
          </Section>

          <Section title="Não foi possível verificar" hint="Análise estática não alcança estes pontos. Não os trate como aprovados.">
            <ul style={{ paddingLeft: '1.25rem', fontSize: '0.8125rem' }}>
              {data.unverified.map((u) => (
                <li key={u}>{u}</li>
              ))}
            </ul>
          </Section>

          {data.projectMap && (
            <Section title="Mapa do projeto">
              <p style={{ fontSize: '0.8125rem' }}>
                {Object.entries(data.projectMap.languages)
                  .map(([l, n]) => `${l} (${n})`)
                  .join(', ') || 'Nenhuma linguagem reconhecida'}
                {' · '}
                {data.projectMap.sourceFiles} arquivos de código, {data.projectMap.testFiles} de teste
                {data.projectMap.frameworks.length > 0 ? ` · ${data.projectMap.frameworks.join(', ')}` : ''}
                {data.projectMap.buildTools.length > 0 ? ` · build: ${data.projectMap.buildTools.join(', ')}` : ''}
              </p>
            </Section>
          )}
        </>
      )}
    </Tile>
  );
}
