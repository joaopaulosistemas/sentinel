import {
  DataTableSkeleton,
  Dropdown,
  Toggle,
  InlineNotification,
  Pagination,
} from '@carbon/react';
import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { api } from '../../api/client';
import { useAudit } from '../../hooks/useAuditRuns';
import { useSystems } from '../../hooks/useSystems';
import { JourneySteps } from '../../components/JourneySteps';
import { FindingsTable } from '../../components/FindingsTable';
import { useFindings } from '../../hooks/useFindings';
import { useAuditStore } from '../../store/useAuditStore';
import type { Category, FindingStatus, Severity } from '../../types';

const PAGE_SIZE = 20;

interface DropdownItem<T> {
  id: string;
  label: string;
  value: T | undefined;
}

const severityItems: DropdownItem<Severity>[] = [
  { id: 'all', label: 'Todas as severidades', value: undefined },
  { id: 'CRITICAL', label: 'Crítico', value: 'CRITICAL' },
  { id: 'HIGH', label: 'Alto', value: 'HIGH' },
  { id: 'MEDIUM', label: 'Médio', value: 'MEDIUM' },
  { id: 'LOW', label: 'Baixo', value: 'LOW' },
  { id: 'INFO', label: 'Info', value: 'INFO' },
];

const categoryItems: DropdownItem<Category>[] = [
  { id: 'all', label: 'Todas as categorias', value: undefined },
  { id: 'SECURITY', label: 'Segurança', value: 'SECURITY' },
  { id: 'QUALITY', label: 'Qualidade', value: 'QUALITY' },
  { id: 'ARCHITECTURE', label: 'Arquitetura', value: 'ARCHITECTURE' },
  { id: 'DEPENDENCY', label: 'Dependências', value: 'DEPENDENCY' },
  { id: 'OBSERVABILITY', label: 'Observabilidade', value: 'OBSERVABILITY' },
];

const statusItems: DropdownItem<FindingStatus>[] = [
  { id: 'all', label: 'Todos os status', value: undefined },
  { id: 'OPEN', label: 'Aberto', value: 'OPEN' },
  { id: 'ACKNOWLEDGED', label: 'Reconhecido', value: 'ACKNOWLEDGED' },
  { id: 'FIXED', label: 'Corrigido', value: 'FIXED' },
  { id: 'SUPPRESSED', label: 'Suprimido', value: 'SUPPRESSED' },
];

