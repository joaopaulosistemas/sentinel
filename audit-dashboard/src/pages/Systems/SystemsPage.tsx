import { DataTableSkeleton, InlineNotification, Modal } from '@carbon/react';
import { PageShell } from '../../components/PageShell';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useState, type ReactNode } from 'react';
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

type Tab = 'cadastro' | 'motor' | 'analisadores' | 'mcp';

const TABS: ReadonlyArray<readonly [Tab, string]> = [
  ['cadastro', 'Cadastro e Git'],
  ['motor', 'Motor de regras'],
  ['analisadores', 'Analisadores'],
  ['mcp', 'Claude (MCP)'],
];

/** O sistema já cadastrado que está sendo editado (nulo ao criar ou com o painel fechado). */
function savedSystem(editing: SystemSummaryResponse | 'new' | null): SystemSummaryResponse | null {
  return editing !== null && editing !== 'new' ? editing : null;
}

async function saveSystem(values: SystemFormValues, current: SystemSummaryResponse | null) {
  const body = toRequest(values);
  const system = current ? await api.updateSystem(current.id, body) : await api.createSystem(body);
  return scheduleChanged(values, current) ? api.updateSchedule(system.id, toSchedule(values)) : system;
}

function filterSystems(systems: SystemSummaryResponse[], query: string): SystemSummaryResponse[] {
  const term = query.trim().toLowerCase();
  return [...systems]
    .filter((s) => term === '' || `${s.name} ${s.team} ${s.repoUrl ?? ''} ${s.localPath ?? ''}`.toLowerCase().includes(term))
    .sort((a, b) => (a.lastAuditScore ?? -1) - (b.lastAuditScore ?? -1));
}

function dirtyLabels(cadastro: boolean, engine: { motor: boolean; analisadores: boolean }): string[] {
  const labels: string[] = [];
  if (cadastro) labels.push('Cadastro e Git');
  if (engine.motor) labels.push('Motor de regras');
  if (engine.analisadores) labels.push('Analisadores');
  return labels;
}

interface SystemTabsProps {
  tab: Tab;
  onSelect: (tab: Tab) => void;
  isNew: boolean;
  dirty: Record<'cadastro' | 'motor' | 'analisadores', boolean>;
}

function SystemTabs({ tab, onSelect, isNew, dirty }: SystemTabsProps) {
  return (
    <div role="tablist" aria-label="Seções do sistema" style={{ display: 'flex', borderBottom: '1px solid var(--s-border)', padding: '0 12px' }}>
      {TABS.map(([id, nome]) => {
        const bloqueada = id !== 'cadastro' && isNew;
        const alterada = id !== 'mcp' && dirty[id];
        return (
          <button
            key={id}
            type="button"
            role="tab"
            aria-selected={tab === id}
            disabled={bloqueada}
            title={bloqueada ? 'Salve o sistema primeiro' : undefined}
            onClick={() => onSelect(id)}
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
            {alterada && (
              <span role="img" aria-label="alterações não salvas" title="Alterações não salvas" style={{ color: 'var(--s-warn-text)', marginLeft: 6 }}>●</span>
            )}
          </button>
        );
      })}
    </div>
  );
}

function ConfirmCloseBar({ tabs, onKeep, onDiscard }: { tabs: string[]; onKeep: () => void; onDiscard: () => void }) {
  return (
    <div role="alert" style={{ background: 'var(--s-warn-bg)', borderLeft: '3px solid #f1c21b', padding: '12px 24px', display: 'flex', gap: 12, alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap' }}>
      <span style={{ fontSize: 14 }}>
        Há alterações não salvas em <strong>{tabs.join(', ')}</strong>. Se fechar agora, elas serão perdidas.
      </span>
      <span style={{ display: 'flex', gap: 8 }}>
        <button type="button" onClick={onKeep} style={{ height: 32, padding: '0 12px', border: '1px solid var(--s-link)', background: 'var(--s-surface)', color: 'var(--s-link)', cursor: 'pointer' }}>
          Continuar editando
        </button>
        <button type="button" onClick={onDiscard} style={{ height: 32, padding: '0 12px', border: '1px solid #da1e28', background: 'var(--s-surface)', color: 'var(--s-danger-text)', cursor: 'pointer' }}>
          Descartar e fechar
        </button>
      </span>
    </div>
  );
}

/** Esc fecha o pop-up; com alteração sem salvar, pergunta antes (e um segundo Esc cancela a pergunta). */
function useEscapeToClose(
  open: boolean,
  dirty: boolean,
  confirming: boolean,
  setConfirming: (value: boolean) => void,
  close: () => void,
) {
  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return;
      if (confirming) setConfirming(false);
      else if (dirty) setConfirming(true);
      else close();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
    // close e setConfirming mudam a cada render, mas só leem estado já listado
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, dirty, confirming]);
}

