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
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useRuleQuality, useSystems } from '../../hooks/useSystems';
import type { SystemSummaryResponse } from '../../types';
import { formatTrend, summarizeTeams } from '../../utils/ranking';
import { timeAgo } from '../../utils/timeAgo';

export type SortKey = 'nota' | 'criticos' | 'tendencia';

const TARGET = 75;
const sortOptions: Array<{ key: SortKey; label: string }> = [
  { key: 'nota', label: 'Nota' },
  { key: 'criticos', label: 'Críticos' },
  { key: 'tendencia', label: 'Tendência' },
];

const trendColor = { up: '#198038', down: '#a2191f', flat: '#525252', none: '#6f6f6f' } as const;

const barColor = (n: number) => (n >= TARGET ? '#198038' : n >= 50 ? '#8a6a00' : '#a2191f');

/** Ordem da lista: melhor nota primeiro, mais críticos primeiro, ou maior ganho de nota primeiro. Sem dado vai para o fim. */
export function sortSystems(systems: SystemSummaryResponse[], by: SortKey): SystemSummaryResponse[] {
  const list = [...systems];
  const byName = (a: SystemSummaryResponse, b: SystemSummaryResponse) => a.name.localeCompare(b.name);
  if (by === 'tendencia') {
    return list.sort((a, b) => (b.scoreDelta ?? -Infinity) - (a.scoreDelta ?? -Infinity) || byName(a, b));
  }
  if (by === 'nota') return list.sort((a, b) => (b.lastAuditScore ?? -1) - (a.lastAuditScore ?? -1) || byName(a, b));
  return list.sort((a, b) => (b.openCritical ?? 0) - (a.openCritical ?? 0) || (b.lastAuditScore ?? -1) - (a.lastAuditScore ?? -1) || byName(a, b));
}

function scoreTag(score: number | null | undefined) {
  if (score == null) return <Tag type="gray">Nunca auditado</Tag>;
  const type = score >= 80 ? 'green' : score >= 60 ? 'warm-gray' : score >= 40 ? 'magenta' : 'red';
  return <Tag type={type}>{Math.round(score)}</Tag>;
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
  const ranking = sortSystems(systems, sortBy);
  const teams = summarizeTeams(systems);

  return (
    <div style={{ padding: '2rem', display: 'grid', gap: '1.5rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', gap: '1rem', flexWrap: 'wrap' }}>
        <div>
          <h1 style={{ marginBottom: '0.25rem', fontSize: '1.75rem', fontWeight: 400 }}>Ranking de Sistemas</h1>
          <p style={{ color: '#525252' }}>
            Compare os sistemas pela nota da última auditoria. Meta da organização: {TARGET}.
          </p>
        </div>
        <div role="group" aria-label="Ordenar por" style={{ display: 'flex' }}>
          {sortOptions.map((o) => (
            <button
              key={o.key}
              type="button"
              aria-pressed={sortBy === o.key}
              onClick={() => setSortBy(o.key)}
              style={{
                height: 36,
                padding: '0 1rem',
                marginLeft: -1,
                border: '1px solid #8d8d8d',
                background: sortBy === o.key ? '#161616' : '#fff',
                color: sortBy === o.key ? '#fff' : '#161616',
                cursor: 'pointer',
                font: 'inherit',
                fontSize: '0.8125rem',
              }}
            >
              {o.label}
            </button>
          ))}
        </div>
      </div>

      <section aria-label="Sistemas por risco" style={{ background: '#fff', border: '1px solid #e0e0e0' }}>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '56px minmax(180px, 2fr) minmax(220px, 3fr) 90px 110px 130px',
            gap: '0.75rem',
            padding: '0.75rem 1rem',
            background: '#e8e8e8',
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
        {ranking.map((s, i) => {
          const score = s.lastAuditScore;
          return (
            <button
              key={s.id}
              type="button"
              onClick={() => navigate(`/systems/${s.id}`)}
              style={{
                all: 'unset',
                boxSizing: 'border-box',
                width: '100%',
                cursor: 'pointer',
                display: 'grid',
                gridTemplateColumns: '56px minmax(180px, 2fr) minmax(220px, 3fr) 90px 110px 130px',
                gap: '0.75rem',
                alignItems: 'center',
                padding: '0.875rem 1rem',
                borderTop: '1px solid #e0e0e0',
              }}
            >
              <span style={{ fontSize: '1.25rem', fontWeight: 300 }}>{i + 1}</span>
              <span style={{ display: 'grid' }}>
                <strong style={{ fontWeight: 600 }}>{s.name}</strong>
                <span style={{ fontSize: '0.75rem', color: '#525252' }}>{s.team || 'Sem time'}</span>
              </span>
              {score == null ? (
                <span><Tag type="gray">Nunca auditado</Tag></span>
              ) : (
                <span style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                  <span style={{ position: 'relative', flex: 1, height: 10, background: '#e0e0e0' }}>
                    <span style={{ position: 'absolute', left: 0, top: 0, height: 10, width: `${Math.max(0, Math.min(100, score))}%`, background: barColor(score) }} />
                    <span style={{ position: 'absolute', left: `${TARGET}%`, top: -4, width: 2, height: 18, background: '#161616' }} />
                  </span>
                  <span style={{ width: 32, fontWeight: 600, color: barColor(score) }}>{Math.round(score)}</span>
                </span>
              )}
              <span style={{ fontWeight: 500, color: trendColor[formatTrend(s.scoreDelta).tone], fontSize: '0.8125rem' }}>
                {formatTrend(s.scoreDelta).text}
              </span>
              <span style={{ fontWeight: 600, color: (s.openCritical ?? 0) > 0 ? '#a2191f' : '#198038' }}>{s.openCritical ?? 0}</span>
              <span style={{ fontSize: '0.8125rem', color: '#525252' }}>{timeAgo(s.lastAuditDate)}</span>
            </button>
          );
        })}
      </section>

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

      <Tile style={{ padding: '1.25rem' }}>
        <h3 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '0.25rem' }}>Qualidade das regras</h3>
        <p style={{ fontSize: '0.875rem', color: '#525252', marginBottom: '0.75rem' }}>
          Taxa de falso positivo, a partir do que o time suprimiu. Só pede revisão com 5+ ocorrências e 40%+ de falsos positivos.
        </p>
        {!rules || rules.length === 0 ? (
          <p style={{ color: '#525252' }}>Ainda não há findings para avaliar.</p>
        ) : (
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
        )}
      </Tile>
    </div>
  );
}
