import {
  Button,
  DataTableSkeleton,
  Checkbox,
  ComposedModal,
  InlineNotification,
  ModalBody,
  ModalFooter,
  ModalHeader,
  NumberInput,
  Search,
  Select,
  SelectItem,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  TextArea,
  TextInput,
} from '@carbon/react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../api/client';
import { ScoreGauge } from '../../components/ScoreGauge';
import { JourneySteps } from '../../components/JourneySteps';
import { StartAuditModal } from '../../components/StartAuditModal';
import { usePacks } from '../../hooks/useLifecycle';
import { useSystems } from '../../hooks/useSystems';
import type { SystemSummaryResponse } from '../../types';
import {
  emptyForm,
  formFrom,
  scheduleChanged,
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

  const [query, setQuery] = useState('');
  const [editing, setEditing] = useState<SystemSummaryResponse | 'new' | null>(null);
  const [values, setValues] = useState<SystemFormValues>(emptyForm);
  const [touched, setTouched] = useState(false);
  const [auditTarget, setAuditTarget] = useState<SystemSummaryResponse | null>(null);

  const errors = validate(values);
  const invalid = Object.keys(errors).length > 0;

  const open = (target: SystemSummaryResponse | 'new') => {
    setEditing(target);
    setValues(target === 'new' ? emptyForm : formFrom(target));
    setTouched(false);
    save.reset();
    check.reset();
  };

  // Confere se o endereço responde e qual é a branch padrão, sem clonar.
  const check = useMutation({
    mutationFn: () => api.checkRepo(values.repoUrl.trim(), values.scheduledBranch.trim() || undefined),
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

  return (
    <div style={{ padding: '2rem', display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', gap: '1rem', flexWrap: 'wrap' }}>
        <div>
          <JourneySteps current={0} />
          <h1 style={{ fontSize: '1.75rem', fontWeight: 400, margin: '0.25rem 0' }}>Sistemas</h1>
          <p style={{ color: '#525252' }}>
            {systems?.length ?? 0} sistemas cadastrados. Cadastre um novo, edite os dados ou inicie uma auditoria.
          </p>
        </div>
        <Button onClick={() => open('new')}>Novo sistema</Button>
      </div>

      <Search
        size="lg"
        labelText="Buscar sistema"
        placeholder="Buscar por nome, equipe ou repositório"
        value={query}
        onChange={(e: { target: { value: string } }) => setQuery(e.target.value)}
      />

      {list.length === 0 ? (
        <InlineNotification
          kind="info"
          title={systems?.length ? 'Nenhum sistema encontrado' : 'Nenhum sistema cadastrado'}
          subtitle={systems?.length ? 'Tente outro termo de busca.' : 'Clique em "Novo sistema" para cadastrar o primeiro.'}
          hideCloseButton
        />
      ) : (
        <Table aria-label="Sistemas cadastrados">
          <TableHead>
            <TableRow>
              <TableHeader>Sistema</TableHeader>
              <TableHeader>Equipe</TableHeader>
              <TableHeader>Agendamento</TableHeader>
              <TableHeader>Nota</TableHeader>
              <TableHeader>Ações</TableHeader>
            </TableRow>
          </TableHead>
          <TableBody>
            {list.map((s) => (
              <TableRow key={s.id}>
                <TableCell>
                  <button
                    type="button"
                    onClick={() => navigate(`/systems/${s.id}`)}
                    style={{ background: 'none', border: 0, padding: 0, color: '#0f62fe', cursor: 'pointer', fontWeight: 500, fontSize: 'inherit' }}
                  >
                    {s.name}
                  </button>
                  <div style={{ fontSize: '0.75rem', color: '#525252', fontFamily: 'monospace' }}>{s.repoUrl ?? ''}</div>
                </TableCell>
                <TableCell>{s.team || '—'}</TableCell>
                <TableCell>
                  {s.auditIntervalHours ? `a cada ${s.auditIntervalHours} h` : 'manual'}
                  {s.auditOnPush ? ' · a cada push' : ''}
                </TableCell>
                <TableCell>
                  <ScoreGauge score={s.lastAuditScore} size="sm" />
                </TableCell>
                <TableCell>
                  <Button kind="ghost" size="sm" onClick={() => setAuditTarget(s)}>
                    Auditar
                  </Button>
                  <Button kind="ghost" size="sm" onClick={() => open(s)}>
                    Editar
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <ComposedModal open={editing !== null} size="md" onClose={() => setEditing(null)} preventCloseOnClickOutside>
        <ModalHeader title={isNew ? 'Novo sistema' : `Editar ${editing?.name ?? ''}`} label="Cadastro de sistemas" />
        <ModalBody hasForm>
          <div style={{ display: 'grid', gap: '1rem' }}>
            <TextInput
              id="sys-name"
              labelText="Nome do sistema *"
              value={values.name}
              invalid={touched && !!errors.name}
              invalidText={errors.name}
              onChange={(e) => setValues({ ...values, name: e.target.value })}
            />
            <div style={{ display: 'grid', gap: '0.5rem' }}>
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
              />
              <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap' }}>
                <Button
                  kind="tertiary"
                  size="sm"
                  disabled={!!validate(values).repoUrl || check.isPending}
                  onClick={() => check.mutate()}
                >
                  {check.isPending ? 'Verificando…' : 'Verificar repositório'}
                </Button>
                {check.isError && (
                  <span role="status" style={{ color: '#a2191f', fontSize: '0.8125rem' }}>
                    Não foi possível verificar agora: {check.error instanceof Error ? check.error.message : 'erro desconhecido'}
                  </span>
                )}
                {check.data?.reachable && (
                  <span role="status" style={{ color: '#198038', fontSize: '0.8125rem' }}>
                    ✔ Alcançável
                    {check.data.defaultBranch ? ` · branch padrão: ${check.data.defaultBranch}` : ''}
                    {check.data.branchFound === false ? ` · a branch "${values.scheduledBranch.trim()}" não existe` : ''}
                  </span>
                )}
                {check.data && !check.data.reachable && (
                  <span role="status" style={{ color: '#a2191f', fontSize: '0.8125rem' }}>
                    ✘ {check.data.message ?? 'Repositório inacessível'}
                  </span>
                )}
                {check.data?.reachable &&
                  check.data.defaultBranch &&
                  values.scheduledBranch.trim() !== check.data.defaultBranch && (
                    <Button
                      kind="ghost"
                      size="sm"
                      onClick={() => setValues({ ...values, scheduledBranch: check.data?.defaultBranch ?? '' })}
                    >
                      Auditar a branch {check.data.defaultBranch}
                    </Button>
                  )}
              </div>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
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
              <TextInput
                id="sys-team"
                labelText="Equipe"
                value={values.team}
                onChange={(e) => setValues({ ...values, team: e.target.value })}
              />
            </div>
            <Select
              id="sys-profile"
              labelText="Perfil de regras"
              helperText="Define quais pacotes de regras valem. Vale a partir da próxima auditoria."
              value={values.profile}
              onChange={(e) => setValues({ ...values, profile: e.target.value })}
            >
              <SelectItem value="default" text="default" />
              {profiles
                .filter((p) => p.id !== 'default')
                .map((p) => (
                  <SelectItem key={p.id} value={p.id} text={p.name || p.id} />
                ))}
            </Select>
            <TextArea
              id="sys-desc"
              labelText="Descrição"
              rows={2}
              value={values.description}
              onChange={(e) => setValues({ ...values, description: e.target.value })}
            />

            <fieldset style={{ border: '1px solid #e0e0e0', padding: '0.75rem 1rem', display: 'grid', gap: '0.75rem' }}>
              <legend style={{ fontSize: '0.75rem', fontWeight: 600, letterSpacing: '0.04em', textTransform: 'uppercase', color: '#525252' }}>
                Auditoria automática
              </legend>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', alignItems: 'end' }}>
                <NumberInput
                  id="sys-hours"
                  label="A cada (horas, 0 = manual)"
                  min={0}
                  max={720}
                  step={1}
                  value={values.auditIntervalHours}
                  invalid={!!errors.auditIntervalHours}
                  invalidText={errors.auditIntervalHours}
                  onChange={(_e: unknown, state: { value: string | number }) =>
                    setValues({ ...values, auditIntervalHours: Number(state.value) || 0 })
                  }
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
                labelText="Auditar a cada push (exige o webhook configurado no repositório)"
                checked={values.auditOnPush}
                onChange={(_e, { checked }) => setValues({ ...values, auditOnPush: checked })}
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
        </ModalBody>
        <ModalFooter>
          <Button kind="secondary" onClick={() => setEditing(null)}>
            Cancelar
          </Button>
          <Button
            kind="tertiary"
            disabled={save.isPending}
            onClick={() => {
              setTouched(true);
              if (!invalid) save.mutate({ auditNow: false });
            }}
          >
            Salvar
          </Button>
          <Button
            kind="primary"
            disabled={save.isPending}
            onClick={() => {
              setTouched(true);
              if (!invalid) save.mutate({ auditNow: true });
            }}
          >
            Salvar e auditar agora
          </Button>
        </ModalFooter>
      </ComposedModal>

      <StartAuditModal system={auditTarget} onClose={() => setAuditTarget(null)} />
    </div>
  );
}
