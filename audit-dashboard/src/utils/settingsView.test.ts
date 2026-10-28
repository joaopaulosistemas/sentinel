import { describe, expect, it } from 'vitest';
import type { SettingView } from '../types';
import {
  SETTINGS_SECTIONS,
  currentValue,
  eslintWarning,
  filterSettings,
  groupSettings,
  hasInvalidChange,
  numberProblem,
  pendingChanges,
  sectionCount,
  shownValue,
} from './settingsView';

function setting(over: Partial<SettingView>): SettingView {
  return {
    key: 'TRIVY_ENABLED', section: 'ferr', group: 'Trivy', label: 'Ligado', help: 'Dependências e infraestrutura', kind: 'bool', def: 'true',
    options: [], unit: '', min: 0, max: 0, editable: true, secret: false, value: 'true', savedValue: null, source: 'ambiente', pendingRestart: false, ...over,
  };
}

const lista = [
  setting({}),
  setting({ key: 'TRIVY_TIMEOUT_SECONDS', label: 'Tempo limite', kind: 'number', value: '900', min: 10, max: 7200, unit: 's' }),
  setting({ key: 'GITLEAKS_ENABLED', group: 'Gitleaks', help: 'Procura segredos' }),
  setting({ key: 'API_KEY', section: 'seg', group: 'Acesso à API', label: 'Chave da API', kind: 'text', value: '********', editable: false, secret: true }),
];

describe('settingsView', () => {
  it('mostra o valor salvo (mesmo aguardando reinício) antes do valor em uso', () => {
    expect(shownValue(setting({ value: 'true', savedValue: 'false' }))).toBe('false');
    expect(shownValue(setting({ value: 'true' }))).toBe('true');
  });

  it('o rascunho vale por cima do mostrado', () => {
    const s = setting({});
    expect(currentValue(s, {})).toBe('true');
    expect(currentValue(s, { TRIVY_ENABLED: 'false' })).toBe('false');
  });

  it('só conta como alteração o que difere do mostrado e é editável', () => {
    const mudancas = pendingChanges(lista, { TRIVY_ENABLED: 'true', TRIVY_TIMEOUT_SECONDS: '120', API_KEY: 'x' });
    expect(mudancas).toEqual({ TRIVY_TIMEOUT_SECONDS: '120' });
  });

  it('sem busca filtra pela seção; com busca procura em todas, por nome, variável ou texto de ajuda', () => {
    expect(filterSettings(lista, 'ferr', '').map((s) => s.key)).toEqual(['TRIVY_ENABLED', 'TRIVY_TIMEOUT_SECONDS', 'GITLEAKS_ENABLED']);
    expect(filterSettings(lista, 'ferr', 'chave').map((s) => s.key)).toEqual(['API_KEY']);
    expect(filterSettings(lista, 'ferr', 'api_key').map((s) => s.key)).toEqual(['API_KEY']);
    expect(filterSettings(lista, 'ferr', 'procura segredos').map((s) => s.key)).toEqual(['GITLEAKS_ENABLED']);
    expect(filterSettings(lista, 'ferr', '   ')).toHaveLength(3);
  });

  it('agrupa mantendo a ordem do catálogo', () => {
    const g = groupSettings(filterSettings(lista, 'ferr', ''));
    expect(g.map((x) => [x.group, x.items.length])).toEqual([['Trivy', 2], ['Gitleaks', 1]]);
  });

  it('conta as configurações por seção', () => {
    expect(sectionCount(lista, 'ferr')).toBe(3);
    expect(sectionCount(lista, 'seg')).toBe(1);
    expect(sectionCount(lista, 'bd')).toBe(0);
    expect(SETTINGS_SECTIONS[0].id).toBe('ferr');
  });

  it('aponta número fora da faixa ou que não é inteiro e bloqueia o salvamento', () => {
    const t = lista[1];
    expect(numberProblem(t, '120')).toBeNull();
    expect(numberProblem(t, '5')).toBe('Use um valor entre 10 e 7200');
    expect(numberProblem(t, '1.5')).toBe('Informe um número inteiro');
    expect(numberProblem(t, '')).toBe('Informe um número inteiro');
    expect(numberProblem(lista[0], 'qualquer')).toBeNull();
    expect(hasInvalidChange(lista, { TRIVY_TIMEOUT_SECONDS: '5' })).toBe(true);
    expect(hasInvalidChange(lista, { TRIVY_TIMEOUT_SECONDS: '60' })).toBe(false);
  });

  it('avisa quando a configuração do ESLint exige um perfil específico', () => {
    expect(eslintWarning('/opt/eslint/eslint.google-angular.config.mjs')).toContain('padrao-google-typescript-angular');
    expect(eslintWarning('/opt/eslint/eslint.padrao-ts.config.mjs')).toContain('padrao-typescript-angular');
    expect(eslintWarning('/opt/eslint/eslint.config.mjs')).toBeNull();
  });
});
