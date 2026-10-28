import { useQueries } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { useFindings, useFindingsOverview } from '../../hooks/useFindings';
import type { Category } from '../../types';
import {
  DEFAULT_SEVERITY,
  DEFAULT_SITUATION,
  severityChoices,
  situationChoices,
  type SeverityChoice,
  type SituationChoice,
} from '../../utils/findingsView';
import { claudeNoteFinder, pickRows, reviewSystemIds } from './findingsRows';

/** `?systemId=` válido (inteiro positivo) ou nada. */
function linkedSystemId(raw: string | null): number | undefined {
  const value = Number(raw ?? '0');
  return value > 0 ? value : undefined;
}

/** Filtros, página e opções de exibição da tela de achados. Mudar um filtro volta para a primeira página. */
export function useFindingFilters(searchParams: URLSearchParams) {
  // Com ?aba=reauditoria&systemId=ID (faixa do Claude na execução da auditoria), abre direto nos pedidos desse sistema.
  const [systemId, setSystemId] = useState<number | undefined>(() => linkedSystemId(searchParams.get('systemId')));
  const [severity, setSeverity] = useState<SeverityChoice>(DEFAULT_SEVERITY);
  const [category, setCategory] = useState<Category | undefined>(undefined);
  const [situation, setSituation] = useState<SituationChoice>(searchParams.get('onlyNew') === '1' ? 'new' : DEFAULT_SITUATION);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [grouped, setGrouped] = useState(false);
  const [showResolved, setShowResolved] = useState(false);
  const [showLow, setShowLow] = useState(false);

  const reset = () => setPage(1);
  return {
    systemId, severity, category, situation, page, pageSize, grouped, showResolved, showLow,
    setPage, setGrouped, setShowResolved,
    setSystemId: (value: number | undefined) => { setSystemId(value); reset(); },
    setSeverity: (value: SeverityChoice) => { setSeverity(value); reset(); },
    setCategory: (value: Category | undefined) => { setCategory(value); reset(); },
    setSituation: (value: SituationChoice) => { setSituation(value); reset(); },
    setShowLow: (value: boolean) => { setShowLow(value); reset(); },
    setPageSize: (value: number) => { setPageSize(value); setPage(1); },
    restoreDefaults: () => {
      setSeverity(DEFAULT_SEVERITY);
      setSituation(DEFAULT_SITUATION);
      setCategory(undefined);
      setSystemId(undefined);
      reset();
    },
  };
}

export type FindingFilters = ReturnType<typeof useFindingFilters>;

/** Nenhum filtro mexido: some o botão "Restaurar filtros". */
export function isDefaultFilters(f: FindingFilters): boolean {
  return f.severity === DEFAULT_SEVERITY && f.situation === DEFAULT_SITUATION && f.category === undefined && f.systemId === undefined;
}

/** Consultas da tela e tudo o que se deriva delas (linhas da página, totais, origem de cada achado). */
export function useFindingsData(auditMode: boolean, auditId: number, f: FindingFilters) {
  const sev = severityChoices.find((c) => c.id === f.severity)?.severities ?? [];
  const sit = situationChoices.find((c) => c.id === f.situation) ?? situationChoices[0];
  const single = useFindings(auditId, {
    severity: sev.length === 1 ? sev[0] : undefined,
    category: f.category,
    status: sit.status,
    onlyNew: sit.onlyNew,
    includeSuppressed: sit.includeSuppressed,
  });
  const overview = useFindingsOverview(
    {
      severity: sev.length > 0 ? sev : undefined,
      category: f.category,
      status: sit.status,
      onlyNew: sit.onlyNew,
      includeSuppressed: sit.includeSuppressed,
      systemId: f.systemId,
      limit: f.pageSize,
      offset: (f.page - 1) * f.pageSize,
    },
    !auditMode,
  );
  const rowsView = pickRows({
    auditMode,
    single: single.data ?? [],
    severities: sev,
    showLow: f.showLow,
    page: f.page,
    pageSize: f.pageSize,
    overview: overview.data,
  });
  const { isLoading, error } = auditMode ? single : overview;
  const items = overview.data?.items ?? [];
  const summary = overview.data?.bySystem ?? [];
  return {
    ...rowsView,
    loading: isLoading,
    error,
    items,
    summary,
    itemByFinding: new Map(items.map((i) => [i.finding.id, i])),
    stackBySystem: new Map(summary.map((s) => [s.systemId, s.stack])),
  };
}

export type FindingsData = ReturnType<typeof useFindingsData>;

/** Sugestões abertas do Claude (mesma consulta da aba Revisão), para marcar na lista os achados de que elas falam. */
export function useClaudeNotes(auditMode: boolean, auditSystemId: number | undefined, systemId: number | undefined, data: FindingsData) {
  const ids = reviewSystemIds(auditMode, auditSystemId, systemId, data.items);
  const reviews = useQueries({
    queries: ids.map((id) => ({ queryKey: ['claude-review', id], queryFn: () => api.getClaudeReview(id), staleTime: 30_000 })),
  });
  const suggestionsBySystem = new Map(ids.map((id, i) => [id, reviews[i]?.data?.suggestions ?? []]));
  return claudeNoteFinder(auditMode, auditSystemId, systemId, data.itemByFinding, suggestionsBySystem);
}
