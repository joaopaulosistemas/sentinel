import type { FindingResponse, ReviewSuggestion } from '../types';
import { shortRuleId } from './ruleCatalog';

const samePath = (a: string, b: string) => {
  const x = a.replace(/\\/g, '/').replace(/^\.?\//, '');
  const y = b.replace(/\\/g, '/').replace(/^\.?\//, '');
  return x === y || x.endsWith(`/${y}`) || y.endsWith(`/${x}`);
};

/**
 * Sugestão aberta do Claude que fala deste achado: mesma regra e, quando a sugestão informa, mesmo arquivo e mesma linha.
 * Sem regra, a sugestão não aponta para um achado só (então não casa).
 */
export function suggestionFor(finding: FindingResponse, suggestions: ReviewSuggestion[]): ReviewSuggestion | undefined {
  if (!finding.ruleId) return undefined;
  return suggestions.find(
    (s) =>
      s.status === 'OPEN' &&
      s.ruleRef !== null &&
      shortRuleId(s.ruleRef) === shortRuleId(finding.ruleId) &&
      (s.file === null || samePath(s.file, finding.filePath)) &&
      (s.line === null || s.line === finding.lineNumber),
  );
}
