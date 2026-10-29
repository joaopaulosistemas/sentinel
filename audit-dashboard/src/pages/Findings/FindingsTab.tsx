import { InlineNotification } from '@carbon/react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useDiff, useFreshness } from '../../hooks/useLifecycle';
import { useAuditStore } from '../../store/useAuditStore';
import type { AuditRunResponse, SystemSummaryResponse } from '../../types';
import { activeFilterChips, footerText } from '../../utils/findingsView';
import { FindingsFilters, PaginationFooter } from './FindingsFilters';
import { FindingsListBody, ResolvedSection, SystemSummaryCards, originFinder } from './FindingsParts';
import { isDefaultFilters, useClaudeNotes, useFindingsData, type FindingFilters } from './useFindingsPage';

interface Props {
  auditMode: boolean;
  auditId: number;
  audit: AuditRunResponse | undefined;
  systems: SystemSummaryResponse[] | undefined;
  filters: FindingFilters;
  auditSystemId: number | undefined;
  activeSystemId: number | undefined;
}

function StaleAuditNotice({ auditedCommit, currentCommit }: { auditedCommit?: string | null; currentCommit?: string | null }) {
  return (
    <InlineNotification
      kind="warning"
      title="Auditoria desatualizada"
      subtitle={`A branch mudou desde esta auditoria (auditado ${auditedCommit?.slice(0, 7)}, atual ${currentCommit?.slice(0, 7)}). Achados já corrigidos podem aparecer aqui; reaudite para conferir.`}
      hideCloseButton
    />
  );
}

type FindingsData = ReturnType<typeof useFindingsData>;

/** Os resolvidos não estão na lista da auditoria (já não existem nela): vêm da comparação com a anterior. */
function useResolvedFindings(auditMode: boolean, auditId: number) {
  const { data: diff } = useDiff(auditMode ? auditId : undefined);
  const { data: freshness } = useFreshness(auditMode ? auditId : undefined);
  const resolved = (diff?.resolvedFindings ?? []).map((f) => ({ ...f, status: 'FIXED' as const }));
  return { resolved, stale: freshness?.stale === true ? freshness : null };
}

/** Clicar na origem de um achado abre o sistema dele (só na visão de todos os sistemas). */
function useSystemOrigin(auditMode: boolean, data: FindingsData) {
  const navigate = useNavigate();
  const setSelectedAudit = useAuditStore((s) => s.setSelectedAudit);
  if (auditMode) return undefined;
  return originFinder(data.itemByFinding, data.stackBySystem, (hit) => {
    setSelectedAudit(hit.auditId);
    navigate(`/systems/${hit.systemId}`);
  });
}

interface ConnectedFiltersProps {
  auditMode: boolean;
  systems: SystemSummaryResponse[] | undefined;
  activeSystemId: number | undefined;
  filters: FindingFilters;
  data: FindingsData;
}

function ConnectedFilters({ auditMode, systems, activeSystemId, filters, data }: ConnectedFiltersProps) {
  const [, setSearchParams] = useSearchParams();
  const systemName = data.summary.find((b) => b.systemId === filters.systemId)?.systemName ?? '';
  const chips = activeFilterChips({ systemName, showSystem: !auditMode, severity: filters.severity, category: filters.category, situation: filters.situation });
  return (
    <FindingsFilters
      auditMode={auditMode}
      systems={systems}
      currentSystemValue={String(activeSystemId ?? '')}
      onPickSystem={(value) => {
        // Escolher um sistema sai do modo "uma auditoria" e volta a olhar a última de cada sistema.
        if (auditMode) setSearchParams({});
        filters.setSystemId(value);
      }}
      severity={filters.severity}
      onSeverity={filters.setSeverity}
      category={filters.category}
      onCategory={filters.setCategory}
      situation={filters.situation}
      onSituation={filters.setSituation}
      grouped={filters.grouped}
      onGrouped={filters.setGrouped}
      lowTotal={data.lowTotal}
      lowHidden={data.lowHidden}
      showLow={filters.showLow}
      onShowLow={filters.setShowLow}
      chips={chips}
      isDefault={isDefaultFilters(filters)}
      onRestore={filters.restoreDefaults}
    />
  );
}

interface ListSectionProps {
  auditMode: boolean;
  auditId: number;
  auditSystemName: string | undefined;
  filters: FindingFilters;
  data: FindingsData;
  systemOf: ReturnType<typeof useSystemOrigin>;
  claudeNoteOf: ReturnType<typeof useClaudeNotes>;
}

function ListSection({ auditMode, auditId, auditSystemName, filters, data, systemOf, claudeNoteOf }: ListSectionProps) {
  const showFooter = !data.loading && !data.error && data.total > 0;
  return (
    <section aria-label="Lista de findings" style={{ background: 'var(--s-surface)', border: '1px solid var(--s-border)' }}>
      <FindingsListBody
        loading={data.loading}
        error={data.error}
        total={data.total}
        auditMode={auditMode}
        auditId={auditId}
        auditSystemName={auditSystemName}
        grouped={filters.grouped}
        rows={data.rows}
        items={data.items}
        systemOf={systemOf}
        claudeNoteOf={claudeNoteOf}
      />
      {showFooter && (
        <PaginationFooter
          text={footerText(data.rows.length, data.total, filters.situation, filters.severity, auditMode ? 1 : data.summary.length)}
          page={filters.page}
          pages={data.pages}
          pageSize={filters.pageSize}
          onPage={filters.setPage}
          onPageSize={filters.setPageSize}
        />
      )}
    </section>
  );
}

/** Aba "Achados e nota": filtros, resumo por sistema, lista paginada e os achados resolvidos desde a auditoria anterior. */
export function FindingsTab({ auditMode, auditId, audit, systems, filters, auditSystemId, activeSystemId }: Props) {
  const { resolved, stale } = useResolvedFindings(auditMode, auditId);
  const data = useFindingsData(auditMode, auditId, filters);
  const claudeNoteOf = useClaudeNotes(auditMode, auditSystemId, filters.systemId, data);
  const systemOf = useSystemOrigin(auditMode, data);

  return (
    <>
      {auditMode && stale && <StaleAuditNotice auditedCommit={stale.auditedCommit} currentCommit={stale.currentCommit} />}
      <ConnectedFilters auditMode={auditMode} systems={systems} activeSystemId={activeSystemId} filters={filters} data={data} />

      {!auditMode && data.summary.length > 0 && <SystemSummaryCards summary={data.summary} />}

      <ListSection auditMode={auditMode} auditId={auditId} auditSystemName={audit?.systemName} filters={filters} data={data} systemOf={systemOf} claudeNoteOf={claudeNoteOf} />

      {auditMode && resolved.length > 0 && (
        <ResolvedSection
          resolved={resolved}
          open={filters.showResolved}
          onToggle={() => filters.setShowResolved(!filters.showResolved)}
          systemName={audit?.systemName}
          auditId={auditId}
          claudeNoteOf={claudeNoteOf}
        />
      )}
    </>
  );
}