const footerButton = { flex: '1 1 120px', height: 56, border: 0, fontSize: 14, cursor: 'pointer', textAlign: 'left', padding: '0 16px' } as const;

function FormFooter({ pending, onCancel, onSave }: { pending: boolean; onCancel: () => void; onSave: (auditNow: boolean) => void }) {
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', borderTop: '1px solid var(--s-border)', position: 'sticky', bottom: 0, background: 'var(--s-surface)', marginTop: 'auto' }}>
      <button type="button" onClick={onCancel} style={{ ...footerButton, background: 'var(--s-surface)', color: 'var(--s-text)', borderRight: '1px solid var(--s-border)' }}>
        Cancelar
      </button>
      <button type="button" disabled={pending} onClick={() => onSave(false)} style={{ ...footerButton, flex: '1 1 140px', background: '#393939', color: '#fff' }}>
        Salvar
      </button>
      <button type="button" disabled={pending} onClick={() => onSave(true)} style={{ ...footerButton, flex: '1 1 180px', background: 'var(--s-primary)', color: '#fff' }}>
        {pending ? 'Salvando…' : 'Salvar e auditar agora'}
      </button>
    </div>
  );
}

function EmptyList({ hasSystems }: { hasSystems: boolean }) {
  return (
    <div style={{ padding: 16 }}>
      <InlineNotification
        kind="info"
        title={hasSystems ? 'Nenhum sistema encontrado' : 'Nenhum sistema cadastrado'}
        subtitle={hasSystems ? 'Tente outro termo de busca.' : 'Clique em "Novo sistema" para cadastrar o primeiro.'}
        hideCloseButton
      />
    </div>
  );
}

/** Troca um dos avisos de "alteração sem salvar" sem recriar o objeto quando nada mudou. */
function withFlag<T extends Record<string, boolean>>(flags: T, key: keyof T, value: boolean): T {
  return flags[key] === value ? flags : { ...flags, [key]: value };
}

function LoadError({ error }: { error: unknown }) {
  return <InlineNotification kind="error" title="Erro ao carregar sistemas" subtitle={error instanceof Error ? error.message : 'Erro desconhecido'} />;
}

function PageHeading({ count, onNew }: { count: number; onNew: () => void }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', gap: 16, flexWrap: 'wrap' }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        <h1 style={{ margin: 0, fontSize: 26, fontWeight: 400 }}>Sistemas</h1>
        <span style={{ color: 'var(--s-text-3)' }}>
          {count} {count === 1 ? 'sistema cadastrado' : 'sistemas cadastrados'}. Cadastre, edite o perfil de regras e o agendamento, ou dispare uma auditoria.
        </span>
      </div>
      <button
        type="button"
        onClick={onNew}
        style={{ height: 48, padding: '0 20px', background: 'var(--s-primary)', color: '#fff', border: 0, fontSize: 14, cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: 24 }}
      >
        Novo sistema
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.5" strokeLinecap="round" aria-hidden="true"><path d="M12 5v14M5 12h14" /></svg>
      </button>
    </div>
  );
}

function SearchBar({ query, onQuery }: { query: string; onQuery: (q: string) => void }) {
  return (
    <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--s-border)', display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between' }}>
      <label style={{ display: 'flex', alignItems: 'center', gap: 8, background: 'var(--s-layer)', borderBottom: '1px solid var(--s-border-strong)', padding: '0 12px', height: 40, minWidth: 260 }}>
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--s-text-3)" strokeWidth="2" strokeLinecap="round" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="M20 20l-4-4" /></svg>
        <span style={{ position: 'absolute', width: 1, height: 1, overflow: 'hidden', clip: 'rect(0 0 0 0)' }}>Buscar sistema</span>
        <input
          type="search"
          value={query}
          onChange={(e) => onQuery(e.target.value)}
          placeholder="Buscar por nome, equipe ou repositório"
          style={{ border: 0, background: 'transparent', outline: 'none', flex: 1, fontSize: 14, minWidth: 0 }}
        />
      </label>
      <span style={{ fontSize: 12, color: 'var(--s-text-3)' }}>Ordenado por nota, menor primeiro</span>
    </div>
  );
}

