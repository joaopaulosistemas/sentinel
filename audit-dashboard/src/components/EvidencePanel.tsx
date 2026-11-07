import { Button, InlineNotification, Tag } from '@carbon/react';
import { CheckmarkFilled, Upload, WarningAlt } from '@carbon/icons-react';
import { useId, useRef, useState, type DragEvent } from 'react';
import {
  EVIDENCE_TYPES,
  MAX_FILES,
  SNIFF_BYTES,
  classifyEvidence,
  evidenceCoverage,
  type EvidenceKind,
  type IEvidenceType,
} from '../utils/evidenceFiles';
import { PageSection } from './PageSection';

interface IAccepted {
  name: string;
  size: number;
  /** Quando o arquivo foi para outro tipo que o da linha em que foi solto */
  note?: string;
}

interface IRejected {
  name: string;
  reason: string;
  hint: string;
}

const ACCEPT = '.sql,.csv,.json,.xml,.info,.txt,.histo';

const sizeLabel = (bytes: number) => (bytes >= 1048576 ? `${(bytes / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`);

async function readHead(file: File): Promise<string> {
  try {
    return await file.slice(0, SNIFF_BYTES).text();
  } catch {
    return '';
  }
}

/** Seção "Evidências" da página do sistema: guia de o que entregar, valida cada arquivo e recusa o resto. */
export function EvidencePanel({ systemName }: { systemName: string }) {
  const [slots, setSlots] = useState<Partial<Record<EvidenceKind, IAccepted>>>({});
  const [rejected, setRejected] = useState<IRejected[]>([]);
  const [open, setOpen] = useState<EvidenceKind | null>(null);
  const [dragging, setDragging] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const targetRef = useRef<EvidenceKind | null>(null);
  const baseId = useId();

  const coverage = evidenceCoverage(Object.keys(slots) as EvidenceKind[]);

  async function receive(files: File[], target: EvidenceKind | null) {
    const next = { ...slots };
    const refused: IRejected[] = [];
    for (const file of files.slice(0, MAX_FILES)) {
      const verdict = classifyEvidence(file.name, file.size, await readHead(file));
      if (!verdict.ok) {
        refused.push({ name: file.name, reason: verdict.reason, hint: verdict.hint });
        continue;
      }
      const found = EVIDENCE_TYPES.find((t) => t.kind === verdict.kind)!;
      const wanted = target ? EVIDENCE_TYPES.find((t) => t.kind === target) : null;
      const moved = wanted && wanted.kind !== verdict.kind;
      next[verdict.kind] = {
        name: file.name,
        size: file.size,
        note: moved ? `Era para "${wanted.title}", mas o conteúdo é de "${found.title}"; ficou no lugar certo.` : undefined,
      };
    }
    setSlots(next);
    setRejected(refused);
  }

  function pick(target: EvidenceKind | null) {
    targetRef.current = target;
    inputRef.current?.click();
  }

  function onDrop(e: DragEvent<HTMLDivElement>) {
    e.preventDefault();
    setDragging(false);
    void receive(Array.from(e.dataTransfer.files), null);
  }

  const pct = Math.round((coverage.received / coverage.total) * 100);

  return (
    <PageSection
      id="evidencias"
      title="Evidências de execução"
      hint="Consulta lenta, índice sobrando e vazamento de memória só aparecem com dados de execução. O Sentinel não se conecta ao seu banco: você gera os arquivos uma vez e entrega aqui."
      aside={<Tag type="purple" size="sm">Protótipo</Tag>}
    >
      <div
        role="status"
        style={{ display: 'flex', flexWrap: 'wrap', gap: '12px 32px', alignItems: 'center', padding: '16px 20px', background: coverage.received === coverage.total ? 'var(--s-good-bg)' : 'var(--s-warn-bg)', marginBottom: 20 }}
      >
        <div style={{ flex: '1 1 320px' }}>
          <strong style={{ fontSize: 15 }}>{coverage.received} de {coverage.total} tipos de evidência recebidos para {systemName}</strong>
          <div style={{ fontSize: 13, color: 'var(--s-text-2)', marginTop: 2 }}>
            {coverage.received === coverage.total
              ? 'Tudo o que o Analisador de Evidências lê foi entregue.'
              : `O que os ${coverage.total - coverage.received} tipos faltantes cobririam não será verificado na próxima auditoria. Isso não é erro: sem o arquivo, o Sentinel não afirma nada sobre aquela área.`}
          </div>
        </div>
        <div style={{ flex: '0 1 220px', minWidth: 160 }} aria-hidden="true">
          <div style={{ height: 8, background: 'var(--s-border)' }}><div style={{ height: 8, width: `${pct}%`, background: 'var(--s-primary)' }} /></div>
        </div>
      </div>

      <div
        onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        style={{ border: `2px dashed ${dragging ? 'var(--s-primary)' : 'var(--s-border-strong)'}`, background: dragging ? 'var(--s-selected)' : 'var(--s-surface)', padding: '20px 24px', display: 'flex', flexWrap: 'wrap', gap: 16, alignItems: 'center', marginBottom: 20 }}
      >
        <Upload size={28} aria-hidden="true" />
        <div style={{ flex: '1 1 320px' }}>
          <div style={{ fontWeight: 600 }}>Solte aqui os arquivos de evidência</div>
          <div style={{ fontSize: 13, color: 'var(--s-text-3)' }}>
            O Sentinel confere o conteúdo de cada um e coloca no tipo certo. Aceitos: .sql, .csv, .json, .xml, .info e .txt, até 200 MB cada e {MAX_FILES} por sistema. O resto é recusado com o motivo.
          </div>
        </div>
        <Button kind="tertiary" size="md" onClick={() => pick(null)}>Escolher arquivos</Button>
        <input
          ref={inputRef}
          type="file"
          multiple
          accept={ACCEPT}
          aria-label="Escolher arquivos de evidência"
          style={{ display: 'none' }}
          onChange={(e) => {
            const files = Array.from(e.target.files ?? []);
            e.target.value = '';
            void receive(files, targetRef.current);
          }}
        />
      </div>

      {rejected.length > 0 && (
        <InlineNotification
          kind="warning"
          lowContrast
          hideCloseButton
          title={`${rejected.length} ${rejected.length === 1 ? 'arquivo recusado' : 'arquivos recusados'}`}
          subtitle="Estes não serão usados."
          style={{ maxWidth: 'none', marginBottom: 12 }}
        >
          <ul style={{ margin: '8px 0 0', paddingLeft: 18, fontSize: 13 }}>
            {rejected.map((r) => (
              <li key={r.name}><strong>{r.name}:</strong> {r.reason} {r.hint}</li>
            ))}
          </ul>
        </InlineNotification>
      )}

      {(['Banco de dados', 'Testes', 'Memória'] as const).map((area) => {
        const cov = coverage.byArea.find((a) => a.area === area)!;
        return (
          <div key={area} style={{ marginBottom: 20 }}>
            <h3 style={{ margin: '0 0 8px', fontSize: 16, fontWeight: 600 }}>
              {area} <span style={{ fontWeight: 400, fontSize: 13, color: 'var(--s-text-3)' }}>· {cov.received} de {cov.total}</span>
            </h3>
            <ul style={{ listStyle: 'none', margin: 0, padding: 0, borderTop: '1px solid var(--s-border)' }}>
              {EVIDENCE_TYPES.filter((t) => t.area === area).map((t) => (
                <EvidenceRow
                  key={t.kind}
                  type={t}
                  accepted={slots[t.kind]}
                  expanded={open === t.kind}
                  panelId={`${baseId}-${t.kind}`}
                  onToggle={() => setOpen(open === t.kind ? null : t.kind)}
                  onPick={() => pick(t.kind)}
                  onRemove={() => setSlots((s) => { const n = { ...s }; delete n[t.kind]; return n; })}
                />
              ))}
            </ul>
          </div>
        );
      })}

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'center' }}>
        <Button kind="primary" disabled>Entregar ao Sentinel</Button>
        <span style={{ fontSize: 13, color: 'var(--s-text-3)', flex: '1 1 320px' }}>
          Protótipo: a conferência dos arquivos já funciona aqui no navegador. A entrega para a pasta do sistema depende de um endpoint novo na API, que ainda não existe; hoje a pasta é montada à mão (<code>evidencias/&lt;sistema&gt;</code>).
        </span>
      </div>
    </PageSection>
  );
}

