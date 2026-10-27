import { describe, expect, it } from 'vitest';
import type { FindingResponse, ReviewSuggestion } from '../types';
import { suggestionFor } from './claudeHint';

const finding = { id: 1, ruleId: 'java.sql.sql-injection', filePath: 'src/main/Repo.java', lineNumber: 42 } as FindingResponse;
const sug = (over: Partial<ReviewSuggestion>): ReviewSuggestion => ({
  id: 1, title: 't', detail: null, ruleRef: 'sql-injection', file: null, line: null, status: 'OPEN', createdAt: '', ...over,
});

describe('suggestionFor', () => {
  it('casa pela regra, mesmo com id curto', () => {
    expect(suggestionFor(finding, [sug({})])?.id).toBe(1);
  });
  it('quando a sugestão traz arquivo e linha, eles precisam bater', () => {
    expect(suggestionFor(finding, [sug({ file: 'Repo.java', line: 42 })])).toBeDefined();
    expect(suggestionFor(finding, [sug({ file: 'Repo.java', line: 7 })])).toBeUndefined();
    expect(suggestionFor(finding, [sug({ file: 'Outro.java' })])).toBeUndefined();
  });
  it('ignora sugestões já tratadas ou sem regra', () => {
    expect(suggestionFor(finding, [sug({ status: 'DISMISSED' })])).toBeUndefined();
    expect(suggestionFor(finding, [sug({ ruleRef: null })])).toBeUndefined();
  });
});
