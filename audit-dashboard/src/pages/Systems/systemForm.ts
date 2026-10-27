import type { LocalSnapshot, ScheduleRequest, SourceType, Stack, SystemRequest, SystemSummaryResponse } from '../../types';

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
  sourceType: SourceType;
  repoUrl: string;
  /** Relativa à raiz de projetos do servidor */
  localPath: string;
  localSnapshot: LocalSnapshot;
  stack: Stack;
  team: string;
  description: string;
  profile: string;
  /** 0 = auditoria periódica desligada */
  auditIntervalHours: number;
  /** Vazio = main */
  scheduledBranch: string;
  auditOnPush: boolean;
  /** Token digitado agora; vazio mantém o salvo */
  gitToken: string;
  /** Hosts que recebem o token; vazio = github.com */
  gitTokenHosts: string;
  clearGitToken: boolean;
  /** Commits baixados para varrer segredos no histórico; 0 ou 1 = clone raso */
  gitHistoryDepth: number;
  /** Segredo do webhook digitado/gerado agora; vazio mantém o salvo */
  webhookSecret: string;
  clearWebhookSecret: boolean;
  /** URL do webhook de notificação digitada agora; vazio mantém a salva */
  notifyUrl: string;
  clearNotifyUrl: boolean;
  /** slack | teams | generic */
  notifyFormat: string;
  notifyMinScoreDrop: number;
  /** CRITICAL | HIGH | MEDIUM | LOW | INFO */
  notifyMinSeverity: string;
  notifyPublicUrl: string;
}

export const emptyForm: SystemFormValues = {
  name: '',
  sourceType: 'GIT_URL',
  repoUrl: '',
  localPath: '',
  localSnapshot: 'COMMIT',
  stack: 'JAVA',
  team: '',
  description: '',
  profile: 'default',
  auditIntervalHours: 0,
  scheduledBranch: '',
  auditOnPush: false,
  gitToken: '',
  gitTokenHosts: '',
  clearGitToken: false,
  gitHistoryDepth: 100,
  webhookSecret: '',
  clearWebhookSecret: false,
  notifyUrl: '',
  clearNotifyUrl: false,
  notifyFormat: 'slack',
  notifyMinScoreDrop: 5,
  notifyMinSeverity: 'HIGH',
  notifyPublicUrl: '',
};

export function formFrom(system: SystemSummaryResponse): SystemFormValues {
  return {
    name: system.name,
    sourceType: system.sourceType === 'LOCAL_PATH' ? 'LOCAL_PATH' : 'GIT_URL',
    repoUrl: system.repoUrl ?? '',
    localPath: system.localPath ?? '',
    localSnapshot: system.localSnapshot === 'TREE' ? 'TREE' : 'COMMIT',
    stack: system.stack,
    team: system.team ?? '',
    description: system.description ?? '',
    profile: system.profile || 'default',
    auditIntervalHours: system.auditIntervalHours ?? 0,
    scheduledBranch: system.scheduledBranch ?? '',
    auditOnPush: !!system.auditOnPush,
    gitToken: '',
    gitTokenHosts: system.gitTokenHosts ?? '',
    clearGitToken: false,
    gitHistoryDepth: system.gitHistoryDepth ?? 100,
    webhookSecret: '',
    clearWebhookSecret: false,
    notifyUrl: '',
    clearNotifyUrl: false,
    notifyFormat: system.notifyFormat || 'slack',
    notifyMinScoreDrop: system.notifyMinScoreDrop ?? 5,
    notifyMinSeverity: system.notifyMinSeverity || 'HIGH',
    notifyPublicUrl: system.notifyPublicUrl ?? '',
  };
}

/** Mesma regra da API: só https, host e caminho. */
const REPO_URL = /^https:\/\/[A-Za-z0-9.-]+(:[0-9]{1,5})?\/[^\s]+$/;

export interface FormErrors {
  name?: string;
  repoUrl?: string;
  localPath?: string;
  scheduledBranch?: string;
  auditIntervalHours?: string;
  gitTokenHosts?: string;
  gitHistoryDepth?: string;
  notifyUrl?: string;
  notifyMinScoreDrop?: string;
  notifyPublicUrl?: string;
}

const HOSTS = /^[A-Za-z0-9.-]+(\s*,\s*[A-Za-z0-9.-]+)*$/;

export const BRANCH_NAME = /^[A-Za-z0-9][A-Za-z0-9._/-]*$/;
export const MAX_INTERVAL_HOURS = 720;

