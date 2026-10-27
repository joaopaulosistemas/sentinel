export type Stack = 'JAVA' | 'JAVASCRIPT' | 'DOTNET' | 'PYTHON' | 'COBOL' | 'MIXED';
export type AuditStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
export type Severity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO';
export type Category = 'SECURITY' | 'QUALITY' | 'ARCHITECTURE' | 'DEPENDENCY' | 'OBSERVABILITY' | 'PERFORMANCE';
export type FindingStatus = 'OPEN' | 'ACKNOWLEDGED' | 'FIXED' | 'SUPPRESSED';
export type SuppressionReason = 'FALSE_POSITIVE' | 'ACCEPTED_RISK' | 'WONT_FIX';

export interface RiskScoreDto {
  overall: number;
  security: number;
  quality: number;
  architecture: number;
  dependencies: number;
  observability: number;
  performance?: number | null;
}

export interface SystemSummaryResponse {
  id: number;
  name: string;
  stack: Stack;
  team: string;
  lastAuditScore: number | null;
  lastAuditDate: string | null;
  openCritical: number;
  openHigh: number;
  auditIntervalHours?: number | null;
  scheduledBranch?: string | null;
  auditOnPush?: boolean | null;
  /** Perfil de regras do sistema; vazio/nulo = "default" */
  profile?: string | null;
  repoUrl?: string | null;
  description?: string | null;
  /** Nota da última auditoria completa menos a anterior; nulo com menos de duas */
  scoreDelta?: number | null;
  /** Quantas auditorias o sistema já tem, em qualquer situação */
  auditCount?: number | null;
  /** Há token Git salvo (o token em si nunca vem da API) */
  gitTokenSet?: boolean | null;
  /** Hosts que recebem o token, separados por vírgula */
  gitTokenHosts?: string | null;
  /** Há segredo de webhook salvo (o segredo em si nunca vem da API) */
  webhookSecretSet?: boolean | null;
  /** Há URL de notificação salva (a URL em si nunca vem da API) */
  /** Commits do histórico baixados no clone; vazio = 100, 0/1 = clone raso */
  gitHistoryDepth?: number | null;
  notifyUrlSet?: boolean | null;
  notifyFormat?: string | null;
  notifyMinScoreDrop?: number | null;
  notifyMinSeverity?: string | null;
  notifyPublicUrl?: string | null;
}

export interface SystemRequest {
  name: string;
  description: string;
  repoUrl: string;
  stack: Stack;
  team: string;
  profile?: string;
  /** Vazio/ausente mantém o token salvo */
  gitToken?: string;
  gitTokenHosts?: string;
  clearGitToken?: boolean;
  /** Vazio/ausente mantém o segredo salvo */
  webhookSecret?: string;
  clearWebhookSecret?: boolean;
  /** Vazio/ausente mantém a URL salva */
  gitHistoryDepth?: number;
  notifyUrl?: string;
  clearNotifyUrl?: boolean;
  notifyFormat?: string;
  notifyMinScoreDrop?: number;
  notifyMinSeverity?: string;
  notifyPublicUrl?: string;
}

export interface ProfileInfo {
  id: string;
  name: string;
  description: string | null;
  extendsId: string | null;
  packs: string[] | null;
  ruleOverrides: number;
  thresholds: Record<string, number>;
}

export interface PackInfo {
  id: string;
  name: string;
  version: string;
  description: string | null;
  enabledByDefault: boolean;
  semgrepRules: number;
  remediations: number;
}

export interface PacksResponse {
  packs: PackInfo[];
  profiles: ProfileInfo[];
  rejected: Record<string, string[]>;
  warnings: string[];
  loadedAt: string;
}

export interface ScheduleRequest {
  auditIntervalHours: number;
  branch: string;
  auditOnPush: boolean;
}

