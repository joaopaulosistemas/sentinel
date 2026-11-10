import { describe, expect, it } from 'vitest';
import type { IPathCheck } from '../types';
import { shouldFallBackToCurrentBranch, staysOnPage } from './StartAuditModal';

const check = (over: Partial<IPathCheck> = {}): IPathCheck => ({
  ok: true, message: null, defaultBranch: 'develop', branchFound: false, fileCount: 10, uncommittedFiles: 0, ...over,
});

describe('branch inicial de pasta local', () => {
  it('usa a branch atual da pasta quando a pedida não existe', () => {
    expect(shouldFallBackToCurrentBranch(check())).toBe(true);
  });
  it('mantém a branch pedida quando ela existe na pasta', () => {
    expect(shouldFallBackToCurrentBranch(check({ branchFound: true }))).toBe(false);
  });
  it('mantém a branch pedida quando não se sabe se ela existe', () => {
    expect(shouldFallBackToCurrentBranch(check({ branchFound: null }))).toBe(false);
  });
  it('não troca quando a pasta é inválida', () => {
    expect(shouldFallBackToCurrentBranch(check({ ok: false }))).toBe(false);
  });
  it('não troca quando a pasta não informou a branch atual', () => {
    expect(shouldFallBackToCurrentBranch(check({ defaultBranch: null }))).toBe(false);
  });
});

describe('para onde vai a tela depois de iniciar', () => {
  const onStarted = () => {};
  it('fica na página quando quem chamou quer só o aviso', () => {
    expect(staysOnPage(onStarted, false)).toBe(true);
  });
  it('vai para o acompanhamento quando não há aviso para mostrar', () => {
    expect(staysOnPage(undefined, false)).toBe(false);
  });
  it('vai para o acompanhamento quando quem chamou pediu para seguir a auditoria, mesmo com aviso', () => {
    expect(staysOnPage(onStarted, true)).toBe(false);
  });
});
