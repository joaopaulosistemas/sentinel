import { InlineNotification, SkeletonText, Tag } from '@carbon/react';
import { useState } from 'react';
import { useInsights } from '../hooks/useLifecycle';
import type { AreaState, DebtItem, Severity } from '../types';
import { PageSection } from './PageSection';

type TagType = 'red' | 'magenta' | 'blue' | 'green' | 'gray' | 'warm-gray';

const stateConfig: Record<AreaState, { label: string; type: TagType; color: string }> = {
  CRITICO: { label: 'Crítico', type: 'red', color: 'var(--s-danger-text)' },
  ATENCAO: { label: 'Atenção', type: 'magenta', color: 'var(--s-warn-text)' },
  SEM_ACHADOS: { label: 'Sem achados', type: 'green', color: 'var(--s-success-text)' },
  NAO_VERIFICADO: { label: 'Não verificado', type: 'gray', color: 'var(--s-text-4)' },
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

const th: React.CSSProperties = { textAlign: 'left', padding: '8px 16px 8px 0', fontSize: '0.75rem', fontWeight: 600, color: 'var(--s-text-3)', borderBottom: '1px solid var(--s-border)' };
const td: React.CSSProperties = { padding: '16px 16px 16px 0', fontSize: '0.875rem', verticalAlign: 'top', borderBottom: '1px solid var(--s-border)' };

function SubTitle({ title, hint }: { title: string; hint?: string }) {
  return (
    <div style={{ margin: '28px 0 12px' }}>
      <h3 style={{ margin: 0, fontSize: 16, fontWeight: 600 }}>{title}</h3>
      {hint && <p style={{ fontSize: 13, color: 'var(--s-text-3)', margin: '2px 0 0' }}>{hint}</p>}
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
                <div style={{ fontSize: '0.75rem', color: 'var(--s-text-3)' }}>
                  {d.areaLabel} · {severityLabel[d.severity]}
                  {d.example ? ` · ${d.example}` : ''}
                </div>
                <div style={{ fontSize: '0.8125rem', color: 'var(--s-text-2)', marginTop: 6 }}>{d.recommendation}</div>
              </td>
              <td style={td}>{d.impact}</td>
              <td style={{ ...td, whiteSpace: 'nowrap' }}>
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
        <p style={{ fontSize: '0.75rem', color: 'var(--s-text-3)', marginTop: '0.5rem' }}>
          Mostrando {limit} de {items.length} problemas.
        </p>
      )}
    </div>
  );
}

