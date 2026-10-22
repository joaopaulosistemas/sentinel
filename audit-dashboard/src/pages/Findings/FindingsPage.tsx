import { Button, DataTableSkeleton, Dropdown, InlineNotification, Pagination, Tag, Toggle } from '@carbon/react';
import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { FindingsTable } from '../../components/FindingsTable';
import { JourneySteps } from '../../components/JourneySteps';
import { useAudit } from '../../hooks/useAuditRuns';
import { useDiff } from '../../hooks/useLifecycle';
import { useFindings, useFindingsOverview } from '../../hooks/useFindings';
import { useSystems } from '../../hooks/useSystems';
import { useAuditStore } from '../../store/useAuditStore';
import type { Category, FindingResponse, FindingStatus, Severity } from '../../types';
import { activeFilterChips, groupBySystem, type FilterKey } from '../../utils/findingsView';

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
  { id: 'FIXED', label: 'Resolvido', value: 'FIXED' },
  { id: 'SUPPRESSED', label: 'Suprimido', value: 'SUPPRESSED' },
];

interface SystemItem {
  id: string;
  name: string;
  value: number | undefined;
}

const ALL_SYSTEMS: SystemItem = { id: 'all', name: 'Todos os sistemas', value: undefined };

/**
 * Achados. Sem auditoria na URL, mostra os de todos os sistemas (última auditoria concluída de cada um) e deixa
 * filtrar por sistema; com `?auditId=`, mostra os daquela auditoria.
 */