interface IRowProps {
  type: IEvidenceType;
  accepted: IAccepted | undefined;
  expanded: boolean;
  panelId: string;
  onToggle: () => void;
  onPick: () => void;
  onRemove: () => void;
}

function EvidenceRow({ type, accepted, expanded, panelId, onToggle, onPick, onRemove }: IRowProps) {
  return (
    <li style={{ borderBottom: '1px solid var(--s-border)', padding: '12px 0' }}>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px 16px', alignItems: 'center' }}>
        <span style={{ display: 'inline-flex' }} title={accepted ? 'Recebido' : 'Não verificado'}>
          {accepted ? <CheckmarkFilled size={20} style={{ color: 'var(--s-success-text)' }} aria-label="Recebido" /> : <WarningAlt size={20} style={{ color: 'var(--s-warn-text)' }} aria-label="Não verificado" />}
        </span>
        <div style={{ flex: '1 1 260px', minWidth: 0 }}>
          <div style={{ fontWeight: 600, fontSize: 14 }}>{type.title}</div>
          <div style={{ fontSize: 13, color: 'var(--s-text-3)' }}>
            {accepted ? <>{accepted.name} · {sizeLabel(accepted.size)}</> : <>esperado: <code>{type.file}</code> · não verificado</>}
          </div>
        </div>
        <Button kind="ghost" size="sm" aria-expanded={expanded} aria-controls={panelId} onClick={onToggle}>
          {expanded ? 'Ocultar' : 'Como gerar'}
        </Button>
        {accepted ? (
          <Button kind="ghost" size="sm" onClick={onRemove}>Remover</Button>
        ) : (
          <Button kind="tertiary" size="sm" onClick={onPick}>Escolher arquivo</Button>
        )}
      </div>
      {accepted?.note && <div style={{ fontSize: 13, color: 'var(--s-warn-text)', marginTop: 6 }}>{accepted.note}</div>}
      {expanded && (
        <div id={panelId} style={{ marginTop: 10, padding: '12px 16px', background: 'var(--s-layer)', fontSize: 13 }}>
          <p style={{ margin: '0 0 8px' }}><strong>O que o Sentinel aponta:</strong> {type.reveals}</p>
          <pre style={{ margin: '0 0 8px', padding: '8px 12px', background: 'var(--s-surface)', border: '1px solid var(--s-border)', overflowX: 'auto', whiteSpace: 'pre-wrap' }}>{type.howTo}</pre>
          <p style={{ margin: 0, color: 'var(--s-text-3)' }}><strong>Cuidado:</strong> {type.care}</p>
        </div>
      )}
    </li>
  );
}
