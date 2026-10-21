import {
  Button,
  DatePicker,
  DatePickerInput,
  Dropdown,
  InlineNotification,
  SkeletonText,
  Tile,
} from '@carbon/react';
import { useEffect, useState } from 'react';
import { useAuditRuns } from '../../hooks/useAuditRuns';
import { useSystems } from '../../hooks/useSystems';

interface ReportHistoryEntry {
  id: string;
  systemName: string;
  auditId: number;
  generatedAt: string;
  url: string;
}

const STORAGE_KEY = 'sentinel_report_history';
const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

function loadHistory(): ReportHistoryEntry[] {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]') as ReportHistoryEntry[];
  } catch {
    return [];
  }
}

function saveHistory(entries: ReportHistoryEntry[]): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(entries));
}

export function ReportsPage() {
  const { data: systems, isLoading: loadingSystems } = useSystems();
  const [selectedSystemId, setSelectedSystemId] = useState<number | null>(null);
  const [selectedAuditId, setSelectedAuditId] = useState<number | null>(null);
  const [history, setHistory] = useState<ReportHistoryEntry[]>(loadHistory);
  const [, setDateRange] = useState<[string, string]>(['', '']);

  const { data: auditRuns, isLoading: loadingRuns } = useAuditRuns(selectedSystemId ?? 0);

  // Reset audit selection when system changes
  useEffect(() => {
    setSelectedAuditId(null);
  }, [selectedSystemId]);

  const systemItems =
    systems?.map((s) => ({ id: String(s.id), label: s.name, value: s.id })) ?? [];

  const completedRuns =
    auditRuns
      ?.filter((r) => r.status === 'COMPLETED')
      .map((r) => ({
        id: String(r.id),
        label: `#${r.id} — ${r.branch} — ${new Date(r.startedAt).toLocaleString('pt-BR')}`,
        value: r.id,
      })) ?? [];

  function handleGenerate() {
    if (!selectedAuditId) return;
    const url = `${BASE_URL}/api/v1/audits/${selectedAuditId}/report`;
    window.open(url, '_blank', 'noreferrer');

    const system = systems?.find((s) => s.id === selectedSystemId);
    const entry: ReportHistoryEntry = {
      id: `${selectedAuditId}-${Date.now()}`,
      systemName: system?.name ?? `Sistema #${selectedSystemId ?? ''}`,
      auditId: selectedAuditId,
      generatedAt: new Date().toISOString(),
      url,
    };
    const updated = [entry, ...history].slice(0, 50);
    setHistory(updated);
    saveHistory(updated);
  }

  function clearHistory() {
    setHistory([]);
    saveHistory([]);
  }

  return (
    <div style={{ padding: '2rem' }}>
      <h1 style={{ fontSize: '1.75rem', fontWeight: 600, marginBottom: '1.5rem' }}>
        Relatórios
      </h1>

      {/* Generator form */}
      <Tile style={{ padding: '1.5rem', marginBottom: '2rem' }}>
        <h2 style={{ fontSize: '1.125rem', fontWeight: 600, marginBottom: '1.25rem' }}>
          Gerar Relatório
        </h2>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem', maxWidth: '480px' }}>
          {loadingSystems ? (
            <SkeletonText lineCount={2} />
          ) : (
            <Dropdown
              id="report-system"
              titleText="Sistema"
              label="Selecione um sistema"
              items={systemItems}
              itemToString={(item) => item?.label ?? ''}
              selectedItem={systemItems.find((i) => i.value === selectedSystemId) ?? null}
              onChange={({ selectedItem }) => setSelectedSystemId(selectedItem?.value ?? null)}
            />
          )}

          {selectedSystemId !== null && (
            <>
              {loadingRuns ? (
                <SkeletonText lineCount={1} />
              ) : completedRuns.length === 0 ? (
                <InlineNotification
                  kind="info"
                  title="Nenhuma auditoria concluída"
                  subtitle="Execute uma auditoria para este sistema primeiro."
                />
              ) : (
                <Dropdown
                  id="report-audit"
                  titleText="Auditoria"
                  label="Selecione uma auditoria"
                  items={completedRuns}
                  itemToString={(item) => item?.label ?? ''}
                  selectedItem={completedRuns.find((i) => i.value === selectedAuditId) ?? null}
                  onChange={({ selectedItem }) => setSelectedAuditId(selectedItem?.value ?? null)}
                />
              )}
            </>
          )}

          <DatePicker
            datePickerType="range"
            dateFormat="d/m/Y"
            onChange={(dates) => {
              if (dates.length === 2) {
                setDateRange([
                  dates[0].toISOString().split('T')[0],
                  dates[1].toISOString().split('T')[0],
                ]);
              }
            }}
          >
            <DatePickerInput id="date-start" labelText="Data de início" placeholder="dd/mm/aaaa" />
            <DatePickerInput id="date-end" labelText="Data de fim" placeholder="dd/mm/aaaa" />
          </DatePicker>

          <Button
            disabled={!selectedAuditId}
            onClick={handleGenerate}
          >
            Gerar Relatório HTML
          </Button>
        </div>
      </Tile>

      {/* History */}
      <Tile style={{ padding: '1.5rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <h2 style={{ fontSize: '1.125rem', fontWeight: 600, margin: 0 }}>
            Histórico de Relatórios
          </h2>
          {history.length > 0 && (
            <Button kind="ghost" size="sm" onClick={clearHistory}>
              Limpar histórico
            </Button>
          )}
        </div>

        {history.length === 0 ? (
          <p style={{ color: '#6f6f6f' }}>Nenhum relatório gerado ainda.</p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            {history.map((entry) => (
              <div
                key={entry.id}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '1rem',
                  padding: '0.625rem 0.75rem',
                  background: '#f4f4f4',
                  borderRadius: '4px',
                }}
              >
                <span style={{ fontWeight: 600 }}>{entry.systemName}</span>
                <span style={{ color: '#6f6f6f', fontSize: '0.875rem' }}>
                  Auditoria #{entry.auditId}
                </span>
                <span style={{ color: '#6f6f6f', fontSize: '0.8125rem' }}>
                  {new Date(entry.generatedAt).toLocaleString('pt-BR')}
                </span>
                <a
                  href={entry.url}
                  target="_blank"
                  rel="noreferrer"
                  style={{ marginLeft: 'auto', color: '#0f62fe', fontSize: '0.875rem' }}
                >
                  Abrir novamente
                </a>
              </div>
            ))}
          </div>
        )}
      </Tile>
    </div>
  );
}
