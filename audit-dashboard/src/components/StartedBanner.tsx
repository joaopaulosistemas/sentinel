import { Link } from 'react-router-dom';
import type { IAuditRunResponse } from '../types';
import { DashIcon } from './StatusIcons';

const mono = "'IBM Plex Mono', ui-monospace, monospace";

/** Aviso mostrado depois de iniciar uma auditoria, com o atalho para acompanhá-la. */
export function StartedBanner({ started, onClose }: { started: IAuditRunResponse; onClose: () => void }) {
  return (
    <div role="status" style={{ background: 'var(--s-surface)', border: '1px solid var(--s-border)', borderLeft: '4px solid var(--s-link)', padding: '12px 16px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
      <span>
        <strong style={{ fontWeight: 600 }}>Auditoria #{started.id} iniciada</strong> na branch <span style={{ fontFamily: mono }}>{started.branch}</span>. Ela já aparece na lista abaixo e se atualiza sozinha.
      </span>
      <span style={{ display: 'flex', gap: 16, alignItems: 'center' }}>
        <Link to={`/audits/${started.id}`} style={{ fontWeight: 500 }}>Acompanhar execução</Link>
        <button type="button" aria-label="Fechar aviso" onClick={onClose} style={{ background: 'transparent', border: 0, cursor: 'pointer', padding: 4 }}>
          <DashIcon size={14} color="var(--s-text-3)" />
        </button>
      </span>
    </div>
  );
}
