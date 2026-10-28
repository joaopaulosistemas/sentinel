import { useEffect, useState } from 'react';
import { PageShell } from '../../components/PageShell';
import { SpinnerIcon } from '../../components/StatusIcons';
import { useAuditRuns } from '../../hooks/useAuditRuns';
import type { AuditRunResponse, SystemSummaryResponse } from '../../types';
import { useSystems } from '../../hooks/useSystems';
import { whenLabel } from '../../utils/timeAgo';
import {
  type ReportFormat,
  type ReportPeriod,
  type ReportType,
  chosenReportUrl,
  formatsFor,
  reportPeriods,
  reportTitle,
  reportTypes,
} from './reportLinks';

interface ReportHistoryEntry {
  id: string;
  /** Nome pronto ("Executivo · 30 dias"); entradas antigas não têm e são montadas a partir dos campos abaixo */
  title?: string;
  systemName: string;
  auditId?: number;
  format?: ReportFormat;
  type?: ReportType;
  days?: ReportPeriod;
  generatedAt: string;
  url: string;
}

const STORAGE_KEY = 'sentinel_report_history';
const BASE_URL = import.meta.env.VITE_API_URL || '';
const mono = "'IBM Plex Mono', ui-monospace, monospace";
const ALL = 'all';

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

const eyebrow = { fontSize: 12, fontWeight: 600, letterSpacing: '0.04em', textTransform: 'uppercase', color: 'var(--s-text-3)' } as const;
const choice = (active: boolean) =>
  ({
    all: 'unset',
    boxSizing: 'border-box',
    cursor: 'pointer',
    padding: '10px 12px',
    border: `1px solid ${active ? '#0f62fe' : 'var(--s-border)'}`,
    background: active ? 'var(--s-selected)' : 'var(--s-surface)',
    display: 'flex',
    flexDirection: 'column',
    gap: 2,
  }) as const;
const select = { height: 40, padding: '0 12px', border: '1px solid var(--s-border-strong)', background: 'var(--s-surface)', font: 'inherit', fontSize: 14 } as const;

function formatName(f?: ReportFormat): string {
  return f === 'sbom' ? 'SBOM' : f === 'csv' ? 'CSV' : (f ?? 'html').toUpperCase();
}

function entryTitle(e: ReportHistoryEntry): string {
  return e.title ?? reportTitle(e.type ?? 'tecnico', e.days ?? 0, e.auditId ?? null);
}


const stepColumn = { display: 'flex', flexDirection: 'column', gap: 8, alignContent: 'start' } as const;

function TypeStep({ type, onPick }: { type: ReportType; onPick: (t: ReportType) => void }) {
  return (
    <div style={stepColumn}>
      <span style={eyebrow}>1 · Tipo</span>
      {reportTypes.map((t) => (
        <button key={t.id} type="button" aria-pressed={type === t.id} onClick={() => onPick(t.id)} style={choice(type === t.id)}>
          <span style={{ fontWeight: 600 }}>{t.label}</span>
          <span style={{ fontSize: 12, color: 'var(--s-text-3)' }}>{t.hint}</span>
        </button>
      ))}
    </div>
  );
}

interface SystemStepProps {
  systems: SystemSummaryResponse[];
  scope: string;
  days: ReportPeriod;
  latest: AuditRunResponse | undefined;
  noAudit: boolean;
  onScope: (v: string) => void;
  onDays: (v: ReportPeriod) => void;
}

function SystemStep({ systems, scope, days, latest, noAudit, onScope, onDays }: SystemStepProps) {
  return (
    <div style={stepColumn}>
      <span style={eyebrow}>2 · Sistema</span>
      <label htmlFor="report-system" style={{ fontSize: 13, color: 'var(--s-text-3)' }}>
        Escolha o sistema ou todos
      </label>
      <select id="report-system" value={scope} onChange={(e) => onScope(e.target.value)} style={select}>
        <option value={ALL}>Todos os sistemas</option>
        {systems.map((s) => (
          <option key={s.id} value={s.id}>
            {s.name}
          </option>
        ))}
      </select>
      <label htmlFor="report-period" style={{ fontSize: 13, color: 'var(--s-text-3)', marginTop: 8 }}>
        Período
      </label>
      <select id="report-period" value={days} onChange={(e) => onDays(Number(e.target.value) as ReportPeriod)} style={select}>
        {reportPeriods.map((p) => (
          <option key={p.id} value={p.id}>
            {p.label}
          </option>
        ))}
      </select>
      {noAudit && (
        <span role="status" style={{ fontSize: 13, color: 'var(--s-danger-text)' }}>
          Este sistema ainda não tem auditoria concluída. Execute uma auditoria primeiro.
        </span>
      )}
      {latest && (
        <span style={{ fontSize: 12, color: 'var(--s-text-3)' }}>
          Usa a auditoria mais recente: #{latest.id}
          {latest.riskScore?.overall != null ? ` · nota ${Math.round(latest.riskScore.overall)}` : ''}.
        </span>
      )}
    </div>
  );
}

interface FormatStepProps {
  formats: ReturnType<typeof formatsFor>;
  format: ReportFormat;
  url: string | null;
  waiting: boolean;
  onFormat: (f: ReportFormat) => void;
  onGenerate: () => void;
}

function FormatStep({ formats, format, url, waiting, onFormat, onGenerate }: FormatStepProps) {
  return (
    <div style={stepColumn}>
      <span style={eyebrow}>3 · Formato</span>
      {formats.map((f) => (
        <button key={f.id} type="button" aria-pressed={format === f.id} onClick={() => onFormat(f.id)} style={{ ...choice(format === f.id), fontWeight: 500 }}>
          {f.label}
        </button>
      ))}
      <button
        type="button"
        onClick={onGenerate}
        disabled={!url}
        style={{
          height: 44,
          marginTop: 8,
          background: url ? '#0f62fe' : '#c6c6c6',
          color: '#fff',
          border: 0,
          fontSize: 14,
          cursor: url ? 'pointer' : 'not-allowed',
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 8,
        }}
      >
        {waiting && <SpinnerIcon size={14} color="#fff" />}
        Gerar relatório
      </button>
    </div>
  );
}