interface EditDialogProps {
  editing: SystemSummaryResponse | 'new';
  tab: Tab;
  visited: Tab[];
  cadastroDirty: boolean;
  engineDirty: { motor: boolean; analisadores: boolean };
  dirtyTabs: string[];
  confirmClose: boolean;
  onTab: (t: Tab) => void;
  onMotorDirty: (d: boolean) => void;
  onAnalyzersDirty: (d: boolean) => void;
  onKeepOpen: () => void;
  onDiscard: () => void;
  onRequestClose: () => void;
  /** Conteúdo da aba "Cadastro e Git" (campos e rodapé). */
  cadastro: ReactNode;
}

/** Pop-up de cadastro e edição: abas, aviso de alteração sem salvar e o painel da aba ativa. */
function EditDialog(p: EditDialogProps) {
  const { editing, tab, visited } = p;
  const isNew = editing === 'new';
  return (
    <div
      role="presentation"
      style={{ position: 'fixed', inset: 0, zIndex: 9000, background: 'rgba(22,22,22,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16 }}
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) p.onRequestClose();
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
          <button type="button" aria-label="Fechar" onClick={p.onRequestClose} style={{ width: 40, height: 40, background: 'transparent', border: 0, cursor: 'pointer' }}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--s-text)" strokeWidth="2" strokeLinecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" /></svg>
          </button>
        </div>

        <SystemTabs
          tab={tab}
          onSelect={p.onTab}
          isNew={isNew}
          dirty={{ cadastro: p.cadastroDirty, motor: p.engineDirty.motor, analisadores: p.engineDirty.analisadores }}
        />

        {p.confirmClose && <ConfirmCloseBar tabs={p.dirtyTabs} onKeep={p.onKeepOpen} onDiscard={p.onDiscard} />}

        {!isNew && visited.includes('motor') && (
          <div hidden={tab !== 'motor'}>
            <EngineRulesPanel key={`motor-${editing.id}`} system={editing} onDirtyChange={p.onMotorDirty} />
          </div>
        )}
        {!isNew && visited.includes('analisadores') && (
          <div hidden={tab !== 'analisadores'}>
            <EngineAnalyzersPanel key={`an-${editing.id}`} system={editing} onDirtyChange={p.onAnalyzersDirty} />
          </div>
        )}
        {tab === 'mcp' && !isNew && <McpPanel system={editing} />}
        {tab === 'cadastro' && p.cadastro}
      </div>
    </div>
  );
}

interface DeleteMutation {
  isPending: boolean;
  isError: boolean;
  error: unknown;
  mutate: (target: SystemSummaryResponse) => void;
}

function DeleteSystemModal({ target, remove, onClose }: { target: SystemSummaryResponse | null; remove: DeleteMutation; onClose: () => void }) {
  return (
    <Modal
      open={target !== null}
      danger
      size="sm"
      modalHeading="Excluir sistema"
      modalLabel={target?.name}
      primaryButtonText={remove.isPending ? 'Excluindo…' : 'Excluir sistema'}
      secondaryButtonText="Cancelar"
      primaryButtonDisabled={remove.isPending}
      onRequestClose={() => {
        if (!remove.isPending) onClose();
      }}
      onRequestSubmit={() => {
        if (target) remove.mutate(target);
      }}
    >
      <p>
        Tem certeza de que deseja excluir <strong>{target?.name}</strong>?
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
  );
}

/** A origem "pasta local" só aparece quando o servidor a habilitou (SENTINEL_LOCAL_ROOT). */
function useLocalSourceEnabled(): boolean {
  return (
    useQuery({
      queryKey: ['local-source-enabled'],
      queryFn: () => api.getLocalSourceEnabled().then((r) => r.enabled).catch(() => false),
      staleTime: 5 * 60_000,
    }).data === true
  );
}

/** Exclui o sistema e todas as suas auditorias (a API remove tudo junto); só depois da confirmação. */
function useRemoveSystem(onDone: () => void) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (target: SystemSummaryResponse) => api.deleteSystem(target.id),
    onSuccess: (_data, target) => {
      void queryClient.invalidateQueries({ queryKey: ['systems'] });
      void queryClient.invalidateQueries({ queryKey: ['audits'] });
      queryClient.removeQueries({ queryKey: ['systems', target.id] });
      onDone();
    },
  });
}