export interface AuditRunResponse {
  id: number;
  systemId?: number;
  systemName: string;
  branch: string;
  status: AuditStatus;
  riskScore: RiskScoreDto | null;
  findingsSummary: Record<Severity, number>;
  startedAt: string;
  completedAt: string | null;
  errorMessage?: string | null;
  /** Só vem enquanto a auditoria está em execução */
  progress?: AuditProgress | null;
  triggeredBy?: string | null;
  profile?: string | null;
  commitSha?: string | null;
  linesOfCode?: number | null;
  /** Achados ativos (sem os suprimidos) */
  totalFindings?: number | null;
  /** Comparação com a auditoria anterior; só vem em auditorias concluídas que têm anterior */
  newFindings?: number | null;
  resolvedFindings?: number | null;
  previousAuditId?: number | null;
  scoreDelta?: number | null;
}

export type AuditPhase = 'FILA' | 'CLONE' | 'ANALISE' | 'PONTUACAO' | 'RELATORIO';

export type AnalyzerProgressStatus =
  | 'ESPERANDO'
  | 'EXECUTANDO'
  | 'OK'
  | 'FALHOU'
  | 'INDISPONIVEL'
  | 'NAO_SUPORTADO'
  | 'SUBSTITUIDO'
  | 'DESLIGADO'
  | 'SEM_EVIDENCIA';

export interface AnalyzerProgress {
  name: string;
  status: AnalyzerProgressStatus;
  findings: number;
  durationMs: number;
  note: string | null;
  /** O que a ferramenta verifica, em uma frase */
  description?: string | null;
}

export interface AuditProgress {
  phase: AuditPhase;
  analyzers: AnalyzerProgress[];
  events: Array<{ at: string; message: string }>;
  repoUrl?: string | null;
  filesRead?: number;
  linesOfCode?: number;
  /** Achados parciais por severidade, já com o que as ferramentas concluídas encontraram */
  severities?: Partial<Record<Severity, number>>;
}

export interface FindingResponse {
  id: number;
  category: Category;
  severity: Severity;
  title: string;
  description: string;
  filePath: string;
  lineNumber: number;
  codeSnippet: string | null;
  ruleId: string;
  toolSource: string;
  cveId: string | null;
  aiRecommendation: string | null;
  status: FindingStatus;
  fingerprint?: string | null;
  /** true = não existia na auditoria anterior; null/undefined = sem auditoria anterior para comparar */
  isNew?: boolean | null;
  /** true = regra em modo sombra: aparece, mas não pesa no score nem derruba o pipeline */
  shadow?: boolean | null;
  remediation?: Remediation | null;
}

export interface Remediation {
  summary: string | null;
  before: string | null;
  after: string | null;
  references: string[];
}

export interface AuditDiff {
  auditId: number;
  baseAuditId: number | null;
  hasBaseline: boolean;
  overall: number | null;
  baseOverall: number | null;
  scoreDelta: number | null;
  newFindings: FindingResponse[];
  resolvedFindings: FindingResponse[];
  persistingCount: number;
}

export interface TrendPoint {
  auditId: number;
  branch: string | null;
  completedAt: string | null;
  overall: number;
  security: number;
  quality: number;
  architecture: number;
  dependencies: number;
  observability: number;
  performance?: number | null;
  findings: Record<Severity, number>;
}

export interface Suppression {
  id: number;
  fingerprint: string;
  reason: SuppressionReason;
  justification: string;
  createdBy: string | null;
  createdAt: string;
  expiresAt: string | null;
  active: boolean;
  ruleId: string | null;
  filePath: string | null;
  title: string | null;
}

export interface SuppressRequest {
  reason: SuppressionReason;
  justification: string;
  createdBy?: string;
  expiresAt?: string | null;
}

export interface RuleQuality {
  ruleId: string;
  tool: string | null;
  total: number;
  falsePositive: number;
  acceptedRisk: number;
  falsePositiveRate: number;
  needsReview: boolean;
}

// ---------------------------------------------------------------- auditoria completa (insights)

export type AreaState = 'CRITICO' | 'ATENCAO' | 'SEM_ACHADOS' | 'NAO_VERIFICADO';