function HistoryRow({ entry: e, fresh }: { entry: ReportHistoryEntry; fresh: boolean }) {
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'minmax(220px, 3fr) minmax(140px, 2fr) 90px 150px 110px',
        gap: 12,
        padding: '12px 16px',
        borderBottom: '1px solid var(--s-border)',
        alignItems: 'center',
        background: fresh ? 'var(--s-selected)' : 'var(--s-surface)',
      }}
    >
      <span style={{ fontWeight: 500 }}>{entryTitle(e)}</span>
      <span style={{ color: 'var(--s-text-2)' }}>{e.systemName}</span>
      <span style={{ fontFamily: mono, fontSize: 12 }}>{formatName(e.format)}</span>
      <span style={{ fontSize: 13, color: 'var(--s-text-3)' }}>{fresh ? 'agora' : whenLabel(e.generatedAt)}</span>
      <a href={e.url} target="_blank" rel="noreferrer" style={{ textAlign: 'right' }}>
        Abrir
      </a>
    </div>
  );
}

function HistorySection({ history, justAdded, onClear }: { history: ReportHistoryEntry[]; justAdded: string | null; onClear: () => void }) {
  return (
    <section aria-label="Relatórios gerados" style={{ background: 'var(--s-surface)', border: '1px solid var(--s-border)' }}>
      <div style={{ padding: '14px 16px', borderBottom: '1px solid var(--s-border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2 style={{ margin: 0, fontSize: 16, fontWeight: 600 }}>Relatórios gerados</h2>
        {history.length > 0 && (
          <button type="button" onClick={onClear} style={{ background: 'none', border: 0, color: 'var(--s-link)', cursor: 'pointer', font: 'inherit', fontSize: 13 }}>
            Limpar lista
          </button>
        )}
      </div>
      {history.length === 0 ? (
        <p style={{ color: 'var(--s-text-3)', padding: '12px 16px', margin: 0 }}>Nenhum relatório gerado ainda neste navegador.</p>
      ) : (
        history.map((e) => <HistoryRow key={e.id} entry={e} fresh={e.id === justAdded} />)
      )}
    </section>
  );
}

/** Relatórios em três passos: tipo, sistema (ou todos) e formato. A lista de gerados fica neste navegador. */
export function ReportsPage() {
  const { data: systems } = useSystems();
  const [type, setType] = useState<ReportType>('executivo');
  const [scope, setScope] = useState<string>(ALL);
  const [days, setDays] = useState<ReportPeriod>(30);
  const [format, setFormat] = useState<ReportFormat>('pdf');
  const [history, setHistory] = useState<ReportHistoryEntry[]>(loadHistory);
  const [justAdded, setJustAdded] = useState<string | null>(null);

  const systemId = scope === ALL ? null : Number(scope);
  const { data: runs, isLoading: loadingRuns } = useAuditRuns(systemId ?? 0);
  const latest = systemId === null ? undefined : (runs ?? []).find((r) => r.status === 'COMPLETED');
  const noAudit = systemId !== null && !loadingRuns && !latest;
  const formats = formatsFor(type, systemId !== null);

  // O SBOM some quando deixa de valer (tipo ou escopo mudaram): volta para PDF.
  useEffect(() => {
    if (!formats.some((f) => f.id === format)) setFormat('pdf');
  }, [formats, format]);

  const url = chosenReportUrl(BASE_URL, { systemId, auditId: latest?.id ?? null, type, format, days });
  const waiting = systemId !== null && loadingRuns;

  function generate() {
    if (!url) return;
    window.open(url, '_blank', 'noreferrer');
    const system = systems?.find((s) => s.id === systemId);
    const entry: ReportHistoryEntry = {
      id: `${scope}-${type}-${format}-${Date.now()}`,
      title: reportTitle(type, days, latest?.id ?? null),
      systemName: system?.name ?? 'Todos os sistemas',
      auditId: latest?.id,
      format,
      type,
      days,
      generatedAt: new Date().toISOString(),
      url,
    };
    const updated = [entry, ...history].slice(0, 50);
    setHistory(updated);
    saveHistory(updated);
    setJustAdded(entry.id);
  }

  function clearHistory() {
    setHistory([]);
    saveHistory([]);
    setJustAdded(null);
  }

  return (
    <PageShell crumbs={[{ label: 'Início', to: '/' }, { label: 'Relatórios' }]}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        <h1 style={{ margin: 0, fontSize: 26, fontWeight: 400 }}>Relatórios</h1>
        <span style={{ color: 'var(--s-text-3)' }}>Gere em 3 passos: escolha o tipo, o sistema e o formato. O relatório fica guardado na lista abaixo.</span>
      </div>

      <section aria-label="Novo relatório" style={{ background: 'var(--s-surface)', border: '1px solid var(--s-border)', padding: '20px 24px' }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(240px, 100%), 1fr))', gap: 24 }}>
          <TypeStep type={type} onPick={setType} />
          <SystemStep systems={systems ?? []} scope={scope} days={days} latest={latest} noAudit={noAudit} onScope={setScope} onDays={setDays} />
          <FormatStep formats={formats} format={format} url={url} waiting={waiting} onFormat={setFormat} onGenerate={generate} />
        </div>
      </section>

      <HistorySection history={history} justAdded={justAdded} onClear={clearHistory} />
    </PageShell>
  );
}
