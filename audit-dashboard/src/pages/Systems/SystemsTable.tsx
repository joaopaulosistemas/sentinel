import { useNavigate } from 'react-router-dom';
import type { SystemSummaryResponse } from '../../types';
import { scoreColor } from '../../utils/systemAttention';
import { repoSlug, scheduleText, stackShort } from './systemForm';

interface Props {
  systems: SystemSummaryResponse[];
  /** Sistema aberto no pop-up, para destacar a linha. */
  selectedId: number | null;
  onAudit: (s: SystemSummaryResponse) => void;
  onEdit: (s: SystemSummaryResponse) => void;
  onDelete: (s: SystemSummaryResponse) => void;
}

const mono = "'IBM Plex Mono', ui-monospace, monospace";
const actionButton = { padding: '8px 10px', background: 'transparent', border: 0, color: 'var(--s-link)', cursor: 'pointer', fontSize: 14 } as const;

/** Tabela dos sistemas cadastrados, com as ações de cada linha. */
export function SystemsTable({ systems, selectedId, onAudit, onEdit, onDelete }: Props) {
  const navigate = useNavigate();
  return (
    <div style={{ overflowX: 'auto' }}>
      <table aria-label="Sistemas cadastrados" style={{ width: '100%', borderCollapse: 'collapse', minWidth: 920 }}>
        <thead>
          <tr style={{ background: 'var(--s-header-row)', textAlign: 'left', fontSize: 12, fontWeight: 600 }}>
            <th style={{ padding: '12px 16px' }}>Sistema</th>
            <th style={{ padding: '12px 8px' }}>Stack</th>
            <th style={{ padding: '12px 8px' }}>Equipe</th>
            <th style={{ padding: '12px 8px' }}>Agendamento</th>
            <th style={{ padding: '12px 8px' }}>Nota</th>
            <th style={{ padding: '12px 16px', textAlign: 'right' }}>Ações</th>
          </tr>
        </thead>
        <tbody>
          {systems.map((s) => {
            const score = s.lastAuditScore === null ? null : Math.round(s.lastAuditScore);
            const selected = selectedId === s.id;
            // Sem o contador (API antiga), a data da última auditoria concluída é o melhor sinal.
            const hasAudits = (s.auditCount ?? (s.lastAuditDate ? 1 : 0)) > 0;
            return (
              <tr key={s.id} style={{ borderBottom: '1px solid var(--s-border)', background: selected ? 'var(--s-selected)' : 'var(--s-surface)' }}>
                <td style={{ padding: '12px 16px', minWidth: 0 }}>
                  <button
                    type="button"
                    onClick={() => navigate(`/systems/${s.id}`)}
                    style={{ background: 'none', border: 0, padding: 0, color: 'var(--s-text)', cursor: 'pointer', fontWeight: 500, fontSize: 'inherit', textAlign: 'left' }}
                  >
                    {s.name}
                  </button>
                  <div style={{ fontSize: 12, color: 'var(--s-text-3)', fontFamily: mono }}>{repoSlug(s.repoUrl)}</div>
                </td>
                <td style={{ padding: '12px 8px' }}>
                  <span style={{ background: 'var(--s-border)', padding: '2px 8px', borderRadius: 10, fontSize: 12 }}>{stackShort[s.stack] ?? s.stack}</span>
                </td>
                <td style={{ padding: '12px 8px', color: 'var(--s-text-2)' }}>{s.team || '—'}</td>
                <td style={{ padding: '12px 8px', color: 'var(--s-text-2)', fontSize: 13 }}>{scheduleText(s)}</td>
                <td style={{ padding: '12px 8px', fontWeight: 600, color: scoreColor(score) }}>{score ?? '-'}</td>
                <td style={{ padding: '8px 16px', textAlign: 'right', whiteSpace: 'nowrap' }}>
                  <button
                    type="button"
                    disabled={!hasAudits}
                    title={hasAudits ? 'Ver as auditorias deste sistema' : 'Este sistema ainda não tem auditorias'}
                    aria-label={`Auditorias de ${s.name}`}
                    onClick={() => navigate(`/systems/${s.id}`)}
                    style={{ ...actionButton, color: hasAudits ? 'var(--s-link)' : 'var(--s-disabled)', cursor: hasAudits ? 'pointer' : 'not-allowed' }}
                  >
                    Auditorias
                  </button>
                  <button type="button" onClick={() => onAudit(s)} style={actionButton}>
                    Auditar
                  </button>
                  <button type="button" onClick={() => onEdit(s)} style={actionButton}>
                    Editar
                  </button>
                  <button
                    type="button"
                    aria-label={`Excluir ${s.name}`}
                    onClick={() => onDelete(s)}
                    style={{ ...actionButton, color: 'var(--s-danger-text)' }}
                  >
                    Excluir
                  </button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
