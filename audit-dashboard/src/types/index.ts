export type Stack = 'JAVA' | 'JAVASCRIPT' | 'DOTNET' | 'PYTHON' | 'COBOL' | 'MIXED';
export type AuditStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';
export type Severity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO';
export type Category = 'SECURITY' | 'QUALITY' | 'ARCHITECTURE' | 'DEPENDENCY' | 'OBSERVABILITY';
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
  systemName: string;
  branch: string;
  status: AuditStatus;
  riskScore: RiskScoreDto | null;
  findingsSummary: Record<Severity, number>;
  startedAt: string;
  completedAt: string | null;
  errorMessage?: string | null;
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
