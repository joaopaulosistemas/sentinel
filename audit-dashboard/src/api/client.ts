import type {
  AuditDiff,
  AuditRunResponse,
  AuditInsights,
  PacksResponse,
  Category,
  FindingResponse,
  FindingsOverview,
  FixPlan,
  FindingStatus,
  Severity,
  RepoCheck,
  RuleQuality,
  ScheduleRequest,
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
    try {
      const body = await res.json();
      detail = body?.message || body?.title || body?.detail || '';
      if (!detail && Array.isArray(body?.violations)) {
        detail = body.violations.map((v: { message?: string }) => v.message).filter(Boolean).join('; ');
      }
    } catch {
      /* corpo vazio ou não JSON */
    }
    throw new Error(detail || `HTTP ${res.status}: ${res.statusText}`);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
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

  checkRepo: (repoUrl: string, branch?: string) =>
    apiFetch<RepoCheck>('/api/v1/systems/check-repo', {
      method: 'POST',
      body: JSON.stringify({ repoUrl, branch: branch || undefined }),
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
    triggeredBy: string;
  }) =>
    apiFetch<AuditRunResponse>('/api/v1/audits', {
      method: 'POST',
      body: JSON.stringify(body),
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
};
