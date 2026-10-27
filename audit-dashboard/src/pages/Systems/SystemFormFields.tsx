import {
  Button,
  Checkbox,
  ContentSwitcher,
  InlineNotification,
  NumberInput,
  PasswordInput,
  RadioButton,
  RadioButtonGroup,
  Select,
  SelectItem,
  Switch,
  TextArea,
  TextInput,
} from '@carbon/react';
import type { UseMutationResult } from '@tanstack/react-query';
import { CheckIcon, CrossIcon, SpinnerIcon } from '../../components/StatusIcons';
import type { PathCheck, ProfileInfo, RepoCheck } from '../../types';
import {
  newWebhookSecret,
  notifyFormats,
  notifySeverities,
  stackOptions,
  validate,
  type SystemFormValues,
} from './systemForm';

interface Props {
  values: SystemFormValues;
  setValues: (v: SystemFormValues) => void;
  errors: Partial<Record<keyof SystemFormValues, string>>;
  touched: boolean;
  check: UseMutationResult<RepoCheck, Error, void>;
  checkPath: UseMutationResult<PathCheck, Error, void>;
  /** O servidor liberou a origem "pasta local" (SENTINEL_LOCAL_ROOT) */
  localEnabled: boolean;
  save: { isError: boolean; error: unknown };
  profiles: ProfileInfo[];
  profileInfo: ProfileInfo | undefined;
  tokenSaved: boolean;
  notifySaved: boolean;
  webhookSaved: boolean;
  mono: string;
}

const num = new Intl.NumberFormat('pt-BR');

