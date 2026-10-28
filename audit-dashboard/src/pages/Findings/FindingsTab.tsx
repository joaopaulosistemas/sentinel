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

/** Aba "Achados e nota": filtros, resumo por sistema, lista paginada e os achados resolvidos desde a auditoria anterior. */
export function FindingsTab({ auditMode, auditId, audit, systems, filters, auditSystemId, activeSystemId }: Props) {
  const navigate = useNavigate();
  const [, setSearchParams] = useSearchParams();
  const setSelectedAudit = useAuditStore((s) => s.setSelectedAudit);
  // Os resolvidos não estão na lista da auditoria (já não existem nela): vêm da comparação com a anterior.
  const { data: diff } = useDiff(auditMode ? auditId : undefined);
  const { data: freshness } = useFreshness(auditMode ? auditId : undefined);
  const resolved = (diff?.resolvedFindings ?? []).map((f) => ({ ...f, status: 'FIXED' as const }));

  const data = useFindingsData(auditMode, auditId, filters);
  const claudeNoteOf = useClaudeNotes(auditMode, auditSystemId, filters.systemId, data);
  const systemOf = auditMode
    ? undefined
    : originFinder(data.itemByFinding, data.stackBySystem, (hit) => {
        setSelectedAudit(hit.auditId);
        navigate(`/systems/${hit.systemId}`);
      });

  const systemName = data.summary.find((b) => b.systemId === filters.systemId)?.systemName ?? '';
  const chips = activeFilterChips({ systemName, showSystem: !auditMode, severity: filters.severity, category: filters.category, situation: filters.situation });
  const showFooter = !data.loading && !data.error && data.total > 0;

  return (
    <>
      {auditMode && freshness?.stale === true && <StaleAuditNotice auditedCommit={freshness.auditedCommit} currentCommit={freshness.currentCommit} />}
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

      {!auditMode && data.summary.length > 0 && <SystemSummaryCards summary={data.summary} />}

      <section aria-label="Lista de findings" style={{ background: 'var(--s-surface)', border: '1px solid var(--s-border)' }}>
        <FindingsListBody
          loading={data.loading}
          error={data.error}
          total={data.total}
          auditMode={auditMode}
          auditId={auditId}
          auditSystemName={audit?.systemName}
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