export function validate(values: SystemFormValues): FormErrors {
  const errors: FormErrors = {};
  if (values.name.trim() === '') errors.name = 'Informe o nome do sistema';
  else if (values.name.length > 255) errors.name = 'No máximo 255 caracteres';
  if (values.sourceType === 'LOCAL_PATH') {
    const path = values.localPath.trim();
    if (path === '') errors.localPath = 'Informe a pasta do projeto';
    else if (path.length > 500) errors.localPath = 'No máximo 500 caracteres';
    else if (path.split(/[\\/]/).includes('..')) errors.localPath = 'Não use ".." no caminho';
  } else if (values.repoUrl.trim() === '') errors.repoUrl = 'Informe o endereço do repositório';
  else if (!REPO_URL.test(values.repoUrl.trim())) errors.repoUrl = 'Use um endereço https://, por exemplo https://github.com/org/repositorio';
  if (values.scheduledBranch.trim() !== '' && !BRANCH_NAME.test(values.scheduledBranch.trim())) errors.scheduledBranch = 'Nome de branch inválido';
  if (!Number.isInteger(values.auditIntervalHours) || values.auditIntervalHours < 0 || values.auditIntervalHours > MAX_INTERVAL_HOURS) {
    errors.auditIntervalHours = `Use um número de 0 a ${MAX_INTERVAL_HOURS} horas`;
  }
  if (values.gitTokenHosts.trim() !== '' && !HOSTS.test(values.gitTokenHosts.trim())) {
    errors.gitTokenHosts = 'Use nomes de host separados por vírgula, por exemplo github.com, git.empresa.com';
  }
  if (!Number.isInteger(values.gitHistoryDepth) || values.gitHistoryDepth < 0 || values.gitHistoryDepth > 5000) {
    errors.gitHistoryDepth = 'Use um número inteiro de 0 a 5000';
  }
  if (values.notifyUrl.trim() !== '' && !/^https:\/\/[^\s/]+\/?\S*$/.test(values.notifyUrl.trim())) {
    errors.notifyUrl = 'Use o endereço https:// do webhook do Teams ou do Slack';
  }
  if (!Number.isFinite(values.notifyMinScoreDrop) || values.notifyMinScoreDrop < 0 || values.notifyMinScoreDrop > 100) {
    errors.notifyMinScoreDrop = 'Use um número de 0 a 100';
  }
  if (values.notifyPublicUrl.trim() !== '' && !/^https?:\/\/\S+$/.test(values.notifyPublicUrl.trim())) {
    errors.notifyPublicUrl = 'Use um endereço http:// ou https://';
  }
  return errors;
}

export const notifyFormats = [
  { value: 'slack', label: 'Slack' },
  { value: 'teams', label: 'Microsoft Teams' },
  { value: 'generic', label: 'Genérico (JSON)' },
];

export const notifySeverities = [
  { value: 'CRITICAL', label: 'Crítica' },
  { value: 'HIGH', label: 'Alta' },
  { value: 'MEDIUM', label: 'Média' },
  { value: 'LOW', label: 'Baixa' },
];

export function toRequest(values: SystemFormValues): SystemRequest {
  const local = values.sourceType === 'LOCAL_PATH';
  return {
    name: values.name.trim(),
    sourceType: values.sourceType,
    ...(local
      ? { localPath: values.localPath.trim(), localSnapshot: values.localSnapshot }
      : { repoUrl: values.repoUrl.trim() }),
    stack: values.stack,
    team: values.team.trim(),
    description: values.description.trim(),
    profile: values.profile === 'default' ? '' : values.profile,
    ...(values.gitToken.trim() ? { gitToken: values.gitToken.trim() } : {}),
    gitTokenHosts: values.gitTokenHosts.trim(),
    gitHistoryDepth: values.gitHistoryDepth,
    ...(values.clearGitToken && !values.gitToken.trim() ? { clearGitToken: true } : {}),
    ...(values.webhookSecret.trim() ? { webhookSecret: values.webhookSecret.trim() } : {}),
    ...(values.notifyUrl.trim() ? { notifyUrl: values.notifyUrl.trim() } : {}),
    ...(values.clearNotifyUrl && !values.notifyUrl.trim() ? { clearNotifyUrl: true } : {}),
    notifyFormat: values.notifyFormat,
    notifyMinScoreDrop: values.notifyMinScoreDrop,
    notifyMinSeverity: values.notifyMinSeverity,
    notifyPublicUrl: values.notifyPublicUrl.trim(),
    ...(values.clearWebhookSecret && !values.webhookSecret.trim() ? { clearWebhookSecret: true } : {}),
  };
}

export function toSchedule(values: SystemFormValues): ScheduleRequest {
  return {
    auditIntervalHours: values.auditIntervalHours,
    branch: values.scheduledBranch.trim(),
    // Webhook de push não se aplica a pasta local.
    auditOnPush: values.sourceType === 'LOCAL_PATH' ? false : values.auditOnPush,
  };
}

/** Segredo aleatório de 64 caracteres hexadecimais para o webhook. */
export function newWebhookSecret(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
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

/** Texto curto do agendamento para a lista: "a cada 24 h · main · push", "a cada push · main" ou "manual". */
export function scheduleText(s: Pick<SystemSummaryResponse, 'auditIntervalHours' | 'scheduledBranch' | 'auditOnPush'>): string {
  const parts: string[] = [];
  if (s.auditIntervalHours) parts.push(`a cada ${s.auditIntervalHours} h`);
  else if (s.auditOnPush) parts.push('a cada push');
  if (parts.length === 0) return 'manual';
  if (s.scheduledBranch) parts.push(s.scheduledBranch);
  if (s.auditIntervalHours && s.auditOnPush) parts.push('push');
  return parts.join(' · ');
}

/** Texto de origem na lista: "org/repo" ou "pasta: caminho". */
export function sourceText(s: Pick<SystemSummaryResponse, 'sourceType' | 'repoUrl' | 'localPath'>): string {
  return s.sourceType === 'LOCAL_PATH' ? `pasta: ${s.localPath ?? ''}` : repoSlug(s.repoUrl);
}

/** "org/repo" a partir da URL https do repositório. */
export function repoSlug(url: string | null | undefined): string {
  if (!url) return '';
  return url.replace(/^https?:\/\/[^/]+\//i, '').replace(/\.git$/, '');
}

/** Nome curto da stack na lista. */
export const stackShort: Record<Stack, string> = {
  JAVA: 'Java',
  JAVASCRIPT: 'TypeScript',
  DOTNET: '.NET',
  PYTHON: 'Python',
  COBOL: 'COBOL',
  MIXED: 'Misto',
};