export function FindingsPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const auditId = Number(searchParams.get('auditId') ?? '0');
  const setSelectedAudit = useAuditStore((s) => s.setSelectedAudit);
  const auditMode = auditId > 0;

  const [systemId, setSystemId] = useState<number | undefined>(undefined);
  const [severity, setSeverity] = useState<Severity | undefined>(undefined);
  const [category, setCategory] = useState<Category | undefined>(undefined);
  const [status, setStatus] = useState<FindingStatus | undefined>(undefined);
  const [onlyNew, setOnlyNew] = useState(searchParams.get('onlyNew') === '1');
  const [includeSuppressed, setIncludeSuppressed] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [grouped, setGrouped] = useState(false);
  const [showResolved, setShowResolved] = useState(false);

  const { data: systems } = useSystems();
  const { data: audit } = useAudit(auditId);
  // Os resolvidos não estão na lista da auditoria (já não existem nela): vêm da comparação com a anterior.
  const { data: diff } = useDiff(auditMode ? auditId : undefined);
  const resolved = (diff?.resolvedFindings ?? []).map((f) => ({ ...f, status: 'FIXED' as const }));

  const filters = { severity, category, status, onlyNew, includeSuppressed };
  const single = useFindings(auditId, filters);
  const overview = useFindingsOverview(
    { ...filters, systemId, limit: pageSize, offset: (page - 1) * pageSize },
    !auditMode,
  );

  const systemItems: SystemItem[] = [ALL_SYSTEMS, ...(systems ?? []).map((s) => ({ id: String(s.id), name: s.name, value: s.id }))];
  const currentSystem = auditMode
    ? (systemItems.find((s) => s.name === audit?.systemName) ?? ALL_SYSTEMS)
    : (systemItems.find((s) => s.value === systemId) ?? ALL_SYSTEMS);

  const loading = auditMode ? single.isLoading : overview.isLoading;
  const error = auditMode ? single.error : overview.error;

  const rows: FindingResponse[] = auditMode
    ? (single.data ?? []).slice((page - 1) * pageSize, page * pageSize)
    : (overview.data?.items.map((i) => i.finding) ?? []);
  const total = auditMode ? (single.data?.length ?? 0) : (overview.data?.total ?? 0);

  const systemByFinding = new Map(overview.data?.items.map((i) => [i.finding.id, i]) ?? []);

  const reset = () => setPage(1);

  const chips = activeFilterChips({
    systemName: auditMode
      ? undefined
      : systemId !== undefined
        ? (overview.data?.bySystem.find((b) => b.systemId === systemId)?.systemName ?? currentSystem.name)
        : undefined,
    severity,
    category,
    status,
    onlyNew,
    includeSuppressed,
  });

  const clearFilter = (key: FilterKey) => {
    if (key === 'system') setSystemId(undefined);
    if (key === 'severity') setSeverity(undefined);
    if (key === 'category') setCategory(undefined);
    if (key === 'status') setStatus(undefined);
    if (key === 'onlyNew') setOnlyNew(false);
    if (key === 'includeSuppressed') setIncludeSuppressed(false);
    reset();
  };

  const clearAll = () => {
    setSystemId(undefined);
    setSeverity(undefined);
    setCategory(undefined);
    setStatus(undefined);
    setOnlyNew(false);
    setIncludeSuppressed(false);
    reset();
  };

  const systemOf = auditMode
    ? undefined
    : (f: FindingResponse) => {
        const hit = systemByFinding.get(f.id);
        if (!hit) return undefined;
        return {
          name: hit.systemName,
          onClick: () => {
            setSelectedAudit(hit.auditId);
            navigate(`/systems/${hit.systemId}`);
          },
        };
      };

  const pickSystem = (value: number | undefined) => {
    // Escolher um sistema sai do modo "uma auditoria" e volta a olhar a última de cada sistema.
    if (auditMode) setSearchParams({});
    setSystemId(value);
    reset();
  };

  return (
    <div style={{ padding: '2rem' }}>
      <div style={{ marginBottom: '1rem' }}>
        <JourneySteps current={4} />
      </div>
      <h1 style={{ fontSize: '1.75rem', fontWeight: 600, marginBottom: '0.25rem' }}>
        Findings
        <span style={{ fontSize: '1rem', color: '#6f6f6f', marginLeft: '0.75rem' }}>
          {auditMode
            ? `— ${audit?.systemName ? `${audit.systemName} · ` : ''}Auditoria #${auditId}`
            : `— ${currentSystem.name}`}
        </span>
      </h1>
      <p style={{ color: '#525252', marginBottom: '1.5rem' }}>
        {auditMode
          ? 'Achados desta auditoria.'
          : 'Da última auditoria concluída de cada sistema. Escolha um sistema para focar.'}
      </p>

      {!auditMode && overview.data && overview.data.bySystem.length > 0 && (
        <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', marginBottom: '1.25rem' }}>
          {overview.data.bySystem.map((s) => {
            const active = systemId === s.systemId;
            return (
              <button
                key={s.systemId}
                type="button"
                aria-pressed={active}
                onClick={() => pickSystem(active ? undefined : s.systemId)}
                style={{
                  padding: '0.5rem 0.875rem',
                  background: active ? '#edf5ff' : '#fff',
                  border: `1px solid ${active ? '#0f62fe' : '#e0e0e0'}`,
                  cursor: 'pointer',
                  font: 'inherit',
                }}
              >
                <strong style={{ fontWeight: 600 }}>{s.systemName}</strong>
                <span style={{ marginLeft: '0.5rem', color: '#525252' }}>{s.count} achados</span>
              </button>
            );
          })}
        </div>
      )}

      <div style={{ display: 'flex', gap: '1rem', marginBottom: '1.5rem', flexWrap: 'wrap' }}>
        <Dropdown
          id="system-filter"
          label="Sistema"
          titleText="Sistema"
          items={systemItems}
          itemToString={(item) => item?.name ?? ''}
          selectedItem={currentSystem}
          onChange={({ selectedItem }) => pickSystem(selectedItem?.value)}
          style={{ minWidth: '240px' }}
        />
        <Dropdown
          id="severity-filter"
          label="Severidade"
          titleText="Severidade"
          items={severityItems}
          itemToString={(item) => item?.label ?? ''}
          selectedItem={severityItems.find((i) => i.value === severity) ?? severityItems[0]}
          onChange={({ selectedItem }) => {
            setSeverity(selectedItem?.value);
            reset();
          }}
          style={{ minWidth: '200px' }}
        />
        <Dropdown
          id="category-filter"
          label="Categoria"
          titleText="Categoria"
          items={categoryItems}
          itemToString={(item) => item?.label ?? ''}
          selectedItem={categoryItems.find((i) => i.value === category) ?? categoryItems[0]}
          onChange={({ selectedItem }) => {
            setCategory(selectedItem?.value);
            reset();
          }}
          style={{ minWidth: '200px' }}
        />
        <Dropdown
          id="status-filter"
          label="Status"
          titleText="Status"
          items={statusItems}
          itemToString={(item) => item?.label ?? ''}
          selectedItem={statusItems.find((i) => i.value === status) ?? statusItems[0]}
          onChange={({ selectedItem }) => {
            setStatus(selectedItem?.value);
            reset();
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
            reset();
          }}
        />
        {!auditMode && (
          <Toggle
            id="group-by-system"
            labelText="Agrupar"
            labelA="Lista única"
            labelB="Por sistema"
            toggled={grouped}
            onToggle={(v) => setGrouped(v)}
          />
        )}
        <Toggle
          id="include-suppressed"
          labelText="Suprimidos"
          labelA="Ocultos"
          labelB="Mostrar"
          toggled={includeSuppressed}
          onToggle={(v) => {
            setIncludeSuppressed(v);
            reset();
          }}
        />
      </div>

      {chips.length > 0 && (
        <div
          aria-label="Filtros ativos"
          style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', flexWrap: 'wrap', marginBottom: '1rem' }}
        >
          <span style={{ fontSize: '0.8125rem', color: '#525252' }}>Filtros ativos:</span>
          {chips.map((c) => (
            <Tag key={c.key} type="blue" filter onClose={() => clearFilter(c.key)}>
              {c.label}
            </Tag>
          ))}
          <Button kind="ghost" size="sm" onClick={clearAll}>
            Limpar filtros
          </Button>
        </div>
      )}

      {loading ? (
        <DataTableSkeleton columnCount={7} rowCount={10} />
      ) : error ? (
        <InlineNotification
          kind="error"
          title="Erro ao carregar findings"
          subtitle={error instanceof Error ? error.message : 'Erro desconhecido'}
        />
      ) : total === 0 ? (
        <InlineNotification
          kind="info"
          title="Nenhum achado com esses filtros"
          subtitle={
            auditMode
              ? 'Tente remover algum filtro.'
              : 'Pode ser que nenhum sistema tenha auditoria concluída ainda, ou que os filtros sejam restritivos.'
          }
          hideCloseButton
        />
      ) : (
        <>
          {grouped && !auditMode ? (
            <div style={{ display: 'grid', gap: '1.5rem' }}>
              {groupBySystem(overview.data?.items ?? []).map((g) => (
                <section key={g.systemId} aria-label={`Achados de ${g.systemName}`}>
                  <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '0.5rem' }}>
                    {g.systemName}
                    <span style={{ fontWeight: 400, color: '#525252', marginLeft: '0.5rem' }}>
                      {g.findings.length} {g.findings.length === 1 ? 'achado nesta página' : 'achados nesta página'}
                    </span>
                  </h2>
                  <FindingsTable findings={g.findings} systemOf={systemOf} />
                </section>
              ))}
            </div>
          ) : (
            <FindingsTable findings={rows} systemName={auditMode ? audit?.systemName : undefined} systemOf={systemOf} />
          )}
          <Pagination
            totalItems={total}
            pageSize={pageSize}
            pageSizes={[10, 20, 50]}
            page={page}
            onChange={({ page: p, pageSize: size }) => {
              if (size !== pageSize) {
                setPageSize(size);
                setPage(1);
              } else {
                setPage(p);
              }
            }}
            style={{ marginTop: '1rem' }}
          />
        </>
      )}

      {auditMode && resolved.length > 0 && (
        <section aria-label="Resolvidos" style={{ marginTop: '2rem' }}>
          <Button kind="tertiary" size="sm" aria-expanded={showResolved} onClick={() => setShowResolved(!showResolved)}>
            {showResolved ? 'Ocultar' : 'Mostrar'} {resolved.length} {resolved.length === 1 ? 'achado resolvido' : 'achados resolvidos'} desde a auditoria anterior
          </Button>
          {showResolved && (
            <div style={{ marginTop: '1rem' }}>
              <FindingsTable findings={resolved} systemName={audit?.systemName} />
            </div>
          )}
        </section>
      )}
    </div>
  );
}
