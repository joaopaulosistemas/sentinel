import {
  DataTableSkeleton,
  InlineNotification,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  Tag,
  Tile,
} from '@carbon/react';
import { PageShell } from '../../components/PageShell';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useRuleQuality, useSystems } from '../../hooks/useSystems';
import type { SystemSummaryResponse } from '../../types';
import { formatTrend, summarizeTeams } from '../../utils/ranking';
import { TARGET_SCORE as TARGET, scoreColor as barColor, scoreTagType } from '../../utils/scoreBand';
import { timeAgo } from '../../utils/timeAgo';

export type SortKey = 'nota' | 'criticos' | 'tendencia';

const sortOptions: Array<{ key: SortKey; label: string }> = [
  { key: 'nota', label: 'Nota' },
  { key: 'criticos', label: 'Críticos' },
  { key: 'tendencia', label: 'Tendência' },
];

const trendColor = { up: 'var(--s-success-text)', down: 'var(--s-danger-text)', flat: 'var(--s-text-3)', none: 'var(--s-text-4)' } as const;


type Cmp = (a: SystemSummaryResponse, b: SystemSummaryResponse) => number;

const byName: Cmp = (a, b) => a.name.localeCompare(b.name);
const byScore: Cmp = (a, b) => (b.lastAuditScore ?? -1) - (a.lastAuditScore ?? -1);
const byCritical: Cmp = (a, b) => (b.openCritical ?? 0) - (a.openCritical ?? 0);
const byTrend: Cmp = (a, b) => (b.scoreDelta ?? -Infinity) - (a.scoreDelta ?? -Infinity);

const sortChains: Record<SortKey, Cmp[]> = {
  tendencia: [byTrend, byName],
  nota: [byScore, byName],
  criticos: [byCritical, byScore, byName],
};

/** Ordem da lista: melhor nota primeiro, mais críticos primeiro, ou maior ganho de nota primeiro. Sem dado vai para o fim. */
export function sortSystems(systems: SystemSummaryResponse[], by: SortKey): SystemSummaryResponse[] {
  const chain = sortChains[by];
  return [...systems].sort((a, b) => {
    for (const cmp of chain) {
      const d = cmp(a, b);
      if (d !== 0) return d;
    }
    return 0;
  });
}

function scoreTag(score: number | null | undefined) {
  if (score == null) return <Tag type="gray">Nunca auditado</Tag>;
  return <Tag type={scoreTagType(score)}>{Math.round(score)}</Tag>;
}

const GRID_COLUMNS = '56px minmax(180px, 2fr) minmax(220px, 3fr) 90px 110px 130px';

function SortToggle({ sortBy, onChange }: { sortBy: SortKey; onChange: (k: SortKey) => void }) {
  return (
    <div role="group" aria-label="Ordenar por" style={{ display: 'flex' }}>
      {sortOptions.map((o) => {
        const active = sortBy === o.key;
        return (
          <button
            key={o.key}
            type="button"
            aria-pressed={active}
            onClick={() => onChange(o.key)}
            style={{
              height: 36,
              padding: '0 1rem',
              marginLeft: -1,
              border: '1px solid var(--s-border-strong)',
              background: active ? 'var(--s-text)' : 'var(--s-surface)',
              color: active ? 'var(--s-surface)' : 'var(--s-text)',
              cursor: 'pointer',
              font: 'inherit',
              fontSize: '0.8125rem',
            }}
          >
            {o.label}
          </button>
        );
      })}
    </div>
  );
}

function ScoreBar({ score }: { score: number | null | undefined }) {
  if (score == null) return <span><Tag type="gray">Nunca auditado</Tag></span>;
  return (
    <span style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
      <span style={{ position: 'relative', flex: 1, height: 10, background: 'var(--s-border)' }}>
        <span style={{ position: 'absolute', left: 0, top: 0, height: 10, width: `${Math.max(0, Math.min(100, score))}%`, background: barColor(score) }} />
        <span style={{ position: 'absolute', left: `${TARGET}%`, top: -4, width: 2, height: 18, background: 'var(--s-text)' }} />
      </span>
      <span style={{ width: 32, fontWeight: 600, color: barColor(score) }}>{Math.round(score)}</span>
    </span>
  );
}

function RankingRow({ system: s, position, onOpen }: { system: SystemSummaryResponse; position: number; onOpen: () => void }) {
  const trend = formatTrend(s.scoreDelta);
  const critical = s.openCritical ?? 0;
  return (
    <button
      type="button"
      className="sentinel-linha-hover"
      onClick={onOpen}
      style={{
        all: 'unset',
        boxSizing: 'border-box',
        width: '100%',
        cursor: 'pointer',
        display: 'grid',
        gridTemplateColumns: GRID_COLUMNS,
        gap: '0.75rem',
        alignItems: 'center',
        padding: '0.875rem 1rem',
        borderTop: '1px solid var(--s-border)',
      }}
    >
      <span style={{ fontSize: '1.25rem', fontWeight: 300 }}>{position}</span>
      <span style={{ display: 'grid' }}>
        <strong style={{ fontWeight: 600 }}>{s.name}</strong>
        <span style={{ fontSize: '0.75rem', color: 'var(--s-text-3)' }}>{s.team || 'Sem time'}</span>
      </span>
      <ScoreBar score={s.lastAuditScore} />
      <span style={{ fontWeight: 500, color: trendColor[trend.tone], fontSize: '0.8125rem' }}>{trend.text}</span>
      <span style={{ fontWeight: 600, color: critical > 0 ? 'var(--s-danger-text)' : 'var(--s-success-text)' }}>{critical}</span>
      <span style={{ fontSize: '0.8125rem', color: 'var(--s-text-3)' }}>{timeAgo(s.lastAuditDate)}</span>
    </button>
  );
}

