import { describe, expect, it } from 'vitest';
import type { IPathCheck } from '../types';
import { shouldFallBackToCurrentBranch } from './StartAuditModal';

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