export interface DebtItem {
  id: string;
  problem: string;
  area: string;
  areaLabel: string;
  severity: Severity;
  priority: 'P0' | 'P1' | 'P2' | 'P3';
  priorityLabel: string;
  occurrences: number;
  impact: string;
  effort: 'S' | 'M' | 'L';
  effortEstimated: boolean;
  regressionRisk: string | null;
  recommendation: string;
  example: string | null;
}

export interface AreaReport {
  area: string;
  label: string;
  state: AreaState;
  currentState: string;
  bySeverity: Record<Severity, number>;
  total: number;
  problems: string[];
  risks: string[];
  recommendations: string[];
  notVerified: string[];
}

export interface RoadmapPhase {
  number: number;
  name: string;
  goal: string;
  total: number;
  items: DebtItem[];
}

export interface AnalyzerRunInfo {
  name: string;
  status: string;
  findings: number;
  durationMs: number;
  note: string | null;
}

export interface ProjectMapInfo {
  languages: Record<string, number>;
  buildTools: string[];
  frameworks: string[];
  entryPoints: string[];
  ciConfigs: string[];
  dockerfiles: string[];
  iacFiles: string[];
  docs: string[];
  lockfiles: string[];
  sourceFiles: number;
  testFiles: number;
  hasReadme: boolean;
  readmeLines: number;
  hasGitignore: boolean;
  hasLicense: boolean;
  truncated: boolean;
}

export interface AuditInsights {
  executiveSummary: string;
  verdict: string;
  bySeverity: Record<Severity, number>;
  activeFindings: number;
  areas: AreaReport[];
  debtTable: DebtItem[];
  topProblems: DebtItem[];
  topImprovements: DebtItem[];
  quickWins: DebtItem[];
  criticalRisks: DebtItem[];
  roadmap: RoadmapPhase[];
  unverified: string[];
  analyzers: AnalyzerRunInfo[];
  projectMap: ProjectMapInfo | null;
}

// ---------------------------------------------------------------- plano de correção

export interface FixPlanItem {
  ruleId: string | null;
  title: string;
  severity: Severity;
  category: Category;
  occurrences: number;
  /** quanto a nota geral sobe se todas as ocorrências forem corrigidas */
  scoreGain: number;
  exampleFile: string | null;
  exampleLine: number | null;
  howToFix: string | null;
}

export interface FixPlan {
  scoreNow: number;
  scoreIfCriticalFixed: number;
  criticalCount: number;
  items: FixPlanItem[];
}

// ---------------------------------------------------------------- achados de todos os sistemas

export interface FindingsOverview {
  total: number;
  bySystem: Array<{
    systemId: number;
    systemName: string;
    auditId: number;
    count: number;
    stack?: string | null;
    critical?: number;
    high?: number;
    auditedAt?: string | null;
  }>;
  items: Array<{ systemId: number; systemName: string; auditId: number; finding: FindingResponse }>;
}

/** Resultado da verificação rápida de um repositório (sem clonar). */
export interface RepoCheck {
  /** Arquivos versionados no repositório, quando deu para contar */
  fileCount?: number | null;
  reachable: boolean;
  defaultBranch: string | null;
  /** Só vem quando se perguntou por uma branch */
  branchFound: boolean | null;
  message: string | null;
}

export interface BatchSuppressRequest extends SuppressRequest {
  findingIds: number[];
}

export interface BatchSuppressResponse {
  requested: number;
  suppressed: number;
  failures: { findingId: number; message: string }[];
}

// ---- Acesso do Claude (MCP) a um sistema ----

export type McpDataLevel = 'TITLES' | 'FILE_LINE' | 'SENSITIVE';

export interface McpToolState {
  name: string;
  description: string;
  /** true = a ferramenta muda algo; nenhuma ferramenta da fase atual escreve */
  writes: boolean;
  enabled: boolean;
}

export interface McpConfig {
  enabled: boolean;
  hasKey: boolean;
  keyPrefix: string | null;
  keyCreatedAt: string | null;
  dataLevel: McpDataLevel;
  tools: McpToolState[];
}

