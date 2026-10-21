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
import { useNavigate } from 'react-router-dom';
import { useRuleQuality, useSystems } from '../../hooks/useSystems';
import { rankSystems, summarizeTeams } from '../../utils/ranking';

function scoreTag(score: number | null | undefined) {
  if (score == null) return <Tag type="gray">Nunca auditado</Tag>;
  const type = score >= 80 ? 'green' : score >= 60 ? 'warm-gray' : score >= 40 ? 'magenta' : 'red';
  return <Tag type={type}>{Math.round(score)}</Tag>;
}

/** Visão consolidada: onde olhar primeiro (sistemas) e como cada time está (times). */
export function RankingPage() {
  const navigate = useNavigate();
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
  const ranking = rankSystems(systems);
  const teams = summarizeTeams(systems);

  return (
    <div style={{ padding: '2rem' }}>
      <h1 style={{ marginBottom: '0.5rem', fontSize: '1.75rem', fontWeight: 600 }}>Painel consolidado</h1>
      <p style={{ marginBottom: '1.5rem', color: '#525252' }}>
        Ordenado por atenção necessária: nunca auditados, depois críticos e altos abertos, depois menor score.
      </p>

      <Tile style={{ marginBottom: '2rem', padding: '1.25rem' }}>
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

      <Tile style={{ marginBottom: '2rem', padding: '1.25rem' }}>
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

      <Tile style={{ padding: '1.25rem' }}>
        <h3 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '0.75rem' }}>Sistemas por risco</h3>
        <Table size="md">
          <TableHead>
            <TableRow>
              <TableHeader>#</TableHeader>
              <TableHeader>Sistema</TableHeader>
              <TableHeader>Time</TableHeader>
              <TableHeader>Score</TableHeader>
              <TableHeader>Críticos</TableHeader>
              <TableHeader>Altos</TableHeader>
            </TableRow>
          </TableHead>
          <TableBody>
            {ranking.map((s, i) => (
              <TableRow key={s.id} style={{ cursor: 'pointer' }} onClick={() => navigate(`/systems/${s.id}`)}>
                <TableCell>{i + 1}</TableCell>
                <TableCell>{s.name}</TableCell>
                <TableCell>{s.team || '—'}</TableCell>
                <TableCell>{scoreTag(s.lastAuditScore)}</TableCell>
                <TableCell>{s.openCritical ?? 0}</TableCell>
                <TableCell>{s.openHigh ?? 0}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Tile>
    </div>
  );
}
