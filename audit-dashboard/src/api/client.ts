import type {
  AuditDiff,
  AuditFreshness,
  AuditRunResponse,
  AuditInsights,
  PacksResponse,
  Category,
  FindingResponse,
  FindingsOverview,
  FixPlan,
  ScoreExplanation,
  FindingStatus,
  Severity,
  PathCheck,
  RepoCheck,
  RuleQuality,
  ClaudeReview,
  EngineConfig,
  PendingAction,
  ReviewMetrics,
  EngineTuning,
  McpCall,
  McpConfig,
  McpConfigRequest,
  McpNewKey,
  ScheduleRequest,
  SettingsResponse,
  Suppression,
  SuppressRequest,
  BatchSuppressRequest,
  BatchSuppressResponse,
  SystemRequest,
  SystemSummaryResponse,
  TrendPoint,
} from '../types';

// URLs relativas: em dev o proxy do Vite e, em produção, o nginx encaminham /api para a API.
// VITE_API_URL só é necessário se a API estiver em outro host (e então exige CORS).
const BASE_URL = import.meta.env.VITE_API_URL || '';

async function apiFetch<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json', ...options?.headers },
    ...options,
    // Sem limite, uma API travada deixaria a tela esperando para sempre.
    signal: options?.signal ?? AbortSignal.timeout(30_000),
  });
  if (!res.ok) {
    // A API devolve mensagens úteis (validação, regra de negócio); mostra em vez de só o código HTTP.
    let detail = '';
    let raw = '';
    try {
      raw = await res.text();
      const body = JSON.parse(raw);
      detail = body?.message || body?.title || body?.detail || '';
      if (!detail && Array.isArray(body?.violations)) {
        detail = body.violations.map((v: { message?: string }) => v.message).filter(Boolean).join('; ');
      }
    } catch {
      // Corpo que não é JSON: se for um texto curto, é a própria mensagem do servidor.
      if (raw && raw.length <= 300 && !raw.trimStart().startsWith('<')) detail = raw.trim();
    }
    throw new Error(detail || `HTTP ${res.status}: ${res.statusText}`);
  }
  // 204 e respostas aceitas sem corpo (ex.: 202 do reinício da API) não têm JSON para ler.
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  if (text.trim() === '') return undefined as T;
  return JSON.parse(text) as T;
}

