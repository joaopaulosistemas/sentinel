import {
  Button,
  DataTableSkeleton,
  Checkbox,
  InlineNotification,
  Modal,
  NumberInput,
  PasswordInput,
  Select,
  SelectItem,
  TextArea,
  TextInput,
} from '@carbon/react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '../../api/client';
import { CheckIcon, CrossIcon, SpinnerIcon } from '../../components/StatusIcons';
import { EngineAnalyzersPanel, EngineRulesPanel } from '../../components/EnginePanel';
import { McpPanel } from '../../components/McpPanel';
import { StartAuditModal } from '../../components/StartAuditModal';
import { usePacks } from '../../hooks/useLifecycle';
import { useSystems } from '../../hooks/useSystems';
import type { SystemSummaryResponse } from '../../types';
import { scoreColor } from '../../utils/systemAttention';
import {
  emptyForm,
  formFrom,
  newWebhookSecret,
  notifyFormats,
  notifySeverities,
  repoSlug,
  scheduleChanged,
  scheduleText,
  stackShort,
  stackOptions,
  toRequest,
  toSchedule,
  validate,
  type SystemFormValues,
} from './systemForm';

/** Cadastro e edição de sistemas: lista com busca e um formulário único para criar ou editar. */
export function SystemsPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { data: systems, isLoading, error } = useSystems();
  const { data: packs } = usePacks();
  const [params, setParams] = useSearchParams();

  const [query, setQuery] = useState('');
  const [editing, setEditing] = useState<SystemSummaryResponse | 'new' | null>(null);
  const [values, setValues] = useState<SystemFormValues>(emptyForm);
  const [touched, setTouched] = useState(false);
  const [auditTarget, setAuditTarget] = useState<SystemSummaryResponse | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<SystemSummaryResponse | null>(null);
  const [tab, setTab] = useState<'cadastro' | 'motor' | 'analisadores' | 'mcp'>('cadastro');

  const errors = validate(values);
  const invalid = Object.keys(errors).length > 0;

  const open = (target: SystemSummaryResponse | 'new') => {
    setEditing(target);
    setTab('cadastro');
    setValues(target === 'new' ? emptyForm : formFrom(target));
    setTouched(false);
    save.reset();
    check.reset();
  };

  // Confere se o endereço responde e qual é a branch padrão, sem clonar.
  const check = useMutation({
    mutationFn: () =>
      api.checkRepo(values.repoUrl.trim(), values.scheduledBranch.trim() || undefined, {
        systemId: editing && editing !== 'new' ? editing.id : undefined,
        gitToken: values.gitToken.trim(),
        gitTokenHosts: values.gitTokenHosts.trim(),
      }),
  });

  const save = useMutation({
    mutationFn: async (vars: { auditNow: boolean }) => {
      const body = toRequest(values);
      const current = editing && editing !== 'new' ? editing : null;
      let system = current ? await api.updateSystem(current.id, body) : await api.createSystem(body);
      if (scheduleChanged(values, current)) {
        system = await api.updateSchedule(system.id, toSchedule(values));
      }
      return { system, auditNow: vars.auditNow };
    },
    onSuccess: ({ system, auditNow }) => {
      void queryClient.invalidateQueries({ queryKey: ['systems'] });
      setEditing(null);
      if (auditNow) setAuditTarget(system);
    },
  });

  // Exclui o sistema e todas as suas auditorias (a API remove tudo junto); só depois da confirmação.
  const remove = useMutation({
    mutationFn: (target: SystemSummaryResponse) => api.deleteSystem(target.id),
    onSuccess: (_data, target) => {
      void queryClient.invalidateQueries({ queryKey: ['systems'] });
      void queryClient.invalidateQueries({ queryKey: ['audits'] });
      queryClient.removeQueries({ queryKey: ['systems', target.id] });
      setDeleteTarget(null);
    },
  });

  // "Editar sistema" na tela do sistema chega aqui com ?edit=ID: abre o painel desse sistema.
  // Com &tab=mcp (faixa do Claude na execução da auditoria), já abre na aba Claude (MCP).
  const editId = params.get('edit');
  const editTab = params.get('tab');
  useEffect(() => {
    if (!editId || !systems) return;
    const target = systems.find((s) => String(s.id) === editId);
    if (target) {
      open(target);
      if (editTab === 'mcp') setTab('mcp');
    }
    setParams({}, { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editId, systems]);

  // Esc fecha o pop-up de cadastro.
  const modalOpen = editing !== null;
  useEffect(() => {
    if (!modalOpen) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setEditing(null);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [modalOpen]);

  if (isLoading) return <DataTableSkeleton columnCount={6} rowCount={5} />;
  if (error) {
    return (
      <InlineNotification
        kind="error"
        title="Erro ao carregar sistemas"
        subtitle={error instanceof Error ? error.message : 'Erro desconhecido'}
      />
    );
  }

  const term = query.trim().toLowerCase();
  const list = [...(systems ?? [])]
    .filter((s) => term === '' || `${s.name} ${s.team} ${s.repoUrl ?? ''}`.toLowerCase().includes(term))
    .sort((a, b) => (a.lastAuditScore ?? -1) - (b.lastAuditScore ?? -1));

  const profiles = packs?.profiles ?? [];
  const isNew = editing === 'new';
  const notifySaved = editing !== null && editing !== 'new' && !!editing.notifyUrlSet;
  const webhookSaved = editing !== null && editing !== 'new' && !!editing.webhookSecretSet;
  const tokenSaved = editing !== null && editing !== 'new' && !!editing.gitTokenSet;
  const profileInfo = profiles.find((p) => p.id === values.profile);
  const mono = "'IBM Plex Mono', ui-monospace, monospace";
  const count = systems?.length ?? 0;
  const num = new Intl.NumberFormat('pt-BR');
  const actionButton = { padding: '8px 10px', background: 'transparent', border: 0, color: 'var(--s-link)', cursor: 'pointer', fontSize: 14 } as const;
  const footerButton = { flex: '1 1 120px', height: 56, border: 0, fontSize: 14, cursor: 'pointer', textAlign: 'left', padding: '0 16px' } as const;

  return (
    <div style={{ padding: '1.5rem 2rem 3rem', display: 'flex', flexDirection: 'column', gap: 24, maxWidth: 1440, margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', gap: 16, flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <h1 style={{ margin: 0, fontSize: 28, fontWeight: 400 }}>Sistemas</h1>
          <span style={{ color: 'var(--s-text-3)' }}>
            {count} {count === 1 ? 'sistema cadastrado' : 'sistemas cadastrados'}. Cadastre, edite o perfil de regras e o agendamento, ou dispare uma auditoria.
          </span>
        </div>
        <button
          type="button"
          onClick={() => open('new')}
          style={{ height: 48, padding: '0 20px', background: '#0f62fe', color: '#fff', border: 0, fontSize: 14, cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: 24 }}
        >
          Novo sistema
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.5" strokeLinecap="round" aria-hidden="true"><path d="M12 5v14M5 12h14" /></svg>
        </button>
      </div>

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 20, alignItems: 'flex-start' }}>
        <section aria-label="Lista de sistemas" style={{ flex: '999 1 640px', minWidth: 0, background: 'var(--s-surface)', border: '1px solid var(--s-border)' }}>
          <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--s-border)', display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: 8, background: 'var(--s-layer)', borderBottom: '1px solid var(--s-border-strong)', padding: '0 12px', height: 40, minWidth: 260 }}>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--s-text-3)" strokeWidth="2" strokeLinecap="round" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="M20 20l-4-4" /></svg>
              <span style={{ position: 'absolute', width: 1, height: 1, overflow: 'hidden', clip: 'rect(0 0 0 0)' }}>Buscar sistema</span>
              <input
                type="search"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Buscar por nome, equipe ou repositório"
                style={{ border: 0, background: 'transparent', outline: 'none', flex: 1, fontSize: 14, minWidth: 0 }}
              />
            </label>
            <span style={{ fontSize: 12, color: 'var(--s-text-3)' }}>Ordenado por nota, menor primeiro</span>
          </div>
          {list.length === 0 ? (
            <div style={{ padding: 16 }}>
              <InlineNotification
                kind="info"
                title={systems?.length ? 'Nenhum sistema encontrado' : 'Nenhum sistema cadastrado'}
                subtitle={systems?.length ? 'Tente outro termo de busca.' : 'Clique em "Novo sistema" para cadastrar o primeiro.'}
                hideCloseButton
              />
            </div>
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table aria-label="Sistemas cadastrados" style={{ width: '100%', borderCollapse: 'collapse', minWidth: 920 }}>
                <thead>
                  <tr style={{ background: 'var(--s-header-row)', textAlign: 'left', fontSize: 12, fontWeight: 600 }}>
                    <th style={{ padding: '12px 16px' }}>Sistema</th>
                    <th style={{ padding: '12px 8px' }}>Stack</th>
                    <th style={{ padding: '12px 8px' }}>Equipe</th>
                    <th style={{ padding: '12px 8px' }}>Agendamento</th>
                    <th style={{ padding: '12px 8px' }}>Nota</th>
                    <th style={{ padding: '12px 16px', textAlign: 'right' }}>Ações</th>
                  </tr>
                </thead>
                <tbody>
                  {list.map((s) => {
                    const score = s.lastAuditScore === null ? null : Math.round(s.lastAuditScore);
                    const selected = editing !== null && editing !== 'new' && editing.id === s.id;
                    // Sem o contador (API antiga), a data da última auditoria concluída é o melhor sinal.
                    const hasAudits = (s.auditCount ?? (s.lastAuditDate ? 1 : 0)) > 0;
                    return (
                      <tr key={s.id} style={{ borderBottom: '1px solid var(--s-border)', background: selected ? 'var(--s-selected)' : 'var(--s-surface)' }}>
                        <td style={{ padding: '12px 16px', minWidth: 0 }}>
                          <button
                            type="button"
                            onClick={() => navigate(`/systems/${s.id}`)}
                            style={{ background: 'none', border: 0, padding: 0, color: 'var(--s-text)', cursor: 'pointer', fontWeight: 500, fontSize: 'inherit', textAlign: 'left' }}
                          >
                            {s.name}
                          </button>
                          <div style={{ fontSize: 12, color: 'var(--s-text-3)', fontFamily: mono }}>{repoSlug(s.repoUrl)}</div>
                        </td>
                        <td style={{ padding: '12px 8px' }}>
                          <span style={{ background: 'var(--s-border)', padding: '2px 8px', borderRadius: 10, fontSize: 12 }}>{stackShort[s.stack] ?? s.stack}</span>
                        </td>
                        <td style={{ padding: '12px 8px', color: 'var(--s-text-2)' }}>{s.team || '—'}</td>
                        <td style={{ padding: '12px 8px', color: 'var(--s-text-2)', fontSize: 13 }}>{scheduleText(s)}</td>
                        <td style={{ padding: '12px 8px', fontWeight: 600, color: scoreColor(score) }}>{score ?? '-'}</td>
                        <td style={{ padding: '8px 16px', textAlign: 'right', whiteSpace: 'nowrap' }}>
                          <button
                            type="button"
                            disabled={!hasAudits}
                            title={hasAudits ? 'Ver as auditorias deste sistema' : 'Este sistema ainda não tem auditorias'}
                            aria-label={`Auditorias de ${s.name}`}
                            onClick={() => navigate(`/systems/${s.id}`)}
                            style={{ ...actionButton, color: hasAudits ? 'var(--s-link)' : 'var(--s-disabled)', cursor: hasAudits ? 'pointer' : 'not-allowed' }}
                          >
                            Auditorias
                          </button>
                          <button type="button" onClick={() => setAuditTarget(s)} style={actionButton}>
                            Auditar
                          </button>
                          <button type="button" onClick={() => open(s)} style={actionButton}>
                            Editar
                          </button>
                          <button
                            type="button"
                            aria-label={`Excluir ${s.name}`}
                            onClick={() => {
                              remove.reset();
                              setDeleteTarget(s);
                            }}
                            style={{ ...actionButton, color: '#da1e28' }}
                          >
                            Excluir
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </section>

        {editing !== null && (
          <div
            style={{ position: 'fixed', inset: 0, zIndex: 9000, background: 'rgba(22,22,22,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16 }}
            onMouseDown={(e) => {
              if (e.target === e.currentTarget) setEditing(null);
            }}
          >
          <div
            role="dialog"
            aria-modal="true"
            aria-label="Editar sistema"
            style={{ width: '70vw', minWidth: 'min(560px, 100%)', maxWidth: '100%', maxHeight: 'calc(100vh - 32px)', overflowY: 'auto', background: 'var(--s-surface)', borderTop: '3px solid #0f62fe', display: 'flex', flexDirection: 'column' }}
          >
            <div style={{ padding: '16px 24px', borderBottom: '1px solid var(--s-border)', display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12 }}>
              <div>
                <div style={{ fontSize: 12, color: 'var(--s-text-3)' }}>{isNew ? 'Cadastrando' : 'Editando'}</div>
                <h2 style={{ margin: '2px 0 0', fontSize: 20, fontWeight: 400 }}>{isNew ? 'Novo sistema' : editing.name}</h2>
              </div>
              <button type="button" aria-label="Fechar" onClick={() => setEditing(null)} style={{ width: 40, height: 40, background: 'transparent', border: 0, cursor: 'pointer' }}>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--s-text)" strokeWidth="2" strokeLinecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" /></svg>
              </button>
            </div>

            <div role="tablist" aria-label="Seções do sistema" style={{ display: 'flex', borderBottom: '1px solid var(--s-border)', padding: '0 12px' }}>
              {([['cadastro', 'Cadastro e Git'], ['motor', 'Motor de regras'], ['analisadores', 'Analisadores'], ['mcp', 'Claude (MCP)']] as const).map(([id, nome]) => {
                const bloqueada = id !== 'cadastro' && isNew;
                return (
                  <button
                    key={id}
                    type="button"
                    role="tab"
                    aria-selected={tab === id}
                    disabled={bloqueada}
                    title={bloqueada ? 'Salve o sistema primeiro' : undefined}
                    onClick={() => setTab(id)}
                    style={{
                      background: 'none',
                      border: 0,
                      borderBottom: tab === id ? '3px solid #0f62fe' : '3px solid transparent',
                      padding: '10px 14px',
                      fontSize: 14,
                      fontWeight: tab === id ? 600 : 400,
                      color: bloqueada ? 'var(--s-disabled)' : 'var(--s-text)',
                      cursor: bloqueada ? 'not-allowed' : 'pointer',
                    }}
                  >
                    {nome}
                  </button>
                );
              })}
            </div>

            {tab === 'motor' && !isNew && <EngineRulesPanel system={editing} />}
            {tab === 'analisadores' && !isNew && <EngineAnalyzersPanel system={editing} />}
            {tab === 'mcp' && !isNew && <McpPanel system={editing} />}

            {tab === 'cadastro' && (
              <>
            <div style={{ padding: '20px 24px', display: 'flex', flexDirection: 'column', gap: 18 }}>
              <TextInput
                id="sys-name"
                labelText="Nome do sistema *"
                value={values.name}
                invalid={touched && !!errors.name}
                invalidText={errors.name}
                onChange={(e) => setValues({ ...values, name: e.target.value })}
              />
              <div style={{ display: 'grid', gap: 8 }}>
                <TextInput
                  id="sys-repo"
                  labelText="Repositório Git (https) *"
                  placeholder="https://github.com/org/repositorio"
                  value={values.repoUrl}
                  invalid={touched && !!errors.repoUrl}
                  invalidText={errors.repoUrl}
                  onChange={(e) => {
                    setValues({ ...values, repoUrl: e.target.value });
                    check.reset();
                  }}
                  onBlur={() => {
                    // Ao sair do campo com um endereço válido, já confere o repositório.
                    if (!validate(values).repoUrl && !check.data && !check.isPending) check.mutate();
                  }}
                />
                <div style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
                  <Button kind="tertiary" size="sm" disabled={!!validate(values).repoUrl || check.isPending} onClick={() => check.mutate()}>
                    {check.isPending ? 'Verificando…' : 'Verificar repositório'}
                  </Button>
                  {check.isPending && (
                    <span role="status" style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, color: 'var(--s-text-3)' }}>
                      <SpinnerIcon size={14} />
                      Conferindo o repositório…
                    </span>
                  )}
                  {check.isError && (
                    <span role="status" style={{ color: 'var(--s-danger-text)', fontSize: 12 }}>
                      Não foi possível verificar agora: {check.error instanceof Error ? check.error.message : 'erro desconhecido'}
                    </span>
                  )}
                  {check.data?.reachable && (
                    <span role="status" style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, color: 'var(--s-success-text)', flexWrap: 'wrap' }}>
                      <CheckIcon size={14} />
                      Alcançável
                      {check.data.defaultBranch && (
                        <>
                          {' · branch padrão: '}
                          <span style={{ fontFamily: mono }}>{check.data.defaultBranch}</span>
                        </>
                      )}
                      {check.data.fileCount != null && ` · ${num.format(check.data.fileCount)} arquivos`}
                      {check.data.branchFound === false ? ` · a branch "${values.scheduledBranch.trim()}" não existe` : ''}
                    </span>
                  )}
                  {check.data && !check.data.reachable && (
                    <span role="status" style={{ display: 'inline-flex', alignItems: 'center', gap: 6, color: 'var(--s-danger-text)', fontSize: 12 }}>
                      <CrossIcon size={14} />
                      {check.data.message ?? 'Repositório inacessível'}
                    </span>
                  )}
                  {check.data?.reachable &&
                    check.data.defaultBranch &&
                    values.scheduledBranch.trim() !== check.data.defaultBranch && (
                      <Button kind="ghost" size="sm" onClick={() => setValues({ ...values, scheduledBranch: check.data?.defaultBranch ?? '' })}>
                        Auditar a branch {check.data.defaultBranch}
                      </Button>
                    )}
                </div>
              </div>
              <fieldset style={{ border: '1px solid var(--s-border)', margin: 0, padding: '14px 16px 16px', display: 'flex', flexDirection: 'column', gap: 14 }}>
                <legend style={{ padding: '0 6px', fontSize: 13, fontWeight: 600 }}>Git: acesso ao repositório</legend>
                <PasswordInput
                  id="sys-git-token"
                  labelText="Token Git (somente leitura)"
                  autoComplete="new-password"
                  placeholder={tokenSaved ? 'Token salvo. Deixe vazio para manter' : 'Opcional; só para repositório privado'}
                  helperText="Fica cifrado no banco e nunca é mostrado de volta. Vale só para este sistema."
                  value={values.gitToken}
                  disabled={values.clearGitToken}
                  onChange={(e) => {
                    setValues({ ...values, gitToken: e.target.value });
                    check.reset();
                  }}
                />
                <TextInput
                  id="sys-git-hosts"
                  labelText="Hosts que recebem o token"
                  placeholder="github.com"
                  helperText="Separe por vírgula. Vazio = github.com. O token nunca é enviado a outro host."
                  value={values.gitTokenHosts}
                  invalid={touched && !!errors.gitTokenHosts}
                  invalidText={errors.gitTokenHosts}
                  onChange={(e) => setValues({ ...values, gitTokenHosts: e.target.value })}
                />
                <NumberInput
                  id="sys-git-depth"
                  label="Commits do histórico"
                  helperText="Quantos commits baixar para achar segredos já removidos do código. 0 ou 1 = clone raso, sem essa varredura."
                  min={0}
                  max={5000}
                  step={50}
                  allowEmpty
                  value={values.gitHistoryDepth}
                  invalid={!!errors.gitHistoryDepth}
                  invalidText={errors.gitHistoryDepth}
                  onChange={(_e, { value }) => setValues({ ...values, gitHistoryDepth: value === '' ? NaN : Number(value) })}
                />
                {tokenSaved && (
                  <Checkbox
                    id="sys-git-clear"
                    labelText="Remover o token salvo"
                    checked={values.clearGitToken}
                    onChange={(_, { checked }) => setValues({ ...values, clearGitToken: checked, gitToken: checked ? '' : values.gitToken })}
                  />
                )}
              </fieldset>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 16 }}>
                <Select
                  id="sys-stack"
                  labelText="Stack"
                  value={values.stack}
                  onChange={(e) => setValues({ ...values, stack: e.target.value as SystemFormValues['stack'] })}
                >
                  {stackOptions.map((o) => (
                    <SelectItem key={o.value} value={o.value} text={o.label} />
                  ))}
                </Select>
                <TextInput id="sys-team" labelText="Equipe" value={values.team} onChange={(e) => setValues({ ...values, team: e.target.value })} />
              </div>
              <Select
                id="sys-profile"
                labelText="Perfil de regras"
                helperText={profileInfo?.description || 'Define quais pacotes de regras valem. Vale a partir da próxima auditoria.'}
                value={values.profile}
                onChange={(e) => setValues({ ...values, profile: e.target.value })}
              >
                <SelectItem value="default" text="default" />
                {profiles
                  .filter((p) => p.id !== 'default')
                  .map((p) => (
                    <SelectItem key={p.id} value={p.id} text={p.id} />
                  ))}
              </Select>
              <TextArea id="sys-desc" labelText="Descrição" rows={2} value={values.description} onChange={(e) => setValues({ ...values, description: e.target.value })} />

              <fieldset style={{ border: '1px solid var(--s-border)', margin: 0, padding: '14px 16px 16px', display: 'flex', flexDirection: 'column', gap: 14 }}>
                <legend style={{ padding: '0 6px', fontSize: 13, fontWeight: 600 }}>Auditoria automática</legend>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 16 }}>
                  <NumberInput
                    id="sys-hours"
                    label="A cada (horas, 0 = manual)"
                    min={0}
                    max={720}
                    step={1}
                    value={values.auditIntervalHours}
                    invalid={!!errors.auditIntervalHours}
                    invalidText={errors.auditIntervalHours}
                    onChange={(_e: unknown, state: { value: string | number }) => setValues({ ...values, auditIntervalHours: Number(state.value) || 0 })}
                  />
                  <TextInput
                    id="sys-branch"
                    labelText="Branch auditada (vazio = main)"
                    value={values.scheduledBranch}
                    invalid={!!errors.scheduledBranch}
                    invalidText={errors.scheduledBranch}
                    onChange={(e) => {
                      setValues({ ...values, scheduledBranch: e.target.value });
                      check.reset();
                    }}
                  />
                </div>
                <Checkbox
                  id="sys-push"
                  labelText="Auditar a cada push (webhook)"
                  helperText="Exige o webhook configurado no repositório, com o segredo abaixo."
                  checked={values.auditOnPush}
                  onChange={(_e, { checked }) => setValues({ ...values, auditOnPush: checked })}
                />
                {values.auditOnPush && (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                    <TextInput
                      id="sys-webhook-secret"
                      labelText="Segredo do webhook"
                      autoComplete="off"
                      placeholder={webhookSaved ? 'Segredo salvo. Deixe vazio para manter' : 'Gere um segredo e cole no repositório'}
                      helperText={`GitHub: Settings > Webhooks > Secret, URL ${window.location.origin}/api/v1/webhooks/github (application/json, evento push). GitLab: Secret token, URL .../api/v1/webhooks/gitlab.`}
                      value={values.webhookSecret}
                      disabled={values.clearWebhookSecret}
                      onChange={(e) => setValues({ ...values, webhookSecret: e.target.value })}
                    />
                    <div style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
                      <Button kind="tertiary" size="sm" disabled={values.clearWebhookSecret} onClick={() => setValues({ ...values, webhookSecret: newWebhookSecret() })}>
                        Gerar segredo
                      </Button>
                      <span style={{ fontSize: 12, color: 'var(--s-text-3)' }}>Copie antes de salvar: depois de salvo, ele não é mostrado de novo.</span>
                    </div>
                    {webhookSaved && (
                      <Checkbox
                        id="sys-webhook-clear"
                        labelText="Remover o segredo salvo"
                        checked={values.clearWebhookSecret}
                        onChange={(_, { checked }) => setValues({ ...values, clearWebhookSecret: checked, webhookSecret: checked ? '' : values.webhookSecret })}
                      />
                    )}
                  </div>
                )}
              </fieldset>

              <fieldset style={{ border: '1px solid var(--s-border)', margin: 0, padding: '14px 16px 16px', display: 'flex', flexDirection: 'column', gap: 14 }}>
                <legend style={{ padding: '0 6px', fontSize: 13, fontWeight: 600 }}>Notificações (Teams, Slack ou genérico)</legend>
                <TextInput
                  id="sys-notify-url"
                  labelText="URL do webhook"
                  autoComplete="off"
                  placeholder={notifySaved ? 'URL salva. Deixe vazio para manter' : 'https://hooks.slack.com/services/…'}
                  helperText="Vazio = sem avisos. Só avisa em falha, queda de nota ou achados graves novos. Fica cifrada e só aceita https público."
                  value={values.notifyUrl}
                  disabled={values.clearNotifyUrl}
                  invalid={touched && !!errors.notifyUrl}
                  invalidText={errors.notifyUrl}
                  onChange={(e) => setValues({ ...values, notifyUrl: e.target.value })}
                />
                {notifySaved && (
                  <Checkbox
                    id="sys-notify-clear"
                    labelText="Remover a URL salva (desliga os avisos)"
                    checked={values.clearNotifyUrl}
                    onChange={(_, { checked }) => setValues({ ...values, clearNotifyUrl: checked, notifyUrl: checked ? '' : values.notifyUrl })}
                  />
                )}
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 16 }}>
                  <Select id="sys-notify-format" labelText="Formato" value={values.notifyFormat} onChange={(e) => setValues({ ...values, notifyFormat: e.target.value })}>
                    {notifyFormats.map((o) => (
                      <SelectItem key={o.value} value={o.value} text={o.label} />
                    ))}
                  </Select>
                  <NumberInput
                    id="sys-notify-drop"
                    label="Queda mínima de nota"
                    min={0}
                    max={100}
                    step={1}
                    allowEmpty
                    value={values.notifyMinScoreDrop}
                    invalid={!!errors.notifyMinScoreDrop}
                    invalidText={errors.notifyMinScoreDrop}
                    onChange={(_e, { value }) => setValues({ ...values, notifyMinScoreDrop: value === '' ? NaN : Number(value) })}
                  />
                  <Select id="sys-notify-sev" labelText="Severidade mínima dos novos" value={values.notifyMinSeverity} onChange={(e) => setValues({ ...values, notifyMinSeverity: e.target.value })}>
                    {notifySeverities.map((o) => (
                      <SelectItem key={o.value} value={o.value} text={o.label} />
                    ))}
                  </Select>
                </div>
                <TextInput
                  id="sys-notify-public"
                  labelText="Endereço do dashboard (link da mensagem)"
                  placeholder={window.location.origin}
                  value={values.notifyPublicUrl}
                  invalid={touched && !!errors.notifyPublicUrl}
                  invalidText={errors.notifyPublicUrl}
                  onChange={(e) => setValues({ ...values, notifyPublicUrl: e.target.value })}
                />
              </fieldset>

              {save.isError && (
                <InlineNotification
                  kind="error"
                  title="Não foi possível salvar"
                  subtitle={save.error instanceof Error ? save.error.message : 'Erro desconhecido'}
                  hideCloseButton
                />
              )}
            </div>

            <div style={{ display: 'flex', flexWrap: 'wrap', borderTop: '1px solid var(--s-border)', position: 'sticky', bottom: 0, background: 'var(--s-surface)', marginTop: 'auto' }}>
              <button type="button" onClick={() => setEditing(null)} style={{ ...footerButton, background: 'var(--s-surface)', color: 'var(--s-text)', borderRight: '1px solid var(--s-border)' }}>
                Cancelar
              </button>
              <button
                type="button"
                disabled={save.isPending}
                onClick={() => {
                  setTouched(true);
                  if (!invalid) save.mutate({ auditNow: false });
                }}
                style={{ ...footerButton, flex: '1 1 140px', background: '#393939', color: '#fff' }}
              >
                Salvar
              </button>
              <button
                type="button"
                disabled={save.isPending}
                onClick={() => {
                  setTouched(true);
                  if (!invalid) save.mutate({ auditNow: true });
                }}
                style={{ ...footerButton, flex: '1 1 180px', background: '#0f62fe', color: '#fff' }}
              >
                {save.isPending ? 'Salvando…' : 'Salvar e auditar agora'}
              </button>
            </div>
              </>
            )}
          </div>
          </div>
        )}
      </div>

      <StartAuditModal system={auditTarget} onClose={() => setAuditTarget(null)} />

      <Modal
        open={deleteTarget !== null}
        danger
        size="sm"
        modalHeading="Excluir sistema"
        modalLabel={deleteTarget?.name}
        primaryButtonText={remove.isPending ? 'Excluindo…' : 'Excluir sistema'}
        secondaryButtonText="Cancelar"
        primaryButtonDisabled={remove.isPending}
        onRequestClose={() => {
          if (!remove.isPending) setDeleteTarget(null);
        }}
        onRequestSubmit={() => {
          if (deleteTarget) remove.mutate(deleteTarget);
        }}
      >
        <p>
          Tem certeza de que deseja excluir <strong>{deleteTarget?.name}</strong>?
        </p>
        <p style={{ marginTop: 12 }}>
          Isso remove também todas as auditorias, achados, supressões e configurações deste sistema. Não dá para desfazer.
        </p>
        {remove.isError && (
          <InlineNotification
            style={{ marginTop: 12 }}
            kind="error"
            title="Não foi possível excluir"
            subtitle={remove.error instanceof Error ? remove.error.message : 'Erro desconhecido'}
            hideCloseButton
          />
        )}
      </Modal>
    </div>
  );
}