function RankingList({ ranking, onOpen }: { ranking: SystemSummaryResponse[]; onOpen: (id: number) => void }) {
  return (
    <section aria-label="Sistemas por risco" style={{ background: 'var(--s-surface)', border: '1px solid var(--s-border)' }}>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: GRID_COLUMNS,
          gap: '0.75rem',
          padding: '0.75rem 1rem',
          background: 'var(--s-header-row)',
          fontSize: '0.75rem',
          fontWeight: 600,
        }}
      >
        <span>Posição</span>
        <span>Sistema</span>
        <span>Nota (a linha marca a meta {TARGET})</span>
        <span>Tendência</span>
        <span>Críticos</span>
        <span>Última auditoria</span>
      </div>
      {ranking.map((s, i) => (
        <RankingRow key={s.id} system={s} position={i + 1} onOpen={() => onOpen(s.id)} />
      ))}
    </section>
  );
}

function TeamsTable({ teams }: { teams: ReturnType<typeof summarizeTeams> }) {
  return (
    <Tile style={{ padding: '1.25rem' }}>
      <h3 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '0.75rem' }}>Por time</h3>
      <Table size="md">
        <TableHead>
          <TableRow>
            <TableHeader>Time</TableHeader>
            <TableHeader>Sistemas</TableHeader>
            <TableHeader>Auditados</TableHeader>
            <TableHeader>Score médio</TableHeader>
            <TableHeader>Críticos</TableHeader>
            <TableHeader>Altos</TableHeader>
            <TableHeader>Prioridade</TableHeader>
          </TableRow>
        </TableHead>
        <TableBody>
          {teams.map((t) => (
            <TableRow key={t.team}>
              <TableCell>{t.team}</TableCell>
              <TableCell>{t.systems}</TableCell>
              <TableCell>{t.audited}</TableCell>
              <TableCell>{scoreTag(t.avgScore)}</TableCell>
              <TableCell>{t.critical}</TableCell>
              <TableCell>{t.high}</TableCell>
              <TableCell>{t.worst?.name ?? '—'}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Tile>
  );
}

type RuleQualityRows = NonNullable<ReturnType<typeof useRuleQuality>['data']>;

function RulesTable({ rules }: { rules: RuleQualityRows }) {
  return (
    <Table size="md">
      <TableHead>
        <TableRow>
          <TableHeader>Regra</TableHeader>
          <TableHeader>Ferramenta</TableHeader>
          <TableHeader>Ocorrências</TableHeader>
          <TableHeader>Falso positivo</TableHeader>
          <TableHeader>Risco aceito</TableHeader>
          <TableHeader>Situação</TableHeader>
        </TableRow>
      </TableHead>
      <TableBody>
        {rules.slice(0, 15).map((r) => (
          <TableRow key={r.ruleId}>
            <TableCell>{r.ruleId}</TableCell>
            <TableCell>{r.tool ?? '—'}</TableCell>
            <TableCell>{r.total}</TableCell>
            <TableCell>
              {r.falsePositive} ({Math.round(r.falsePositiveRate * 100)}%)
            </TableCell>
            <TableCell>{r.acceptedRisk}</TableCell>
            <TableCell>
              {r.needsReview ? <Tag type="red">Revisar</Tag> : <Tag type="gray">OK</Tag>}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function RuleQualityTile({ rules }: { rules: RuleQualityRows | undefined }) {
  return (
    <Tile style={{ padding: '1.25rem' }}>
      <h3 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '0.25rem' }}>Qualidade das regras</h3>
      <p style={{ fontSize: '0.875rem', color: 'var(--s-text-3)', marginBottom: '0.75rem' }}>
        Taxa de falso positivo, a partir do que o time suprimiu. Só pede revisão com 5+ ocorrências e 40%+ de falsos positivos.
      </p>
      {!rules || rules.length === 0 ? (
        <p style={{ color: 'var(--s-text-3)' }}>Ainda não há findings para avaliar.</p>
      ) : (
        <RulesTable rules={rules} />
      )}
    </Tile>
  );
}

/** Visão consolidada: onde olhar primeiro (sistemas) e como cada time está (times). */
export function RankingPage() {
  const navigate = useNavigate();
  const [sortBy, setSortBy] = useState<SortKey>('nota');
  const { data, isLoading, error } = useSystems();
  const { data: rules } = useRuleQuality();

  if (isLoading) return <DataTableSkeleton columnCount={5} rowCount={5} />;
  if (error) {
    return (
      <InlineNotification
        kind="error"
        title="Erro ao carregar sistemas"
        subtitle={error instanceof Error ? error.message : 'Erro desconhecido'}
      />
    );
  }

  const systems = data ?? [];
  return (
    <PageShell crumbs={[{ label: 'Início', to: '/' }, { label: 'Ranking de Sistemas' }]}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', gap: '1rem', flexWrap: 'wrap' }}>
        <div>
          <h1 style={{ margin: '0 0 0.25rem', fontSize: 26, fontWeight: 400 }}>Ranking de Sistemas</h1>
          <p style={{ color: 'var(--s-text-3)' }}>
            Compare os sistemas pela nota da última auditoria. Meta da organização: {TARGET}.
          </p>
        </div>
        <SortToggle sortBy={sortBy} onChange={setSortBy} />
      </div>
      <RankingList ranking={sortSystems(systems, sortBy)} onOpen={(id) => navigate(`/systems/${id}`)} />
      <TeamsTable teams={summarizeTeams(systems)} />
      <RuleQualityTile rules={rules} />
    </PageShell>
  );
}
