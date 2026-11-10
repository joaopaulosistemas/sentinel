/** Estado mínimo de uma conferência (mutação) de repositório ou pasta. */
interface ICheckState {
  data?: unknown;
  isPending: boolean;
}

/** Ao sair do campo: confere sozinho só se o valor é válido e não há resultado nem conferência em andamento. */
export function shouldCheckOnBlur(validationError: string | undefined, check: ICheckState): boolean {
  const hasValidValue = !validationError;
  const alreadyChecked = Boolean(check.data);
  return hasValidValue && !alreadyChecked && !check.isPending;
}