/** Aba "Cadastro e Git" do pop-up de sistema: identificação, repositório, agendamento, perfil, notificação e webhook. */
export function SystemFormFields({ values, setValues, errors, touched, check, checkPath, localEnabled, save, profiles, profileInfo, tokenSaved, notifySaved, webhookSaved, mono }: Props) {
  const local = values.sourceType === 'LOCAL_PATH';
  // Quem já tem sistema em pasta local continua vendo a opção mesmo se o servidor a desligou (com aviso).
  const showSwitcher = localEnabled || local;
  return (
    <div style={{ padding: '20px 24px', display: 'flex', flexDirection: 'column', gap: 18 }}>
      <TextInput
        id="sys-name"
        labelText="Nome do sistema *"
        value={values.name}
        invalid={touched && !!errors.name}
        invalidText={errors.name}
        onChange={(e) => setValues({ ...values, name: e.target.value })}
      />
      {showSwitcher && (
        <ContentSwitcher
          size="md"
          selectedIndex={local ? 1 : 0}
          onChange={(e) => {
            setValues({ ...values, sourceType: (e as { name?: string | number }).name === 'LOCAL_PATH' ? 'LOCAL_PATH' : 'GIT_URL', auditOnPush: false });
            check.reset();
            checkPath.reset();
          }}
        >
          <Switch name="GIT_URL" text="Repositório Git (URL)" />
          <Switch name="LOCAL_PATH" text="Pasta local" />
        </ContentSwitcher>
      )}
      {local && !localEnabled && (
        <InlineNotification
          kind="warning"
          lowContrast
          hideCloseButton
          title="Pasta local desligada no servidor"
          subtitle="Defina SENTINEL_LOCAL_ROOT para a API poder auditar este sistema."
        />
      )}
      {!local && (
        <>
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
        </>
      )}
      {local && (
        <fieldset style={{ border: '1px solid var(--s-border)', margin: 0, padding: '14px 16px 16px', display: 'flex', flexDirection: 'column', gap: 14 }}>
          <legend style={{ padding: '0 6px', fontSize: 13, fontWeight: 600 }}>Pasta local do projeto</legend>
          <div style={{ display: 'flex', gap: 12, alignItems: 'flex-end', flexWrap: 'wrap' }}>
            <div style={{ flex: '1 1 320px', minWidth: 0 }}>
              <TextInput
                id="sys-local-path"
                labelText="Pasta (dentro da raiz de projetos) *"
                placeholder="meu-sistema"
                helperText="Caminho relativo à raiz de projetos do servidor; deve ser a raiz de um repositório Git. O Sentinel só lê: nunca altera nem apaga esta pasta."
                value={values.localPath}
                invalid={touched && !!errors.localPath}
                invalidText={errors.localPath}
                onChange={(e) => {
                  setValues({ ...values, localPath: e.target.value });
                  checkPath.reset();
                }}
                onBlur={() => {
                  if (!validate(values).localPath && !checkPath.data && !checkPath.isPending) checkPath.mutate();
                }}
              />
            </div>
            <Button kind="tertiary" size="md" disabled={!!validate(values).localPath || checkPath.isPending} onClick={() => checkPath.mutate()}>
              {checkPath.isPending ? 'Verificando…' : 'Verificar pasta'}
            </Button>
          </div>
          {checkPath.isPending && (
            <span role="status" style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, color: 'var(--s-text-3)' }}>
              <SpinnerIcon size={14} />
              Conferindo a pasta…
            </span>
          )}
          {checkPath.isError && (
            <span role="status" style={{ color: 'var(--s-danger-text)', fontSize: 12 }}>
              Não foi possível verificar agora: {checkPath.error instanceof Error ? checkPath.error.message : 'erro desconhecido'}
            </span>
          )}
          {checkPath.data?.ok && (
            <span role="status" style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, color: 'var(--s-success-text)', flexWrap: 'wrap' }}>
              <CheckIcon size={14} />
              Pasta encontrada · repositório Git
              {checkPath.data.defaultBranch && (
                <>
                  {' · branch atual: '}
                  <span style={{ fontFamily: mono }}>{checkPath.data.defaultBranch}</span>
                </>
              )}
              {checkPath.data.fileCount != null && ` · ${num.format(checkPath.data.fileCount)} arquivos`}
              {!!checkPath.data.uncommittedFiles && ` · ${num.format(checkPath.data.uncommittedFiles)} com alterações não commitadas`}
              {checkPath.data.branchFound === false ? ` · a branch "${values.scheduledBranch.trim()}" não existe` : ''}
            </span>
          )}
          {checkPath.data && !checkPath.data.ok && (
            <span role="status" style={{ display: 'inline-flex', alignItems: 'center', gap: 6, color: 'var(--s-danger-text)', fontSize: 12 }}>
              <CrossIcon size={14} />
              {checkPath.data.message ?? 'Pasta inacessível'}
            </span>
          )}
          <RadioButtonGroup
            legendText="O que auditar"
            name="sys-local-snapshot"
            orientation="vertical"
            valueSelected={values.localSnapshot}
            onChange={(v) => setValues({ ...values, localSnapshot: v === 'TREE' ? 'TREE' : 'COMMIT' })}
          >
            <RadioButton id="snap-commit" value="COMMIT" labelText="Último commit da branch (recomendado): repetível, com SHA e comparação entre auditorias" />
            <RadioButton id="snap-tree" value="TREE" labelText="Pasta como está agora: inclui o que ainda não foi commitado (sem SHA; a auditoria fica sem o aviso de código desatualizado)" />
          </RadioButtonGroup>
          <InlineNotification
            kind="info"
            lowContrast
            hideCloseButton
            title="Como funciona"
            subtitle="A auditoria analisa uma cópia temporária da pasta, descartada no fim. Webhook de push não se aplica a pasta local; o agendamento por horas continua valendo."
          />
        </fieldset>
      )}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(180px, 100%), 1fr))', gap: 16 }}>
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
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(150px, 100%), 1fr))', gap: 16 }}>
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
        {!local && (
          <>
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
          </>
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
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(150px, 100%), 1fr))', gap: 16 }}>
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

  );
}
