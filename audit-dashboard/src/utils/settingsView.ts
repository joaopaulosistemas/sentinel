import type { ISettingView } from '../types';

export interface ISettingsSection {
  id: string;
  label: string;
  hint: string;
}

/** Ordem da tela: o que se ajusta no dia a dia primeiro; o que só se muda no ambiente por último. */
export const SETTINGS_SECTIONS: ISettingsSection[] = [
  { id: 'ferr', label: 'Ferramentas de análise', hint: 'Ligar, desligar e limitar cada analisador.' },
  { id: 'pacotes', label: 'Pacotes de regras', hint: 'Pacotes desativados, pacotes de confiança e laboratório de regras.' },
  { id: 'origem', label: 'Origem e evidências', hint: 'Tempos limite do Git, cobertura mínima e agendamento.' },
  { id: 'seg', label: 'Segurança e acesso', hint: 'Somente leitura: mudam no ambiente da instalação.' },
  { id: 'bd', label: 'Banco de dados', hint: 'Somente leitura: mudam no ambiente da instalação.' },
];

export interface ISettingsGroup {
  group: string;
  items: ISettingView[];
}

/** Valor que a tela mostra: o que foi salvo (mesmo aguardando reinício) ou, sem isso, o que está em uso. */
export function shownValue(s: ISettingView): string {
  return s.savedValue ?? s.value;
}

export function currentValue(s: ISettingView, drafts: Record<string, string>): string {
  return drafts[s.key] ?? shownValue(s);
}

/** O campo é editável e tem rascunho diferente do valor mostrado. */
function isChangedDraft(s: ISettingView, draft: string | undefined): draft is string {
  return s.editable && draft !== undefined && draft !== shownValue(s);
}

/** Só o que o usuário mudou de fato: rascunho igual ao valor mostrado não conta. */
export function pendingChanges(settings: ISettingView[], drafts: Record<string, string>): Record<string, string> {
  const out: Record<string, string> = {};
  for (const s of settings) {
    const draft = drafts[s.key];
    if (isChangedDraft(s, draft)) out[s.key] = draft;
  }
  return out;
}

export interface IFilterSettingsInput {
  settings: ISettingView[];
  section: string;
  query: string;
}

/** Com busca, procura em todas as seções; sem busca, mostra só a seção escolhida. */
export function filterSettings({ settings, section, query }: IFilterSettingsInput): ISettingView[] {
  const needle = query.trim().toLowerCase();
  if (!needle) return settings.filter((s) => s.section === section);
  return settings.filter((s) => `${s.label} ${s.key} ${s.help} ${s.group}`.toLowerCase().includes(needle));
}

/** Agrupa mantendo a ordem em que o catálogo as entrega. */
export function groupSettings(list: ISettingView[]): ISettingsGroup[] {
  const groups: ISettingsGroup[] = [];
  for (const s of list) {
    const last = groups[groups.length - 1];
    if (last && last.group === s.group) last.items.push(s);
    else groups.push({ group: s.group, items: [s] });
  }
  return groups;
}

export function sectionCount(settings: ISettingView[], id: string): number {
  return settings.filter((s) => s.section === id).length;
}

export function sectionLabel(id: string): string {
  return SETTINGS_SECTIONS.find((s) => s.id === id)?.label ?? id;
}

/** Mensagem curta do que o valor atual de um campo numérico tem de errado, ou null. */
export function numberProblem(s: ISettingView, value: string): string | null {
  if (s.kind !== 'number') return null;
  if (!/^-?\d+$/.test(value.trim())) return 'Informe um número inteiro';
  const parsed = Number(value);
  if (parsed < s.min || parsed > s.max) return `Use um valor entre ${s.min} e ${s.max}`;
  return null;
}

/** Há algum campo numérico mudado com valor inválido? Impede salvar antes de ir à API. */
export function hasInvalidChange(settings: ISettingView[], changes: Record<string, string>): boolean {
  return settings.some((s) => s.key in changes && numberProblem(s, changes[s.key]) !== null);
}

/** Aviso de combinação ruim entre a configuração do ESLint e o perfil Google/da casa; null se estiver coerente. */
export function eslintWarning(config: string): string | null {
  if (config.includes('google')) {
    return 'Esta configuração segue o Google TypeScript. Use com o perfil padrao-google-typescript-angular; com o perfil da casa os achados entram em conflito (underscore, "public", sufixo Enum, prefixo I).';
  }
  if (config.includes('padrao-ts')) {
    return 'Esta configuração segue o padrão da casa. Use com o perfil padrao-typescript-angular; não combine com o perfil Google TypeScript.';
  }
  return null;
}
