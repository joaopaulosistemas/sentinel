import { describe, expect, it } from 'vitest';
import type { FindingResponse } from '../types';
import { similarTo } from './SuppressModal';

const f = (id: number, ruleId: string, status = 'OPEN', tool = 'Semgrep') => ({ id, ruleId, status, toolSource: tool }) as FindingResponse;

describe('similarTo', () => {
  it('traz os abertos da mesma regra e ferramenta, fora da seleção', () => {
    const sel = [f(1, 'a')];
    const all = [f(1, 'a'), f(2, 'a'), f(3, 'b'), f(4, 'a', 'SUPPRESSED'), f(5, 'a', 'OPEN', 'Trivy')];
    expect(similarTo(sel, all).map((x) => x.id)).toEqual([2]);
  });
  it('sem regra não há parecidos', () => {
    expect(similarTo([f(1, '')], [f(2, '')])).toEqual([]);
  });
});