export function FindingsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const queryClient = useQueryClient();
  const auditIdFromQuery = Number(searchParams.get('auditId') ?? '0');
  const storeAuditId = useAuditStore((s) => s.selectedAuditId);
  const setSelectedAudit = useAuditStore((s) => s.setSelectedAudit);
  const auditId = auditIdFromQuery > 0 ? auditIdFromQuery : (storeAuditId ?? 0);

  const { data: audit } = useAudit(auditId);
  const { data: systems } = useSystems();
  const [switching, setSwitching] = useState<string | null>(null);
  const currentSystem = systems?.find((sys) => sys.name === audit?.systemName) ?? null;

  // Trocar de sistema leva à última auditoria concluída dele.
  const pickSystem = async (systemId: number) => {
    setSwitching(null);
    const runs = await queryClient.fetchQuery({
      queryKey: ['auditRuns', systemId],
      queryFn: () => api.getAuditRuns(systemId),
    });
    const latest = runs
      .filter((r) => r.status === 'COMPLETED')
      .sort((a, b) => b.id - a.id)[0];
    if (!latest) {
      setSwitching('Este sistema ainda não tem auditoria concluída.');
      return;
    }
    setSelectedAudit(latest.id);
    setSearchParams({ auditId: String(latest.id) });
    setPage(1);
  };

  const [severityFilter, setSeverityFilter] = useState<Severity | undefined>(undefined);
  const [categoryFilter, setCategoryFilter] = useState<Category | undefined>(undefined);
  const [statusFilter, setStatusFilter] = useState<FindingStatus | undefined>(undefined);
  const [onlyNew, setOnlyNew] = useState(searchParams.get('onlyNew') === '1');
  const [includeSuppressed, setIncludeSuppressed] = useState(false);
  const [page, setPage] = useState(1);

  const { data: findings, isLoading, error } = useFindings(
    auditId,
    { severity: severityFilter, category: categoryFilter, status: statusFilter, onlyNew, includeSuppressed },
  );

  const totalItems = findings?.length ?? 0;
  const paginated = findings?.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE) ?? [];

  return (
    <div style={{ padding: '2rem' }}>
      <div style={{ marginBottom: '1rem' }}>
        <JourneySteps current={4} />
      </div>
      <h1 style={{ fontSize: '1.75rem', fontWeight: 600, marginBottom: '1.5rem' }}>
        Findings
        {auditId > 0 && (
          <span style={{ fontSize: '1rem', color: '#6f6f6f', marginLeft: '0.75rem' }}>
            — {audit?.systemName ? `${audit.systemName} · ` : ''}Auditoria #{auditId}
          </span>
        )}
      </h1>

      {switching && (
        <InlineNotification kind="warning" title="Sem resultado" subtitle={switching} style={{ marginBottom: '1rem' }} />
      )}

      {auditId === 0 && (
        <InlineNotification
          kind="info"
          title="Selecione uma auditoria"
          subtitle="Acesse um sistema e clique em 'Ver Findings' para visualizar os resultados."
          style={{ marginBottom: '1rem' }}
        />
      )}

      {/* Filters */}
      <div
        style={{
          display: 'flex',
          gap: '1rem',
          marginBottom: '1.5rem',
          flexWrap: 'wrap',
        }}
      >
        <Dropdown
          id="system-filter"
          label="Sistema"
          titleText="Sistema"
          items={systems ?? []}
          itemToString={(item) => item?.name ?? ''}
          selectedItem={currentSystem}
          onChange={({ selectedItem }) => {
            if (selectedItem) void pickSystem(selectedItem.id);
          }}
          style={{ minWidth: '240px' }}
        />
        <Dropdown
          id="severity-filter"
          label="Severidade"
          titleText="Severidade"
          items={severityItems}
          itemToString={(item) => item?.label ?? ''}
          selectedItem={severityItems.find((i) => i.value === severityFilter) ?? severityItems[0]}
          onChange={({ selectedItem }) => {
            setSeverityFilter(selectedItem?.value);
            setPage(1);
          }}
          style={{ minWidth: '200px' }}
        />
        <Dropdown
          id="category-filter"
          label="Categoria"
          titleText="Categoria"
          items={categoryItems}
          itemToString={(item) => item?.label ?? ''}
          selectedItem={categoryItems.find((i) => i.value === categoryFilter) ?? categoryItems[0]}
          onChange={({ selectedItem }) => {
            setCategoryFilter(selectedItem?.value);
            setPage(1);
          }}
          style={{ minWidth: '200px' }}
        />
        <Dropdown
          id="status-filter"
          label="Status"
          titleText="Status"
          items={statusItems}
          itemToString={(item) => item?.label ?? ''}
          selectedItem={statusItems.find((i) => i.value === statusFilter) ?? statusItems[0]}
          onChange={({ selectedItem }) => {
            setStatusFilter(selectedItem?.value);
            setPage(1);
          }}
          style={{ minWidth: '200px' }}
        />
        <Toggle
          id="only-new"
          labelText="Somente novos"
          labelA="Todos"
          labelB="Só novos"
          toggled={onlyNew}
          onToggle={(v) => {
            setOnlyNew(v);
            setPage(1);
          }}
        />
        <Toggle
          id="include-suppressed"
          labelText="Suprimidos"
          labelA="Ocultos"
          labelB="Mostrar"
          toggled={includeSuppressed}
          onToggle={(v) => {
            setIncludeSuppressed(v);
            setPage(1);
          }}
        />
      </div>

      {/* Content */}
      {isLoading && auditId > 0 ? (
        <DataTableSkeleton columnCount={7} rowCount={10} />
      ) : error ? (
        <InlineNotification
          kind="error"
          title="Erro ao carregar findings"
          subtitle={error instanceof Error ? error.message : 'Erro desconhecido'}
        />
      ) : auditId > 0 ? (
        <>
          <FindingsTable findings={paginated} systemName={audit?.systemName} />
          <Pagination
            totalItems={totalItems}
            pageSize={PAGE_SIZE}
            pageSizes={[10, 20, 50]}
            page={page}
            onChange={({ page: p, pageSize }) => {
              setPage(p);
              if (pageSize !== PAGE_SIZE) setPage(1);
            }}
            style={{ marginTop: '1rem' }}
          />
        </>
      ) : null}
    </div>
  );
}