/** Confere o endereço (responde? qual a branch padrão?) e a pasta local, sem clonar. */
function useSourceChecks(values: SystemFormValues, editing: SystemSummaryResponse | 'new' | null) {
  const check = useMutation({
    mutationFn: () =>
      api.checkRepo(values.repoUrl.trim(), values.scheduledBranch.trim() || undefined, {
        systemId: savedSystem(editing)?.id,
        gitToken: values.gitToken.trim(),
        gitTokenHosts: values.gitTokenHosts.trim(),
      }),
  });
  const checkPath = useMutation({
    mutationFn: () => api.checkPath(values.localPath.trim(), values.scheduledBranch.trim() || undefined),
  });
  return { check, checkPath };
}

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
  const onMotorDirty = useCallback((d: boolean) => setEngineDirty((p) => withFlag(p, 'motor', d)), []);
  const onAnalyzersDirty = useCallback((d: boolean) => setEngineDirty((p) => withFlag(p, 'analisadores', d)), []);

  const errors = validate(values);
  const invalid = Object.keys(errors).length > 0;

  const { check, checkPath } = useSourceChecks(values, editing);
  const localEnabled = useLocalSourceEnabled();
  const remove = useRemoveSystem(() => setDeleteTarget(null));

  const save = useMutation({
    mutationFn: async (vars: { auditNow: boolean }) => ({
      system: await saveSystem(values, savedSystem(editing)),
      auditNow: vars.auditNow,
    }),
    onSuccess: ({ system, auditNow }) => {
      void queryClient.invalidateQueries({ queryKey: ['systems'] });
      setEditing(null);
      if (auditNow) setAuditTarget(system);
    },
  });

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
    checkPath.reset();
  };

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
  const cadastroDirty = JSON.stringify(values) !== snapshot;
  const dirtyTabs = dirtyLabels(cadastroDirty, engineDirty);
  const anyDirty = modalOpen && dirtyTabs.length > 0;
  const requestClose = () => {
    if (anyDirty) setConfirmClose(true);
    else setEditing(null);
  };
  useEscapeToClose(modalOpen, anyDirty, confirmClose, setConfirmClose, () => setEditing(null));

  if (isLoading) return <DataTableSkeleton columnCount={6} rowCount={5} />;
  if (error) return <LoadError error={error} />;

  const list = filterSystems(systems ?? [], query);
  const profiles = packs?.profiles ?? [];
  const current = savedSystem(editing);

  const cadastro = (
    <>
      <SystemFormFields
        values={values}
        setValues={setValues}
        errors={errors}
        touched={touched}
        check={check}
        checkPath={checkPath}
        localEnabled={localEnabled}
        save={save}
        profiles={profiles}
        profileInfo={profiles.find((p) => p.id === values.profile)}
        tokenSaved={!!current?.gitTokenSet}
        notifySaved={!!current?.notifyUrlSet}
        webhookSaved={!!current?.webhookSecretSet}
        mono="'IBM Plex Mono', ui-monospace, monospace"
      />
      <FormFooter
        pending={save.isPending}
        onCancel={requestClose}
        onSave={(auditNow) => {
          setTouched(true);
          if (!invalid) save.mutate({ auditNow });
        }}
      />
    </>
  );

  return (
    <PageShell crumbs={[{ label: 'Início', to: '/' }, { label: 'Sistemas' }]}>
      <PageHeading count={systems?.length ?? 0} onNew={() => open('new')} />

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 20, alignItems: 'flex-start' }}>
        <section aria-label="Lista de sistemas" style={{ flex: '999 1 640px', minWidth: 0, background: 'var(--s-surface)', border: '1px solid var(--s-border)' }}>
          <SearchBar query={query} onQuery={setQuery} />
          {list.length === 0 ? (
            <EmptyList hasSystems={!!systems?.length} />
          ) : (
            <SystemsTable systems={list} selectedId={current?.id ?? null} onAudit={setAuditTarget} onEdit={open} onDelete={(s) => { remove.reset(); setDeleteTarget(s); }} />
          )}
        </section>

        {editing !== null && (
          <EditDialog
            editing={editing}
            tab={tab}
            visited={visited}
            cadastroDirty={cadastroDirty}
            engineDirty={engineDirty}
            dirtyTabs={dirtyTabs}
            confirmClose={confirmClose}
            onTab={setTab}
            onMotorDirty={onMotorDirty}
            onAnalyzersDirty={onAnalyzersDirty}
            onKeepOpen={() => setConfirmClose(false)}
            onDiscard={() => { setConfirmClose(false); setEditing(null); }}
            onRequestClose={requestClose}
            cadastro={cadastro}
          />
        )}
      </div>

      <StartAuditModal system={auditTarget} onClose={() => setAuditTarget(null)} />
      <DeleteSystemModal target={deleteTarget} remove={remove} onClose={() => setDeleteTarget(null)} />
    </PageShell>
  );
}