/** Parecer da auditoria: veredito, resumo e o estado de cada área. */
function VerdictBlock({ data }: { data: NonNullable<ReturnType<typeof useInsights>['data']> }) {
  return (
    <>
      <p style={{ margin: 0, fontSize: 20, fontWeight: 300, maxWidth: 760 }}>{data.verdict}</p>
      <p style={{ margin: '10px 0 24px', color: 'var(--s-text-2)', maxWidth: 760 }}>{data.executiveSummary}</p>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(min(320px, 100%), 1fr))', gap: '28px 40px' }}>
        {data.areas.map((a) => (
          <div key={a.area} style={{ borderLeft: `4px solid ${stateConfig[a.state].color}`, paddingLeft: 16 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
              <strong style={{ fontSize: 15, fontWeight: 600 }}>{a.label}</strong>
              <Tag type={stateConfig[a.state].type} size="sm">
                {stateConfig[a.state].label}
              </Tag>
            </div>
            <p style={{ fontSize: 13, color: 'var(--s-text-2)', margin: '4px 0 0' }}>{a.currentState}</p>
            {a.risks.length > 0 && (
              <ul style={{ fontSize: 12, color: 'var(--s-text-3)', margin: '6px 0 0', paddingLeft: 16 }}>
                {a.risks.map((r) => (
                  <li key={r}>{r}</li>
                ))}
              </ul>
            )}
          </div>
        ))}
      </div>
    </>
  );
}

/** Parecer da auditoria e, em seguida, o que corrigir: quick wins, dívida técnica P0–P3 e plano em fases. */
export function InsightsPanel({ auditId }: { auditId: number | undefined }) {
  const { data, isLoading, error } = useInsights(auditId);
  const [showAll, setShowAll] = useState(false);

  if (auditId === undefined) return null;

  return (
    <>
      <PageSection id="parecer" title="Parecer da auditoria">
        {isLoading && <SkeletonText paragraph lineCount={4} />}
        {error && (
          <InlineNotification
            kind="error"
            title="Não foi possível carregar a auditoria completa"
            subtitle={error instanceof Error ? error.message : 'Erro desconhecido'}
            hideCloseButton
          />
        )}
        {data && <VerdictBlock data={data} />}
      </PageSection>

      {data && (
        <PageSection id="plano" title="O que corrigir" hint="Quick wins primeiro; depois a dívida técnica, um problema por linha, agrupado por regra.">
          {data.quickWins.length > 0 && (
            <>
              <SubTitle title="Quick wins" hint="Esforço pequeno e prioridade até P2: comece por aqui." />
              <DebtTable items={data.quickWins} />
            </>
          )}

          <SubTitle title="Dívida técnica" />
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

          <SubTitle title="Plano de ação em fases" />
          <ol style={{ listStyle: 'none', margin: 0, padding: 0, display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(220px, 100%), 1fr))', gap: 24 }}>
            {data.roadmap.map((p) => (
              <li key={p.number} style={{ borderTop: '4px solid var(--s-link)', paddingTop: 10 }}>
                <div style={{ fontSize: 12, color: 'var(--s-text-3)' }}>
                  Fase {p.number} · {p.total} {p.total === 1 ? 'item' : 'itens'}
                </div>
                <strong style={{ fontWeight: 600 }}>{p.name}</strong>
                <div style={{ fontSize: 13, color: 'var(--s-text-2)' }}>{p.goal}</div>
              </li>
            ))}
          </ol>
        </PageSection>
      )}
    </>
  );
}

/** O que a análise não alcança e o mapa do projeto; fica no fim da página, depois da evolução da nota. */
export function InsightsLimits({ auditId }: { auditId: number | undefined }) {
  const { data } = useInsights(auditId);
  if (auditId === undefined || !data) return null;
  const map = data.projectMap;
  return (
    <PageSection id="limites" title="Limites e mapa do projeto">
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(320px, 100%), 1fr))', gap: 40 }}>
        <div>
          <h3 style={{ margin: '0 0 4px', fontSize: 16, fontWeight: 600 }}>Não foi possível verificar</h3>
          <p style={{ margin: '0 0 12px', color: 'var(--s-text-3)', fontSize: 13 }}>Análise estática não alcança estes pontos. Não os trate como aprovados.</p>
          <ul style={{ margin: 0, paddingLeft: 18, color: 'var(--s-text-2)', fontSize: 13 }}>
            {data.unverified.map((u) => (
              <li key={u}>{u}</li>
            ))}
          </ul>
        </div>
        {map && (
          <div>
            <h3 style={{ margin: '0 0 12px', fontSize: 16, fontWeight: 600 }}>Mapa do projeto</h3>
            <p style={{ fontSize: 13, margin: 0 }}>
              {Object.entries(map.languages)
                .map(([l, n]) => `${l} (${n})`)
                .join(', ') || 'Nenhuma linguagem reconhecida'}
              {' · '}
              {map.sourceFiles} arquivos de código, {map.testFiles} de teste
              {map.frameworks.length > 0 ? ` · ${map.frameworks.join(', ')}` : ''}
              {map.buildTools.length > 0 ? ` · build: ${map.buildTools.join(', ')}` : ''}
            </p>
          </div>
        )}
      </div>
    </PageSection>
  );
}
