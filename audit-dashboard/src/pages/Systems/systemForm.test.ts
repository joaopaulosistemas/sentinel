import { describe, expect, it } from 'vitest';
import { emptyForm, toRequest, validate } from './systemForm';

describe('formulário de sistema', () => {
  it('exige nome e repositório', () => {
    expect(validate(emptyForm)).toEqual({
      name: 'Informe o nome do sistema',
      repoUrl: 'Informe o endereço do repositório',
    });
  });

  it('aceita só https e rejeita ssh, http e espaços', () => {
    const ok = { ...emptyForm, name: 'api' };
    expect(validate({ ...ok, repoUrl: 'https://github.com/org/repo' })).toEqual({});
    expect(validate({ ...ok, repoUrl: 'http://github.com/org/repo' }).repoUrl).toBeDefined();
    expect(validate({ ...ok, repoUrl: 'git@github.com:org/repo.git' }).repoUrl).toBeDefined();
    expect(validate({ ...ok, repoUrl: 'https://github.com/org/meu repo' }).repoUrl).toBeDefined();
  });

  it('perfil "default" vai vazio para a API', () => {
    expect(toRequest({ ...emptyForm, name: ' api ', repoUrl: 'https://x.com/a/b' })).toMatchObject({ name: 'api', profile: '' });
    expect(toRequest({ ...emptyForm, profile: 'corporativo-java-ee-angular' }).profile).toBe('corporativo-java-ee-angular');
  });
});
