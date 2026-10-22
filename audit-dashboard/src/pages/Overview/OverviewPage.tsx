import {
  Button,
  DataTableSkeleton,
  InlineNotification,
  Select,
  SelectItem,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  Tile,
} from '@carbon/react';
import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { JourneySteps } from '../../components/JourneySteps';
import { RecentAudits } from '../../components/RecentAudits';
import { ScoreGauge } from '../../components/ScoreGauge';
import { StartAuditModal } from '../../components/StartAuditModal';
import { useRecentAudits } from '../../hooks/useAuditRuns';
import { useSystems } from '../../hooks/useSystems';
import { useAuditStore } from '../../store/useAuditStore';
import type { SystemSummaryResponse } from '../../types';
import { isActiveRun } from '../../utils/auditProgress';
import { STALE_DAYS, isStale, systemsNeedingAttention } from '../../utils/systemAttention';

const stackLabels: Record<string, string> = {
  JAVA: 'Java',
  JAVASCRIPT: 'JavaScript',
  DOTNET: '.NET',
  PYTHON: 'Python',
  COBOL: 'COBOL',
  MIXED: 'Misto',
};

export function OverviewPage() {
  const navigate = useNavigate();
  const setSelectedSystem = useAuditStore((s) => s.setSelectedSystem);
  const { data: systems, isLoading, error } = useSystems();
  const { data: recent } = useRecentAudits(20);
  const [pickedId, setPickedId] = useState('');

  const [auditTarget, setAuditTarget] = useState<SystemSummaryResponse | null>(null);

  if (isLoading) return <DataTableSkeleton columnCount={7} rowCount={5} />;

  if (error) {
    return (
      <InlineNotification
        kind="error"
        title="Erro ao carregar sistemas"
        subtitle={error instanceof Error ? error.message : 'Erro desconhecido'}
      />
    );
  }

  const totalSystems = systems?.length ?? 0;
  const totalCritical = systems?.reduce((acc, s) => acc + s.openCritical, 0) ?? 0;
  const scores = systems?.map((s) => s.lastAuditScore).filter((s): s is number => s !== null) ?? [];
  const avgScore = scores.length > 0 ? Math.round(scores.reduce((a, b) => a + b, 0) / scores.length) : null;

  const running = (recent ?? []).filter(isActiveRun).length;
  const now = Date.now();
  const stale = (systems ?? []).filter((s) => isStale(s, now)).length;
  const attention = systemsNeedingAttention(systems ?? [], now);
  const picked = (systems ?? []).find((s) => String(s.id) === pickedId) ?? null;

  const kpi = (label: string, value: React.ReactNode, hint?: string) => (
    <Tile>
      <p style={{ fontSize: '0.875rem', color: '#6f6f6f', marginBottom: '0.5rem' }}>{label}</p>
      <div style={{ fontSize: '2.5rem', fontWeight: 700, lineHeight: 1.1 }}>{value}</div>
      {hint && <p style={{ fontSize: '0.75rem', color: '#525252', marginTop: '0.25rem' }}>{hint}</p>}
    </Tile>
  );

  return (
    <div style={{ padding: '2rem' }}>
      <div style={{ marginBottom: '1rem' }}>
        <JourneySteps current={0} />
      </div>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-end',
          flexWrap: 'wrap',
          gap: '1rem',
          marginBottom: '1.5rem',
        }}
      >
        <h1 style={{ fontSize: '1.75rem', fontWeight: 600, margin: 0 }}>Visão Geral</h1>
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'flex-end' }}>
          <Select
            id="overview-system"
            labelText="Auditar um sistema"
            size="md"
            value={pickedId}
            onChange={(e: React.ChangeEvent<HTMLSelectElement>) => setPickedId(e.target.value)}
          >
            <SelectItem value="" text="Escolha o sistema…" />
            {(systems ?? []).map((s) => (
              <SelectItem key={s.id} value={String(s.id)} text={s.name} />
            ))}
          </Select>
          <Button disabled={!picked} onClick={() => setAuditTarget(picked)}>
            Nova auditoria
          </Button>
        </div>
      </div>

      {/* Indicadores */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
          gap: '1rem',
          marginBottom: '2rem',
        }}
      >
        {kpi('Nota média', <ScoreGauge score={avgScore} size="lg" />, `${totalSystems} ${totalSystems === 1 ? 'sistema' : 'sistemas'}`)}
        {kpi(
          'Críticos abertos',
          <span style={{ color: totalCritical > 0 ? '#da1e28' : '#24a148' }}>{totalCritical}</span>,
        )}
        {kpi(
          'Em execução agora',
          <span style={{ color: running > 0 ? '#0043ce' : undefined }}>{running}</span>,
          running > 0 ? 'atualiza sozinho' : 'nenhuma auditoria em andamento',
        )}
        {kpi(
          `Sem auditar há mais de ${STALE_DAYS} dias`,
          <span style={{ color: stale > 0 ? '#8a6a00' : '#24a148' }}>{stale}</span>,
          'inclui os nunca auditados',
        )}
      </div>

      <Tile style={{ padding: 0, marginBottom: '2rem' }}>
        <div style={{ padding: '1rem 1rem 0.75rem', borderBottom: '1px solid #e0e0e0' }}>
          <h2 style={{ fontSize: '1rem', fontWeight: 600, margin: 0 }}>Precisam de atenção</h2>
        </div>
        {attention.length === 0 ? (
          <p style={{ padding: '1rem', color: '#198038' }}>Tudo em ordem: nenhum sistema pede ação agora.</p>
        ) : (
          <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
            {attention.map(({ system, reasons }) => (
              <li
                key={system.id}
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  gap: '1rem',
                  flexWrap: 'wrap',
                  padding: '0.75rem 1rem',
                  borderBottom: '1px solid #e0e0e0',
                }}
              >
                <span style={{ display: 'grid', gap: 2 }}>
                  <strong style={{ fontWeight: 600 }}>{system.name}</strong>
                  <span style={{ fontSize: '0.8125rem', color: '#525252' }}>{reasons.join(' · ')}</span>
                </span>
                <span style={{ display: 'flex', gap: '0.5rem' }}>
                  <Button kind="ghost" size="sm" onClick={() => navigate(`/systems/${system.id}`)}>
                    Ver sistema
                  </Button>
                  <Button size="sm" onClick={() => setAuditTarget(system)}>
                    Auditar agora
                  </Button>
                </span>
              </li>
            ))}
          </ul>
        )}
      </Tile>

      <RecentAudits />

      {/* Systems Table */}
      <TableContainer title="Sistemas" description="Todos os sistemas cadastrados no Sentinel Audit Tool">
        <Table>
          <TableHead>
            <TableRow>
              <TableHeader>Nome</TableHeader>
              <TableHeader>Stack</TableHeader>
              <TableHeader>Time</TableHeader>
              <TableHeader>Score</TableHeader>
              <TableHeader>Críticos</TableHeader>
              <TableHeader>Altos</TableHeader>
              <TableHeader>Última Auditoria</TableHeader>
              <TableHeader>Ações</TableHeader>
            </TableRow>
          </TableHead>
          <TableBody>
            {(systems ?? []).map((system) => (
              <TableRow key={system.id}>
                <TableCell>
                  <button
                    style={{
                      background: 'none',
                      border: 'none',
                      cursor: 'pointer',
                      color: '#0f62fe',
                      textDecoration: 'underline',
                      padding: 0,
                      fontSize: 'inherit',
                    }}
                    onClick={() => {
                      setSelectedSystem(system.id);
                      navigate(`/systems/${system.id}`);
                    }}
                  >
                    {system.name}
                  </button>
                </TableCell>
                <TableCell>{stackLabels[system.stack] ?? system.stack}</TableCell>
                <TableCell>{system.team}</TableCell>
                <TableCell>
                  <ScoreGauge score={system.lastAuditScore} size="sm" />
                </TableCell>
                <TableCell>
                  <span style={{ color: system.openCritical > 0 ? '#da1e28' : undefined, fontWeight: 600 }}>
                    {system.openCritical}
                  </span>
                </TableCell>
                <TableCell>
                  <span style={{ color: system.openHigh > 0 ? '#ff832b' : undefined }}>
                    {system.openHigh}
                  </span>
                </TableCell>
                <TableCell>
                  {system.lastAuditDate
                    ? new Date(system.lastAuditDate).toLocaleDateString('pt-BR')
                    : '—'}
                </TableCell>
                <TableCell>
                  <Button
                    kind="ghost"
                    size="sm"
                    onClick={() => setAuditTarget(system)}
                  >
                    Nova Auditoria
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      <StartAuditModal system={auditTarget} onClose={() => setAuditTarget(null)} />
    </div>
  );
}
