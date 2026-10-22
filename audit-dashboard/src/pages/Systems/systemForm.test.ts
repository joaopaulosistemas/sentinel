import { describe, expect, it } from 'vitest';
import type { SystemSummaryResponse } from '../../types';
import { emptyForm, scheduleChanged, toRequest, toSchedule, validate } from './systemForm';

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

  it('valida a branch e o intervalo do agendamento', () => {
    const ok = { ...emptyForm, name: 'api', repoUrl: 'https://x.com/a/b' };
    expect(validate({ ...ok, scheduledBranch: 'release/1.0', auditIntervalHours: 24 })).toEqual({});
    expect(validate({ ...ok, scheduledBranch: '--upload-pack=x' }).scheduledBranch).toBeDefined();
    expect(validate({ ...ok, auditIntervalHours: -1 }).auditIntervalHours).toBeDefined();
    expect(validate({ ...ok, auditIntervalHours: 721 }).auditIntervalHours).toBeDefined();
    expect(validate({ ...ok, auditIntervalHours: 1.5 }).auditIntervalHours).toBeDefined();
  });

  it('monta o agendamento e só o envia quando mudou', () => {
    const values = { ...emptyForm, auditIntervalHours: 24, scheduledBranch: ' develop ', auditOnPush: true };
    expect(toSchedule(values)).toEqual({ auditIntervalHours: 24, branch: 'develop', auditOnPush: true });

    expect(scheduleChanged(emptyForm, null)).toBe(false);
    expect(scheduleChanged(values, null)).toBe(true);

    const system = { auditIntervalHours: 24, scheduledBranch: 'develop', auditOnPush: true } as SystemSummaryResponse;
    expect(scheduleChanged(values, system)).toBe(false);
    expect(scheduleChanged({ ...values, auditIntervalHours: 12 }, system)).toBe(true);
    expect(scheduleChanged({ ...values, auditOnPush: false }, system)).toBe(true);
  });
});
