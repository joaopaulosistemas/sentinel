import {
  DataTableSkeleton,
  Dropdown,
  Toggle,
  InlineNotification,
  Pagination,
} from '@carbon/react';
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
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
  const [searchParams] = useSearchParams();
  const auditIdFromQuery = Number(searchParams.get('auditId') ?? '0');
  const storeAuditId = useAuditStore((s) => s.selectedAuditId);
  const auditId = auditIdFromQuery > 0 ? auditIdFromQuery : (storeAuditId ?? 0);

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
      <h1 style={{ fontSize: '1.75rem', fontWeight: 600, marginBottom: '1.5rem' }}>
        Findings
        {auditId > 0 && (
          <span style={{ fontSize: '1rem', color: '#6f6f6f', marginLeft: '0.75rem' }}>
            — Auditoria #{auditId}
          </span>
        )}
      </h1>

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
        <DataTableSkeleton columnCount={6} rowCount={10} />
      ) : error ? (
        <InlineNotification
          kind="error"
          title="Erro ao carregar findings"
          subtitle={error instanceof Error ? error.message : 'Erro desconhecido'}
        />
      ) : auditId > 0 ? (
        <>
          <FindingsTable findings={paginated} />
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
