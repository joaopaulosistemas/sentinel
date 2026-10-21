import {
  Button,
  DataTableSkeleton,
  InlineNotification,
  Modal,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  TextInput,
  Tile,
} from '@carbon/react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../api/client';
import { ScoreGauge } from '../../components/ScoreGauge';
import { useSystems } from '../../hooks/useSystems';
import { useAuditStore } from '../../store/useAuditStore';
import type { SystemSummaryResponse } from '../../types';

interface TriggerModalState {
  open: boolean;
  system: SystemSummaryResponse | null;
  branch: string;
}

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
  const queryClient = useQueryClient();
  const setSelectedSystem = useAuditStore((s) => s.setSelectedSystem);
  const { data: systems, isLoading, error } = useSystems();

  const [modal, setModal] = useState<TriggerModalState>({
    open: false,
    system: null,
    branch: 'main',
  });

  const triggerMutation = useMutation({
    mutationFn: (vars: { systemId: number; branch: string }) =>
      api.triggerAudit({ ...vars, triggeredBy: 'dashboard-user' }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['systems'] });
      setModal({ open: false, system: null, branch: 'main' });
    },
  });

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

  return (
    <div style={{ padding: '2rem' }}>
      <h1 style={{ marginBottom: '1.5rem', fontSize: '1.75rem', fontWeight: 600 }}>
        Visão Geral
      </h1>

      {/* Metric Cards */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(3, 1fr)',
          gap: '1rem',
          marginBottom: '2rem',
        }}
      >
        <Tile>
          <p style={{ fontSize: '0.875rem', color: '#6f6f6f', marginBottom: '0.5rem' }}>
            Total de Sistemas
          </p>
          <div style={{ fontSize: '2.5rem', fontWeight: 700 }}>{totalSystems}</div>
        </Tile>
        <Tile>
          <p style={{ fontSize: '0.875rem', color: '#6f6f6f', marginBottom: '0.5rem' }}>
            Findings Críticos Abertos
          </p>
          <div
            style={{
              fontSize: '2.5rem',
              fontWeight: 700,
              color: totalCritical > 0 ? '#da1e28' : '#24a148',
            }}
          >
            {totalCritical}
          </div>
        </Tile>
        <Tile>
          <p style={{ fontSize: '0.875rem', color: '#6f6f6f', marginBottom: '0.5rem' }}>
            Score Médio
          </p>
          <ScoreGauge score={avgScore} size="lg" />
        </Tile>
      </div>

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
                    onClick={() => setModal({ open: true, system, branch: 'main' })}
                  >
                    Nova Auditoria
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Trigger Audit Modal */}
      <Modal
        open={modal.open}
        modalHeading={`Nova Auditoria — ${modal.system?.name ?? ''}`}
        primaryButtonText="Iniciar"
        secondaryButtonText="Cancelar"
        primaryButtonDisabled={triggerMutation.isPending}
        onRequestClose={() => setModal({ open: false, system: null, branch: 'main' })}
        onRequestSubmit={() => {
          if (modal.system) {
            triggerMutation.mutate({ systemId: modal.system.id, branch: modal.branch });
          }
        }}
      >
        <TextInput
          id="branch-input"
          labelText="Branch"
          value={modal.branch}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            setModal((prev) => ({ ...prev, branch: e.target.value }))
          }
        />
        {triggerMutation.isError && (
          <InlineNotification
            kind="error"
            title="Erro ao iniciar auditoria"
            subtitle={
              triggerMutation.error instanceof Error
                ? triggerMutation.error.message
                : 'Erro desconhecido'
            }
            style={{ marginTop: '1rem' }}
          />
        )}
      </Modal>
    </div>
  );
}
