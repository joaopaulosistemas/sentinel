import { Button, InlineNotification, SkeletonText } from '@carbon/react';
import { useEffect, useState } from 'react';
import { useAuditRuns } from '../../hooks/useAuditRuns';
import { useSystems } from '../../hooks/useSystems';
import { type ReportFormat, reportFormats, reportUrl } from './reportLinks';

interface ReportHistoryEntry {
  id: string;
  systemName: string;
  auditId: number;
  format?: ReportFormat;
  generatedAt: string;
  url: string;
}

const STORAGE_KEY = 'sentinel_report_history';
const BASE_URL = import.meta.env.VITE_API_URL || '';

function loadHistory(): ReportHistoryEntry[] {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]') as ReportHistoryEntry[];
  } catch {
    return [];
  }
}

function saveHistory(entries: ReportHistoryEntry[]): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(entries));
  } catch {
    /* armazenamento indisponível: o histórico só não persiste */
  }
}

const eyebrow = { fontSize: '0.75rem', fontWeight: 600, letterSpacing: '0.04em', textTransform: 'uppercase', color: '#525252' } as const;
const choice = (active: boolean) =>
  ({
    all: 'unset',
    boxSizing: 'border-box',
    cursor: 'pointer',
    padding: '0.625rem 0.75rem',
    border: `1px solid ${active ? '#0f62fe' : '#e0e0e0'}`,
    background: active ? '#edf5ff' : '#fff',
    display: 'grid',
    gap: 2,
  }) as const;

/** Relatórios em três passos: sistema, auditoria e formato. O histórico fica neste navegador. */
export function ReportsPage() {
  const { data: systems, isLoading: loadingSystems } = useSystems();
  const [systemId, setSystemId] = useState<number | null>(null);
  const [auditId, setAuditId] = useState<number | null>(null);
  const [format, setFormat] = useState<ReportFormat>('html');
  const [history, setHistory] = useState<ReportHistoryEntry[]>(loadHistory);

  const { data: runs, isLoading: loadingRuns } = useAuditRuns(systemId ?? 0);
  const completed = (runs ?? []).filter((r) => r.status === 'COMPLETED');

  // Ao escolher o sistema, já deixa selecionada a auditoria mais recente.
  useEffect(() => {
    setAuditId(completed.length > 0 ? completed[0].id : null);
  }, [systemId, runs?.length]);

  function generate() {
    if (!auditId) return;
    const url = reportUrl(BASE_URL, auditId, format);
    window.open(url, '_blank', 'noreferrer');
    const system = systems?.find((s) => s.id === systemId);
    const entry: ReportHistoryEntry = {
      id: `${auditId}-${format}-${Date.now()}`,
      systemName: system?.name ?? `Sistema #${systemId ?? ''}`,
      auditId,
      format,
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
    <div style={{ padding: '2rem', display: 'grid', gap: '1.5rem' }}>
      <div>
        <h1 style={{ fontSize: '1.75rem', fontWeight: 400, marginBottom: '0.25rem' }}>Relatórios</h1>
        <p style={{ color: '#525252' }}>Escolha o sistema, a auditoria e o formato. O relatório abre em uma nova aba.</p>
      </div>

      <section aria-label="Novo relatório" style={{ background: '#fff', border: '1px solid #e0e0e0', padding: '1.25rem 1.5rem' }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: '1.5rem' }}>
          <div style={{ display: 'grid', gap: '0.5rem', alignContent: 'start' }}>
            <div style={eyebrow}>1 · Sistema</div>
            {loadingSystems ? (
              <SkeletonText lineCount={3} />
            ) : (
              (systems ?? []).map((s) => (
                <button key={s.id} type="button" aria-pressed={systemId === s.id} onClick={() => setSystemId(s.id)} style={choice(systemId === s.id)}>
                  <strong style={{ fontWeight: 600 }}>{s.name}</strong>
                  <span style={{ fontSize: '0.75rem', color: '#525252' }}>{s.team || 'Sem time'}</span>
                </button>
              ))
            )}
          </div>

          <div style={{ display: 'grid', gap: '0.5rem', alignContent: 'start' }}>
            <div style={eyebrow}>2 · Auditoria</div>
            {systemId === null ? (
              <p style={{ color: '#525252' }}>Escolha um sistema primeiro.</p>
            ) : loadingRuns ? (
              <SkeletonText lineCount={3} />
            ) : completed.length === 0 ? (
              <InlineNotification kind="info" title="Nenhuma auditoria concluída" subtitle="Execute uma auditoria para este sistema primeiro." hideCloseButton />
            ) : (
              completed.slice(0, 6).map((r, i) => (
                <button key={r.id} type="button" aria-pressed={auditId === r.id} onClick={() => setAuditId(r.id)} style={choice(auditId === r.id)}>
                  <strong style={{ fontWeight: 600 }}>
                    #{r.id}
                    {i === 0 ? ' · mais recente' : ''}
                  </strong>
                  <span style={{ fontSize: '0.75rem', color: '#525252' }}>
                    {r.branch} · {new Date(r.startedAt).toLocaleString('pt-BR')} · nota {r.riskScore?.overall ?? '—'}
                  </span>
                </button>
              ))
            )}
          </div>

          <div style={{ display: 'grid', gap: '0.5rem', alignContent: 'start' }}>
            <div style={eyebrow}>3 · Formato</div>
            {reportFormats.map((f) => (
              <button key={f.id} type="button" aria-pressed={format === f.id} onClick={() => setFormat(f.id)} style={choice(format === f.id)}>
                <strong style={{ fontWeight: 600 }}>{f.label}</strong>
                <span style={{ fontSize: '0.75rem', color: '#525252' }}>{f.hint}</span>
              </button>
            ))}
            <Button disabled={!auditId} onClick={generate} style={{ marginTop: '0.5rem' }}>
              Gerar relatório
            </Button>
          </div>
        </div>
      </section>

      <section aria-label="Histórico" style={{ background: '#fff', border: '1px solid #e0e0e0' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0.875rem 1rem', borderBottom: '1px solid #e0e0e0' }}>
          <h2 style={{ fontSize: '1rem', fontWeight: 600, margin: 0 }}>Gerados neste navegador</h2>
          {history.length > 0 && (
            <Button kind="ghost" size="sm" onClick={clearHistory}>
              Limpar histórico
            </Button>
          )}
        </div>
        {history.length === 0 ? (
          <p style={{ color: '#525252', padding: '1rem' }}>Nenhum relatório gerado ainda.</p>
        ) : (
          history.map((e) => (
            <div key={e.id} style={{ display: 'grid', gridTemplateColumns: 'minmax(160px, 2fr) 110px 150px 170px auto', gap: '0.75rem', padding: '0.75rem 1rem', borderTop: '1px solid #e0e0e0', alignItems: 'center' }}>
              <strong style={{ fontWeight: 500 }}>{e.systemName}</strong>
              <span style={{ color: '#525252' }}>Auditoria #{e.auditId}</span>
              <code style={{ fontSize: '0.75rem' }}>{e.format === 'sbom' ? 'SBOM' : 'HTML'}</code>
              <span style={{ fontSize: '0.8125rem', color: '#525252' }}>{new Date(e.generatedAt).toLocaleString('pt-BR')}</span>
              <a href={e.url} target="_blank" rel="noreferrer" style={{ justifySelf: 'end' }}>
                Abrir novamente
              </a>
            </div>
          ))
        )}
      </section>
    </div>
  );
}
