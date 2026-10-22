import type { Stack, SystemRequest, SystemSummaryResponse } from '../../types';

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
}

export const emptyForm: SystemFormValues = {
  name: '',
  repoUrl: '',
  stack: 'JAVA',
  team: '',
  description: '',
  profile: 'default',
};

export function formFrom(system: SystemSummaryResponse): SystemFormValues {
  return {
    name: system.name,
    repoUrl: system.repoUrl ?? '',
    stack: system.stack,
    team: system.team ?? '',
    description: system.description ?? '',
    profile: system.profile || 'default',
  };
}

/** Mesma regra da API: só https, host e caminho. */
const REPO_URL = /^https:\/\/[A-Za-z0-9.-]+(:[0-9]{1,5})?\/[^\s]+$/;

export interface FormErrors {
  name?: string;
  repoUrl?: string;
}

export function validate(values: SystemFormValues): FormErrors {
  const errors: FormErrors = {};
  if (values.name.trim() === '') errors.name = 'Informe o nome do sistema';
  else if (values.name.length > 255) errors.name = 'No máximo 255 caracteres';
  if (values.repoUrl.trim() === '') errors.repoUrl = 'Informe o endereço do repositório';
  else if (!REPO_URL.test(values.repoUrl.trim())) errors.repoUrl = 'Use um endereço https://, por exemplo https://github.com/org/repositorio';
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
