import type { AuditStatus } from '../types';

export type StatusTagType = 'warm-gray' | 'blue' | 'green' | 'red' | 'gray';

/** Rótulo e cor de cada situação de auditoria, iguais em todas as telas. */
export const auditStatusConfig: Record<AuditStatus, { label: string; type: StatusTagType }> = {
  PENDING: { label: 'Na fila', type: 'warm-gray' },
  RUNNING: { label: 'Em execução', type: 'blue' },
  COMPLETED: { label: 'Concluída', type: 'green' },
  FAILED: { label: 'Falhou', type: 'red' },
  CANCELLED: { label: 'Cancelada', type: 'gray' },
};
