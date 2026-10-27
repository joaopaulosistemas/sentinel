import { DataTableSkeleton, InlineNotification, Modal } from '@carbon/react';
import { PageShell } from '../../components/PageShell';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../../api/client';
import { EngineAnalyzersPanel, EngineRulesPanel } from '../../components/EnginePanel';
import { McpPanel } from '../../components/McpPanel';
import { StartAuditModal } from '../../components/StartAuditModal';
import { usePacks } from '../../hooks/useLifecycle';
import { useSystems } from '../../hooks/useSystems';
import type { SystemSummaryResponse } from '../../types';
import {
  emptyForm,
  formFrom,
  scheduleChanged,
  toRequest,
  toSchedule,
  validate,
  type SystemFormValues,
} from './systemForm';
import { SystemsTable } from './SystemsTable';
import { SystemFormFields } from './SystemFormFields';

/** Cadastro e edição de sistemas: lista com busca e um formulário único para criar ou editar. */
export function SystemsPage() {
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
  type Tab = 'cadastro' | 'motor' | 'analisadores' | 'mcp';
  const [tab, setTabState] = useState<Tab>('cadastro');
  // As abas de ajuste do motor ficam montadas depois da primeira visita: trocar de aba não descarta o que foi digitado.
  const [visited, setVisited] = useState<Tab[]>(['cadastro']);
  const setTab = (next: Tab) => {
    setTabState(next);
    setVisited((v) => (v.includes(next) ? v : [...v, next]));
  };
  const [snapshot, setSnapshot] = useState('');
  const [engineDirty, setEngineDirty] = useState<{ motor: boolean; analisadores: boolean }>({ motor: false, analisadores: false });
  const [confirmClose, setConfirmClose] = useState(false);
  const onMotorDirty = useCallback((d: boolean) => setEngineDirty((p) => (p.motor === d ? p : { ...p, motor: d })), []);
  const onAnalyzersDirty = useCallback((d: boolean) => setEngineDirty((p) => (p.analisadores === d ? p : { ...p, analisadores: d })), []);

  const errors = validate(values);
  const invalid = Object.keys(errors).length > 0;

  const open = (target: SystemSummaryResponse | 'new') => {
    const initial = target === 'new' ? emptyForm : formFrom(target);
    setEditing(target);
    setTabState('cadastro');
    setVisited(['cadastro']);
    setValues(initial);
    setSnapshot(JSON.stringify(initial));
    setEngineDirty({ motor: false, analisadores: false });
    setConfirmClose(false);
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

  // Esc fecha o pop-up de cadastro, mas pergunta antes se houver alteração sem salvar.
  const modalOpen = editing !== null;
  const dirtyTabs = [
    JSON.stringify(values) !== snapshot ? 'Cadastro e Git' : null,
    engineDirty.motor ? 'Motor de regras' : null,
    engineDirty.analisadores ? 'Analisadores' : null,
  ].filter((t): t is string => t !== null);
  const anyDirty = modalOpen && dirtyTabs.length > 0;
  const requestClose = () => {
    if (anyDirty) setConfirmClose(true);
    else setEditing(null);
  };
  useEffect(() => {
    if (!modalOpen) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return;
      if (confirmClose) setConfirmClose(false);
      else if (anyDirty) setConfirmClose(true);
      else setEditing(null);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [modalOpen, anyDirty, confirmClose]);

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
  const footerButton = { flex: '1 1 120px', height: 56, border: 0, fontSize: 14, cursor: 'pointer', textAlign: 'left', padding: '0 16px' } as const;

  return (
    <PageShell>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', gap: 16, flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <h1 style={{ margin: 0, fontSize: 26, fontWeight: 400 }}>Sistemas</h1>
          <span style={{ color: 'var(--s-text-3)' }}>
            {count} {count === 1 ? 'sistema cadastrado' : 'sistemas cadastrados'}. Cadastre, edite o perfil de regras e o agendamento, ou dispare uma auditoria.
          </span>
        </div>
        <button
          type="button"
          onClick={() => open('new')}
          style={{ height: 48, padding: '0 20px', background: 'var(--s-primary)', color: '#fff', border: 0, fontSize: 14, cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: 24 }}
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
            <SystemsTable systems={list} selectedId={editing !== null && editing !== 'new' ? editing.id : null} onAudit={setAuditTarget} onEdit={open} onDelete={(s) => { remove.reset(); setDeleteTarget(s); }} />
          )}
        </section>

        {editing !== null && (
          <div
            style={{ position: 'fixed', inset: 0, zIndex: 9000, background: 'rgba(22,22,22,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16 }}
            onMouseDown={(e) => {
              if (e.target === e.currentTarget) requestClose();
            }}
          >
          <div
            role="dialog"
            aria-modal="true"
            aria-label="Editar sistema"
            style={{ width: '70vw', minWidth: 'min(560px, 100%)', maxWidth: '100%', maxHeight: 'calc(100vh - 32px)', overflowY: 'auto', background: 'var(--s-surface)', borderTop: '3px solid var(--s-link)', display: 'flex', flexDirection: 'column' }}
          >
            <div style={{ padding: '16px 24px', borderBottom: '1px solid var(--s-border)', display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12 }}>
              <div>
                <div style={{ fontSize: 12, color: 'var(--s-text-3)' }}>{isNew ? 'Cadastrando' : 'Editando'}</div>
                <h2 style={{ margin: '2px 0 0', fontSize: 20, fontWeight: 400 }}>{isNew ? 'Novo sistema' : editing.name}</h2>
              </div>
              <button type="button" aria-label="Fechar" onClick={requestClose} style={{ width: 40, height: 40, background: 'transparent', border: 0, cursor: 'pointer' }}>
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
                      borderBottom: tab === id ? '3px solid var(--s-link)' : '3px solid transparent',
                      padding: '10px 14px',
                      fontSize: 14,
                      fontWeight: tab === id ? 600 : 400,
                      color: bloqueada ? 'var(--s-disabled)' : 'var(--s-text)',
                      cursor: bloqueada ? 'not-allowed' : 'pointer',
                    }}
                  >
                    {nome}
                    {(id === 'cadastro' ? JSON.stringify(values) !== snapshot : id === 'motor' ? engineDirty.motor : id === 'analisadores' ? engineDirty.analisadores : false) && (
                      <span role="img" aria-label="alterações não salvas" title="Alterações não salvas" style={{ color: 'var(--s-warn-text)', marginLeft: 6 }}>●</span>
                    )}
                  </button>
                );
              })}
            </div>

            {confirmClose && (
              <div role="alert" style={{ background: 'var(--s-warn-bg)', borderLeft: '3px solid #f1c21b', padding: '12px 24px', display: 'flex', gap: 12, alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap' }}>
                <span style={{ fontSize: 14 }}>
                  Há alterações não salvas em <strong>{dirtyTabs.join(', ')}</strong>. Se fechar agora, elas serão perdidas.
                </span>
                <span style={{ display: 'flex', gap: 8 }}>
                  <button type="button" onClick={() => setConfirmClose(false)} style={{ height: 32, padding: '0 12px', border: '1px solid var(--s-link)', background: 'var(--s-surface)', color: 'var(--s-link)', cursor: 'pointer' }}>
                    Continuar editando
                  </button>
                  <button type="button" onClick={() => { setConfirmClose(false); setEditing(null); }} style={{ height: 32, padding: '0 12px', border: '1px solid #da1e28', background: 'var(--s-surface)', color: 'var(--s-danger-text)', cursor: 'pointer' }}>
                    Descartar e fechar
                  </button>
                </span>
              </div>
            )}

            {!isNew && visited.includes('motor') && (
              <div hidden={tab !== 'motor'}>
                <EngineRulesPanel key={`motor-${editing.id}`} system={editing} onDirtyChange={onMotorDirty} />
              </div>
            )}
            {!isNew && visited.includes('analisadores') && (
              <div hidden={tab !== 'analisadores'}>
                <EngineAnalyzersPanel key={`an-${editing.id}`} system={editing} onDirtyChange={onAnalyzersDirty} />
              </div>
            )}
            {tab === 'mcp' && !isNew && <McpPanel system={editing} />}

            {tab === 'cadastro' && (
              <>
            <SystemFormFields values={values} setValues={setValues} errors={errors} touched={touched} check={check} save={save} profiles={profiles} profileInfo={profileInfo} tokenSaved={tokenSaved} notifySaved={notifySaved} webhookSaved={webhookSaved} mono={mono} />

            <div style={{ display: 'flex', flexWrap: 'wrap', borderTop: '1px solid var(--s-border)', position: 'sticky', bottom: 0, background: 'var(--s-surface)', marginTop: 'auto' }}>
              <button type="button" onClick={requestClose} style={{ ...footerButton, background: 'var(--s-surface)', color: 'var(--s-text)', borderRight: '1px solid var(--s-border)' }}>
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
                style={{ ...footerButton, flex: '1 1 180px', background: 'var(--s-primary)', color: '#fff' }}
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
    </PageShell>
  );
}
