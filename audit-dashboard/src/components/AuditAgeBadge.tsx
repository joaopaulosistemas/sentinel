import { AUDIT_BADGE_DAYS, type IAuditAge } from '../utils/systemAttention';

interface IProps {
  age: IAuditAge;
}

const ESTILO = {
  attention: { background: 'var(--s-warn-bg)', color: 'var(--s-warn-text)', border: '1px solid var(--s-warn-text)' },
  critical: { background: 'var(--s-bad-bg)', color: 'var(--s-danger-text)', border: '1px solid var(--s-danger-text)' },
  never: { background: 'transparent', color: 'var(--s-text-3)', border: '1px dashed var(--s-text-4)' },
} as const;

export const auditAgeText = (age: IAuditAge): string => (age.days === null ? 'Nunca auditado' : `Sem auditar há ${age.days} dias`);

const auditAgeTitle = (age: IAuditAge): string =>
  age.days === null
    ? 'Este sistema ainda não foi auditado'
    : `Última auditoria concluída há ${age.days} dias (o selo aparece depois de ${AUDIT_BADGE_DAYS})`;

function AlertIcon() {
  return (
    <svg width="12" height="12" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true" focusable="false" style={{ flex: '0 0 auto' }}>
      <path d="M8 1 .5 14.5h15L8 1Zm-.75 5h1.5v4.5h-1.5V6Zm.75 6.25a.9.9 0 1 1 0-1.8.9.9 0 0 1 0 1.8Z" />
    </svg>
  );
}

function ClockIcon() {
  return (
    <svg width="12" height="12" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true" focusable="false" style={{ flex: '0 0 auto' }}>
      <path d="M8 1a7 7 0 1 0 0 14A7 7 0 0 0 8 1Zm0 1.5a5.5 5.5 0 1 1 0 11 5.5 5.5 0 0 1 0-11ZM7.25 4v4.6l3 1.8.75-1.25-2.25-1.35V4h-1.5Z" />
    </svg>
  );
}

/** Selo da lista de Sistemas: aparece quando o sistema está há mais de uma semana sem auditar, ou nunca foi auditado. */
export function AuditAgeBadge({ age }: IProps) {
  return (
    <span
      data-testid="audit-age-badge"
      data-level={age.level}
      title={auditAgeTitle(age)}
      style={{ ...ESTILO[age.level], display: 'inline-flex', alignItems: 'center', gap: 5, fontSize: 11, fontWeight: 600, borderRadius: 8, padding: '1px 7px', lineHeight: '18px', whiteSpace: 'nowrap' }}
    >
      {age.level === 'never' ? <ClockIcon /> : <AlertIcon />}
      {auditAgeText(age)}
    </span>
  );
}
