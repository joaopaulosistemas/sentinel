import { expect } from 'vitest';
import { axe } from 'vitest-axe';
import * as matchers from 'vitest-axe/matchers';

expect.extend(matchers);

interface IAxeMatchers {
  toHaveNoViolations(): void;
}

/** Falha o teste se a acessibilidade do trecho renderizado tiver violações (axe-core). */
export async function expectNoAxeViolations(container: Element): Promise<void> {
  (expect(await axe(container)) as unknown as IAxeMatchers).toHaveNoViolations();
}