export const api = {
  getSystems: () => apiFetch<SystemSummaryResponse[]>('/api/v1/systems'),

  getSystem: (id: number) =>
    apiFetch<SystemSummaryResponse>(`/api/v1/systems/${id}`),

  updateSchedule: (systemId: number, body: ScheduleRequest) =>
    apiFetch<SystemSummaryResponse>(`/api/v1/systems/${systemId}/schedule`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),

  getPacks: () => apiFetch<PacksResponse>('/api/v1/packs'),

  getInsights: (auditId: number) => apiFetch<AuditInsights>(`/api/v1/audits/${auditId}/insights`),

  updateProfile: (systemId: number, profile: string) =>
    apiFetch<SystemSummaryResponse>(`/api/v1/systems/${systemId}/profile`, {
      method: 'PUT',
      body: JSON.stringify({ profile }),
    }),

  getRuleQuality: () => apiFetch<RuleQuality[]>('/api/v1/rules/quality'),

  getAuditRuns: (systemId: number) =>
    apiFetch<AuditRunResponse[]>(`/api/v1/systems/${systemId}/audits`),

  getRecentAudits: (limit = 8) => apiFetch<AuditRunResponse[]>(`/api/v1/audits?limit=${limit}`),

  cancelAudit: (id: number) =>
    apiFetch<AuditRunResponse>(`/api/v1/audits/${id}/cancel`, { method: 'POST' }),

  checkRepo: (repoUrl: string, branch?: string, access?: { systemId?: number; gitToken?: string; gitTokenHosts?: string }) =>
    apiFetch<RepoCheck>('/api/v1/systems/check-repo', {
      method: 'POST',
      body: JSON.stringify({
        repoUrl,
        branch: branch || undefined,
        systemId: access?.systemId,
        gitToken: access?.gitToken || undefined,
        gitTokenHosts: access?.gitTokenHosts || undefined,
      }),
    }),

  /** A pasta local só está disponível quando o servidor define SENTINEL_LOCAL_ROOT. */
  getLocalSourceEnabled: () => apiFetch<{ enabled: boolean }>('/api/v1/systems/local-source'),

  checkPath: (path: string, branch?: string) =>
    apiFetch<PathCheck>('/api/v1/systems/check-path', {
      method: 'POST',
      body: JSON.stringify({ path, branch: branch || undefined }),
    }),

  getAudit: (id: number) => apiFetch<AuditRunResponse>(`/api/v1/audits/${id}`),

  getFindings: (
    auditId: number,
    params?: {
      severity?: Severity;
      category?: Category;
      status?: FindingStatus;
      includeSuppressed?: boolean;
      onlyNew?: boolean;
    },
  ) => {
    const qs = params
      ? new URLSearchParams(
          Object.fromEntries(
            Object.entries(params)
              .filter(([, v]) => v !== undefined && v !== false)
              .map(([k, v]) => [k, String(v)]),
          ) as Record<string, string>,
        ).toString()
      : '';
    const url = `/api/v1/audits/${auditId}/findings${qs ? `?${qs}` : ''}`;
    return apiFetch<FindingResponse[]>(url);
  },

  getFixPlan: (auditId: number) => apiFetch<FixPlan>(`/api/v1/audits/${auditId}/fix-plan`),
  getScoreExplanation: (auditId: number, without: string[]) =>
    apiFetch<ScoreExplanation>(`/api/v1/audits/${auditId}/score-explanation${without.length > 0 ? `?without=${encodeURIComponent(without.join(','))}` : ''}`),

  getFindingsOverview: (params: {
    systemId?: number;
    /** Várias severidades de uma vez (ex.: crítica e alta) */
    severity?: Severity[];
    category?: Category;
    status?: FindingStatus;
    onlyNew?: boolean;
    includeSuppressed?: boolean;
    limit: number;
    offset: number;
  }) => {
    const qs = new URLSearchParams();
    for (const [k, v] of Object.entries(params)) {
      if (v === undefined || v === false) continue;
      if (Array.isArray(v)) v.forEach((item) => qs.append(k, String(item)));
      else qs.append(k, String(v));
    }
    return apiFetch<FindingsOverview>(`/api/v1/findings?${qs.toString()}`);
  },

  getDiff: (auditId: number) => apiFetch<AuditDiff>(`/api/v1/audits/${auditId}/diff`),

  getFreshness: (auditId: number) => apiFetch<AuditFreshness>(`/api/v1/audits/${auditId}/freshness`),

  getTrend: (systemId: number, limit = 30) =>
    apiFetch<TrendPoint[]>(`/api/v1/systems/${systemId}/trend?limit=${limit}`),

  getSuppressions: (systemId: number) =>
    apiFetch<Suppression[]>(`/api/v1/systems/${systemId}/suppressions`),

  suppressFinding: (findingId: number, body: SuppressRequest) =>
    apiFetch<Suppression>(`/api/v1/findings/${findingId}/suppress`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  suppressFindings: (body: BatchSuppressRequest) =>
    apiFetch<BatchSuppressResponse>('/api/v1/findings/suppress-batch', {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  removeSuppression: (systemId: number, suppressionId: number) =>
    apiFetch<void>(`/api/v1/systems/${systemId}/suppressions/${suppressionId}`, { method: 'DELETE' }),

  triggerAudit: (body: {
    systemId: number;
    branch: string;
    /** Só como reserva: com login, a API grava quem está autenticado. */
    triggeredBy?: string;
  }) =>
    apiFetch<AuditRunResponse>('/api/v1/audits', {
      method: 'POST',
      body: JSON.stringify({ triggeredBy: 'dashboard', ...body }),
    }),

  createSystem: (body: SystemRequest) =>
    apiFetch<SystemSummaryResponse>('/api/v1/systems', {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  updateSystem: (id: number, body: SystemRequest) =>
    apiFetch<SystemSummaryResponse>(`/api/v1/systems/${id}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),

  deleteSystem: (id: number) => apiFetch<void>(`/api/v1/systems/${id}`, { method: 'DELETE' }),

  // Acesso do Claude (MCP) a um sistema. A chave só aparece na resposta que a cria.
  getMcp: (systemId: number) => apiFetch<McpConfig>(`/api/v1/mcp-access/${systemId}`),

  updateMcp: (systemId: number, body: McpConfigRequest) =>
    apiFetch<McpConfig>(`/api/v1/mcp-access/${systemId}`, { method: 'PUT', body: JSON.stringify(body) }),

  newMcpKey: (systemId: number) => apiFetch<McpNewKey>(`/api/v1/mcp-access/${systemId}/key`, { method: 'POST' }),

  revokeMcpKey: (systemId: number) => apiFetch<void>(`/api/v1/mcp-access/${systemId}/key`, { method: 'DELETE' }),

  getEngineConfig: (systemId: number) => apiFetch<EngineConfig>(`/api/v1/engine-config/${systemId}`),

  updateEngineConfig: (systemId: number, body: EngineTuning) =>
    apiFetch<EngineConfig>(`/api/v1/engine-config/${systemId}`, { method: 'PUT', body: JSON.stringify(body) }),

  getSettings: () => apiFetch<SettingsResponse>('/api/v1/settings'),

  /** Grava só as chaves informadas; valor null devolve a configuração ao ambiente. */
  saveSettings: (values: Record<string, string | null>) =>
    apiFetch<SettingsResponse>('/api/v1/settings', { method: 'PUT', body: JSON.stringify({ values }) }),

  resetSetting: (key: string) => apiFetch<SettingsResponse>(`/api/v1/settings/${encodeURIComponent(key)}`, { method: 'DELETE' }),

  restartApi: () => apiFetch<void>('/api/v1/settings/restart', { method: 'POST' }),

  getReviewMetrics: (systemId: number) => apiFetch<ReviewMetrics>(`/api/v1/claude-review/${systemId}/metrics`),

  getClaudeReview: (systemId: number) => apiFetch<ClaudeReview>(`/api/v1/claude-review/${systemId}`),

  reviewAction: (systemId: number, path: string, body?: unknown) =>
    apiFetch<ClaudeReview>(`/api/v1/claude-review/${systemId}/${path}`, {
      method: 'POST',
      body: JSON.stringify(body ?? {}),
    }),

  getPendingActions: (systemId: number) => apiFetch<PendingAction[]>(`/api/v1/pending-actions/${systemId}`),

  decidePendingAction: (systemId: number, actionId: number, decision: 'approve' | 'reject') =>
    apiFetch<PendingAction[]>(`/api/v1/pending-actions/${systemId}/${actionId}/${decision}`, { method: 'POST', body: '{}' }),

  getMcpCalls: (systemId: number, limit = 20) =>
    apiFetch<McpCall[]>(`/api/v1/mcp-access/${systemId}/calls?limit=${limit}`),
};