export interface McpConfigRequest {
  enabled?: boolean;
  dataLevel?: McpDataLevel;
  tools?: string[];
}

/** A chave completa só vem na resposta que a cria. */
export interface McpNewKey {
  key: string;
  keyPrefix: string;
}

export interface McpCall {
  tool: string;
  ok: boolean;
  durationMs: number;
  detail: string | null;
  at: string;
}

/** Ajuste de uma regra de um sistema (por cima do perfil). */
export interface RuleAdjustment {
  id: string;
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO' | null;
  disabled: boolean;
  shadow: boolean | null;
}

export interface EngineTuning {
  rules: RuleAdjustment[];
  thresholds: Record<string, number>;
  enablePacks: string[];
  disablePacks: string[];
  disableAnalyzers: string[];
  /** Caminhos (curinga estilo .gitignore) cujos achados ficam fora da auditoria. Ausente em APIs antigas. */
  ignorePaths?: string[];
}

export interface EnginePackState {
  id: string;
  name: string;
  description: string | null;
  inProfile: boolean;
  active: boolean;
}

export interface EngineAnalyzerState {
  name: string;
  description: string;
  disabled: boolean;
}

export interface EngineThresholdState {
  key: string;
  profileValue: number | null;
  override: number | null;
}

export interface EngineConfig {
  profile: string;
  profileDescription: string | null;
  /** Quantos ajustes o sistema tem em relação ao perfil. */
  adjustments: number;
  tuning: EngineTuning;
  profileRules: RuleAdjustment[];
  packs: EnginePackState[];
  analyzers: EngineAnalyzerState[];
  thresholds: EngineThresholdState[];
  knownRules: Array<{ id: string; count: number }>;
  /** Regras declaradas nos pacotes (id, texto, pacote). Ausente em APIs antigas. */
  catalog?: Array<{ id: string; pack: string; message: string | null; severity: string | null; category: string | null }>;
}

export type SuggestionStatus = 'OPEN' | 'ADDRESSED' | 'DISMISSED';

export type RuleStage = 'PROPOSED' | 'REVIEWED' | 'SAMPLE_PASSED' | 'OBSERVING' | 'PROMOTED' | 'DISCARDED';

export interface ReviewSuggestion {
  id: number;
  title: string;
  detail: string | null;
  ruleRef: string | null;
  file: string | null;
  line: number | null;
  status: SuggestionStatus;
  createdAt: string;
}

export interface RuleEvidence {
  hits: number;
  falsePositives: number;
  auditsSinceStart: number;
  precision: number;
}

export interface RuleProposalView {
  id: number;
  slug: string;
  title: string;
  rationale: string | null;
  semgrepYaml: string;
  stage: RuleStage;
  stageLabel: string;
  copiedFromId: number | null;
  sampleNote: string | null;
  observationStartedAt: string | null;
  decisionNote: string | null;
  evidence: RuleEvidence;
  minPrecision: number;
  minHits: number;
  sharedEligible: boolean;
  runsInAudits: boolean;
}

export interface ClaudeReview {
  openSuggestions: number;
  suggestions: ReviewSuggestion[];
  proposals: RuleProposalView[];
}

export type PendingStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXPIRED';

/** Pedido do Claude que só acontece se uma pessoa aprovar. */
export interface PendingAction {
  id: number;
  kind: 'START_AUDIT' | 'SUPPRESS_FINDING';
  summary: string;
  status: PendingStatus;
  createdAt: string;
  expiresAt: string;
  decidedAt: string | null;
  resultNote: string | null;
  auditRunId: number | null;
}

/** Quanto o Claude acerta, segundo o que as pessoas decidiram. Taxas são null enquanto não há decisão. */
export interface ReviewMetrics {
  suggestionsTotal: number;
  suggestionsOpen: number;
  suggestionsAddressed: number;
  suggestionsDismissed: number;
  suggestionAdoption: number | null;
  rulesTotal: number;
  rulesInProgress: number;
  rulesPromoted: number;
  rulesDiscarded: number;
  rulePromotion: number | null;
}
