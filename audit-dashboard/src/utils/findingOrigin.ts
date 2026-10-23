/**
 * De qual analisador veio o achado (campo `toolSource`). Nomes conhecidos ganham a grafia de exibição;
 * qualquer outro valor aparece como veio, para nunca esconder uma origem nova.
 */
const KNOWN: Record<string, string> = {
  sentinel: 'Sentinel',
  'sentinel-rules': 'Sentinel',
  semgrep: 'Semgrep',
  sonarqube: 'SonarQube',
  sonar: 'SonarQube',
  checkstyle: 'Checkstyle',
  eslint: 'ESLint',
  stylelint: 'Stylelint',
  jscpd: 'jscpd',
  trivy: 'Trivy',
  gitleaks: 'Gitleaks',
};

export function originLabel(toolSource: string | null | undefined): string {
  const raw = (toolSource ?? '').trim();
  if (raw === '') return 'Origem desconhecida';
  return KNOWN[raw.toLowerCase()] ?? raw;
}
