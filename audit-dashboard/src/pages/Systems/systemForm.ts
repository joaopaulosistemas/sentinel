import type { ScheduleRequest, Stack, SystemRequest, SystemSummaryResponse } from '../../types';

export const stackOptions: Array<{ value: Stack; label: string }> = [
  { value: 'JAVA', label: 'Java' },
  { value: 'JAVASCRIPT', label: 'JavaScript / TypeScript' },
  { value: 'DOTNET', label: '.NET' },
  { value: 'PYTHON', label: 'Python' },
  { value: 'COBOL', label: 'COBOL' },
  { value: 'MIXED', label: 'Misto' },
];

export interface SystemFormValues {
  name: string;
  repoUrl: string;
  stack: Stack;
  team: string;
  description: string;
  profile: string;
  /** 0 = auditoria periódica desligada */
  auditIntervalHours: number;
  /** Vazio = main */
  scheduledBranch: string;
  auditOnPush: boolean;
}

export const emptyForm: SystemFormValues = {
  name: '',
  repoUrl: '',
  stack: 'JAVA',
  team: '',
  description: '',
  profile: 'default',
  auditIntervalHours: 0,
  scheduledBranch: '',
  auditOnPush: false,
};

export function formFrom(system: SystemSummaryResponse): SystemFormValues {
  return {
    name: system.name,
    repoUrl: system.repoUrl ?? '',
    stack: system.stack,
    team: system.team ?? '',
    description: system.description ?? '',
    profile: system.profile || 'default',
    auditIntervalHours: system.auditIntervalHours ?? 0,
    scheduledBranch: system.scheduledBranch ?? '',
    auditOnPush: !!system.auditOnPush,
  };
}

/** Mesma regra da API: só https, host e caminho. */
const REPO_URL = /^https:\/\/[A-Za-z0-9.-]+(:[0-9]{1,5})?\/[^\s]+$/;

export interface FormErrors {
  name?: string;
  repoUrl?: string;
  scheduledBranch?: string;
  auditIntervalHours?: string;
}

export const BRANCH_NAME = /^[A-Za-z0-9][A-Za-z0-9._/-]*$/;
export const MAX_INTERVAL_HOURS = 720;

export function validate(values: SystemFormValues): FormErrors {
  const errors: FormErrors = {};
  if (values.name.trim() === '') errors.name = 'Informe o nome do sistema';
  else if (values.name.length > 255) errors.name = 'No máximo 255 caracteres';
  if (values.repoUrl.trim() === '') errors.repoUrl = 'Informe o endereço do repositório';
  else if (!REPO_URL.test(values.repoUrl.trim())) errors.repoUrl = 'Use um endereço https://, por exemplo https://github.com/org/repositorio';
  if (values.scheduledBranch.trim() !== '' && !BRANCH_NAME.test(values.scheduledBranch.trim())) errors.scheduledBranch = 'Nome de branch inválido';
  if (!Number.isInteger(values.auditIntervalHours) || values.auditIntervalHours < 0 || values.auditIntervalHours > MAX_INTERVAL_HOURS) {
    errors.auditIntervalHours = `Use um número de 0 a ${MAX_INTERVAL_HOURS} horas`;
  }
  return errors;
}

export function toRequest(values: SystemFormValues): SystemRequest {
  return {
    name: values.name.trim(),
    repoUrl: values.repoUrl.trim(),
    stack: values.stack,
    team: values.team.trim(),
    description: values.description.trim(),
    profile: values.profile === 'default' ? '' : values.profile,
  };
}

export function toSchedule(values: SystemFormValues): ScheduleRequest {
  return {
    auditIntervalHours: values.auditIntervalHours,
    branch: values.scheduledBranch.trim(),
    auditOnPush: values.auditOnPush,
  };
}

/** O agendamento só precisa ser enviado se mudou (ou se o sistema é novo e foi configurado). */
export function scheduleChanged(values: SystemFormValues, system: SystemSummaryResponse | null): boolean {
  if (system === null) return values.auditIntervalHours > 0 || values.scheduledBranch.trim() !== '' || values.auditOnPush;
  return (
    values.auditIntervalHours !== (system.auditIntervalHours ?? 0) ||
    values.scheduledBranch.trim() !== (system.scheduledBranch ?? '') ||
    values.auditOnPush !== !!system.auditOnPush
  );
}
