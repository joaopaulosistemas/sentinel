import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ClaudeReviewPanel } from '../../components/ClaudeReviewPanel';
import { PageShell } from '../../components/PageShell';
import { ReauditPanel } from '../../components/ReauditPanel';
import { useAudit } from '../../hooks/useAuditRuns';
import { useSystems } from '../../hooks/useSystems';
import type { AuditRunResponse, SystemSummaryResponse } from '../../types';
import { findingsCrumbs } from './findingsRows';
import { FindingsTab } from './FindingsTab';
import { PageHeading, TabBar, type AbaId } from './FindingsParts';
import { useFindingFilters } from './useFindingsPage';

/** Sistema da auditoria aberta: pelo id, ou pelo nome quando a resposta ainda não traz o id. */
function findAuditSystemId(audit: AuditRunResponse | undefined, systems: SystemSummaryResponse[]): number | undefined {
  return audit?.systemId ?? systems.find((s) => s.name === audit?.systemName)?.id;
}

/**
 * Achados. Sem auditoria na URL, mostra os de todos os sistemas (última auditoria concluída de cada um) e deixa
 * filtrar por sistema; com `?auditId=`, mostra os daquela auditoria.
 */
export function FindingsPage() {
  const [searchParams] = useSearchParams();
  const auditId = Number(searchParams.get('auditId') ?? '0');
  const auditMode = auditId > 0;
  const filters = useFindingFilters(searchParams);
  const [aba, setAba] = useState<AbaId>(searchParams.get('aba') === 'reauditoria' ? 'reauditoria' : 'achados');
  const { data: systems } = useSystems();
  const { data: audit } = useAudit(auditId);

  // Sistema em foco: o da auditoria aberta (vindo de "Ver todos os achados") ou o escolhido no filtro.
  // As três abas partem dele, então quem escolheu o sistema não precisa escolher de novo.
  const auditSystemId = auditMode ? findAuditSystemId(audit, systems ?? []) : undefined;
  const activeSystemId = auditMode ? auditSystemId : filters.systemId;
  const panelProps = { systems: systems ?? [], initialSystemId: activeSystemId };
  const panelKey = activeSystemId ?? 'sem-sistema';

  return (
    <PageShell crumbs={findingsCrumbs(auditMode, audit, auditId)}>
      <PageHeading auditMode={auditMode} auditId={auditId} systemName={audit?.systemName} />
      <TabBar aba={aba} onSelect={setAba} />
      {aba === 'revisao' && <ClaudeReviewPanel key={panelKey} {...panelProps} />}
      {aba === 'reauditoria' && <ReauditPanel key={panelKey} {...panelProps} />}
      {aba === 'achados' && (
        <FindingsTab
          auditMode={auditMode}
          auditId={auditId}
          audit={audit}
          systems={systems}
          filters={filters}
          auditSystemId={auditSystemId}
          activeSystemId={activeSystemId}
        />
      )}
    </PageShell>
  );
}
