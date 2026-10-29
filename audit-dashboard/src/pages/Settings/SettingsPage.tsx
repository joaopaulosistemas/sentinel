import { ActionableNotification, Button, InlineNotification, Modal, Search, Select, SelectItem, Tag, TextInput, Toggle } from '@carbon/react';
import { useEffect, useMemo, useState } from 'react';
import { PageShell } from '../../components/PageShell';
import { RestartOverlay } from '../../components/RestartOverlay';
import { useResetSetting, useRestartApi, useSaveSettings, useSettings } from '../../hooks/useSettings';
import type { SettingView } from '../../types';
import {
  SETTINGS_SECTIONS,
  currentValue,
  eslintWarning,
  filterSettings,
  groupSettings,
  hasInvalidChange,
  numberProblem,
  pendingChanges,
  sectionCount,
  sectionLabel,
} from '../../utils/settingsView';

const mono = "'IBM Plex Mono', ui-monospace, monospace";
/** Enquanto a API reinicia, pergunta de tempos em tempos se ela voltou. */
const POLL_MS = 2000;

interface Notice {
  kind: 'success' | 'error' | 'info';
  title: string;
  text?: string;
}

function SourceTag({ s }: { s: SettingView }) {
  const type = s.source === 'tela' ? 'blue' : 'gray';
  return <Tag type={type} size="sm">{`Origem: ${s.source}`}</Tag>;
}

interface RowProps {
  s: SettingView;
  value: string;
  onChange: (key: string, value: string) => void;
  onReset: (key: string) => void;
  showSection: boolean;
}

function Control({ s, value, onChange }: Pick<RowProps, 's' | 'value' | 'onChange'>) {
  const id = `setting-${s.key}`;
  if (!s.editable) {
    return (
      <span style={{ fontFamily: mono, fontSize: 13, wordBreak: 'break-all' }} data-testid={`valor-${s.key}`}>
        {value === '' ? 'vazio' : value}
      </span>
    );
  }
  if (s.kind === 'bool') {
    return (
      <Toggle
        id={id}
        size="sm"
        labelText={s.label}
        hideLabel
        labelA="Desligado"
        labelB="Ligado"
        toggled={value === 'true'}
        onToggle={(checked: boolean) => onChange(s.key, checked ? 'true' : 'false')}
      />
    );
  }
  if (s.kind === 'enum') {
    return (
      <Select id={id} labelText={s.label} hideLabel value={value} onChange={(e) => onChange(s.key, e.target.value)}>
        {s.options.map((o) => (
          <SelectItem key={o.value} value={o.value} text={o.label} />
        ))}
      </Select>
    );
  }
  const problem = numberProblem(s, value);
  return (
    <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}>
      <TextInput
        id={id}
        labelText={s.label}
        hideLabel
        size="md"
        type={s.kind === 'number' ? 'number' : 'text'}
        value={value}
        invalid={problem !== null}
        invalidText={problem ?? ''}
        onChange={(e) => onChange(s.key, e.target.value)}
      />
      {s.unit && <span style={{ paddingTop: 12, fontSize: 13, color: 'var(--s-text-3)' }}>{s.unit}</span>}
    </div>
  );
}

function SettingRow({ s, value, onChange, onReset, showSection }: RowProps) {
  const warn = s.key === 'ESLINT_CONFIG' ? eslintWarning(value) : null;
  const fromScreen = s.editable && (s.savedValue !== null || s.source === 'tela');
  return (
    // Margem negativa + padding igual: o fundo do foco ganha respiro nas laterais sem mudar o alinhamento do texto.
    <div className="sentinel-linha-hover" style={{ borderTop: '1px solid var(--s-border)', padding: '16px 12px', margin: '0 -12px', display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) minmax(220px, 360px)', gap: 24 }}>
      <div style={{ minWidth: 0 }}>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
          <span style={{ fontWeight: 600 }}>{s.label}</span>
          {s.editable ? <SourceTag s={s} /> : <Tag type="gray" size="sm">Só no ambiente</Tag>}
          {s.pendingRestart && <Tag type="warm-gray" size="sm">Aguarda reinício</Tag>}
          {showSection && <Tag type="outline" size="sm">{sectionLabel(s.section)}</Tag>}
        </div>
        <div style={{ fontFamily: mono, fontSize: 12, color: 'var(--s-text-3)', marginTop: 2 }}>{s.key}</div>
        <div style={{ fontSize: 13, color: 'var(--s-text-2)', marginTop: 4 }}>{s.help}</div>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        <Control s={s} value={value} onChange={onChange} />
        {warn && <div role="note" style={{ fontSize: 12.5, color: 'var(--s-text-2)', borderLeft: '3px solid #f1c21b', paddingLeft: 8 }}>{warn}</div>}
        {fromScreen && (
          <Button kind="ghost" size="sm" onClick={() => onReset(s.key)} style={{ alignSelf: 'flex-start' }}>
            {`Voltar ao ambiente (${s.key})`}
          </Button>
        )}
      </div>
    </div>
  );
}

type SettingsQuery = ReturnType<typeof useSettings>;
type SetNotice = (n: Notice | null) => void;

/** Reinício da API: pedido, espera pela volta (consultando a cada POLL_MS) e o aviso de tela cheia. */
function useRestartFlow(setNotice: SetNotice) {
  const [restarting, setRestarting] = useState(false);
  // O pedido de reinício está a caminho. Estado próprio (e não restart.isPending) para o aviso não piscar entre o pedido e a espera.
  const [requesting, setRequesting] = useState(false);
  // Quem fechou o aviso depois do tempo limite continua esperando, só que sem a tela travada.
  const [overlayDismissed, setOverlayDismissed] = useState(false);
  // Muda a cada "Continuar esperando" para o aviso recomeçar o cronômetro.
  const [waitRound, setWaitRound] = useState(0);
  const [confirm, setConfirm] = useState(false);
  const query = useSettings(restarting ? POLL_MS : false);
  const restart = useRestartApi();
  const restartRequired = query.data?.restartRequired ?? false;

  // A API voltou depois do reinício: a resposta nova não pede mais reinício.
  useEffect(() => {
    if (restarting && query.isSuccess && !query.isFetching && !restartRequired) {
      setRestarting(false);
      setNotice({ kind: 'success', title: 'API reiniciada', text: 'As configurações salvas já estão em uso.' });
    }
  }, [restarting, query.isSuccess, query.isFetching, restartRequired, setNotice]);

  const onRestart = () => {
    setConfirm(false);
    setOverlayDismissed(false);
    setWaitRound(0);
    setRequesting(true);
    restart.mutate(undefined, {
      onSuccess: () => {
        setNotice({ kind: 'info', title: 'Reiniciando a API', text: 'A tela volta a responder em instantes.' });
        setRequesting(false);
        setRestarting(true);
      },
      onError: (e: Error) => {
        setRequesting(false);
        setNotice({ kind: 'error', title: 'Não foi possível reiniciar', text: e.message });
      },
    });
  };

  return {
    query,
    restarting,
    restartRequired,
    confirm,
    setConfirm,
    onRestart,
    showOverlay: (requesting || restarting) && !overlayDismissed,
    waitRound,
    keepWaiting: () => setWaitRound((r) => r + 1),
    dismissOverlay: () => setOverlayDismissed(true),
  };
}

/** Rascunhos da tela: o que foi digitado, salvar, descartar e voltar ao valor do ambiente. */
function useSettingsEditor(settings: SettingView[], setNotice: SetNotice) {
  const save = useSaveSettings();
  const reset = useResetSetting();
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const changes = pendingChanges(settings, drafts);

  const onChange = (key: string, value: string) => {
    setNotice(null);
    setDrafts((d) => ({ ...d, [key]: value }));
  };
  const onSave = () => {
    save.mutate(changes, {
      onSuccess: () => {
        setDrafts({});
        setNotice({ kind: 'success', title: 'Salvo', text: 'Os valores só entram em uso quando a API reiniciar.' });
      },
      onError: (e: Error) => setNotice({ kind: 'error', title: 'Não foi possível salvar', text: e.message }),
    });
  };
  const onReset = (key: string) => {
    setDrafts((d) => {
      const { [key]: _removido, ...resto } = d;
      return resto;
    });
    reset.mutate(key, {
      onSuccess: () => setNotice({ kind: 'info', title: 'Voltou para o ambiente', text: `${key} usará o valor do ambiente após reiniciar a API.` }),
      onError: (e: Error) => setNotice({ kind: 'error', title: 'Não foi possível restaurar', text: e.message }),
    });
  };

  return {
    drafts,
    changedCount: Object.keys(changes).length,
    invalid: hasInvalidChange(settings, changes),
    saving: save.isPending,
    onChange,
    onSave,
    onReset,
    discard: () => setDrafts({}),
  };
}

interface BannersProps {
  query: SettingsQuery;
  restarting: boolean;
  restartRequired: boolean;
  notice: Notice | null;
  onAskRestart: () => void;
  onCloseNotice: () => void;
}

function SettingsBanners({ query, restarting, restartRequired, notice, onAskRestart, onCloseNotice }: BannersProps) {
  return (
    <>
      {query.isError && !restarting && (
        <InlineNotification kind="error" lowContrast hideCloseButton title="Não foi possível carregar as configurações" subtitle={(query.error as Error).message} />
      )}
      {restarting && <InlineNotification kind="info" lowContrast hideCloseButton title="Reiniciando a API" subtitle="Aguardando a API voltar…" />}
      {restartRequired && !restarting && (
        <ActionableNotification
          kind="warning"
          lowContrast
          hideCloseButton
          inline
          title="Há configurações salvas aguardando reinício"
          subtitle="A API só lê as configurações ao subir."
          actionButtonLabel="Reiniciar a API"
          onActionButtonClick={onAskRestart}
        />
      )}
      {notice && !restarting && (
        <InlineNotification kind={notice.kind} lowContrast title={notice.title} subtitle={notice.text} onCloseButtonClick={onCloseNotice} />
      )}
    </>
  );
}

function SectionNav({ settings, section, searching, onPick }: { settings: SettingView[]; section: string; searching: boolean; onPick: (id: string) => void }) {
  return (
    <nav aria-label="Seções de configuração" style={{ flex: '0 0 230px', display: 'flex', flexDirection: 'column', gap: 2 }}>
      {SETTINGS_SECTIONS.map((sec) => {
        const active = !searching && section === sec.id;
        return (
          <button
            key={sec.id}
            type="button"
            aria-current={active ? 'true' : undefined}
            onClick={() => onPick(sec.id)}
            style={{
              all: 'unset', boxSizing: 'border-box', cursor: 'pointer', minHeight: 40, padding: '8px 12px', display: 'flex', justifyContent: 'space-between', gap: 8,
              borderLeft: `3px solid ${active ? '#0f62fe' : 'transparent'}`, background: active ? 'var(--s-selected)' : 'transparent', fontWeight: active ? 600 : 400,
            }}
          >
            <span>{sec.label}</span>
            <span style={{ color: 'var(--s-text-3)', fontSize: 12 }}>{sectionCount(settings, sec.id)}</span>
          </button>
        );
      })}
    </nav>
  );
}

function SectionHeader({ searching, section, found }: { searching: boolean; section: string; found: number }) {
  const title = searching ? 'Resultados da busca' : sectionLabel(section);
  const hint = searching ? `${found} configuração(ões) encontrada(s)` : SETTINGS_SECTIONS.find((x) => x.id === section)?.hint;
  return (
    <>
      <h2 style={{ margin: '0 0 4px', fontSize: 20, fontWeight: 400 }}>{title}</h2>
      <p style={{ margin: '0 0 8px', color: 'var(--s-text-3)', fontSize: 13 }}>{hint}</p>
    </>
  );
}

const groupTitleStyle = { fontSize: 12, fontWeight: 600, letterSpacing: '0.04em', textTransform: 'uppercase', color: 'var(--s-text-3)', margin: '8px 0 0' } as const;

interface SectionBodyProps {
  loading: boolean;
  list: SettingView[];
  searching: boolean;
  drafts: Record<string, string>;
  onChange: RowProps['onChange'];
  onReset: RowProps['onReset'];
}

function SectionBody({ loading, list, searching, drafts, onChange, onReset }: SectionBodyProps) {
  if (loading) return <p>Carregando…</p>;
  if (list.length === 0) return <p style={{ padding: '16px 0' }}>Nenhuma configuração encontrada.</p>;
  return (
    <>
      {groupSettings(list).map((g) => (
        <div key={g.group} style={{ marginBottom: 16 }}>
          {!searching && <div style={groupTitleStyle}>{g.group}</div>}
          {g.items.map((s) => (
            <SettingRow key={s.key} s={s} value={currentValue(s, drafts)} onChange={onChange} onReset={onReset} showSection={searching} />
          ))}
        </div>
      ))}
    </>
  );
}

function SaveBar({ changedCount, disableSave, onDiscard, onSave }: { changedCount: number; disableSave: boolean; onDiscard: () => void; onSave: () => void }) {
  return (
    <div style={{ position: 'sticky', bottom: 0, background: 'var(--s-surface)', borderTop: '1px solid var(--s-border)', padding: '12px 0', marginTop: 16, display: 'flex', gap: 12, alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap' }}>
      <span role="status" style={{ fontSize: 14 }}>
        {changedCount === 0 ? 'Nenhuma alteração pendente.' : `${changedCount} alteração(ões) não salva(s).`}
      </span>
      <span style={{ display: 'flex', gap: 8 }}>
        <Button kind="secondary" disabled={changedCount === 0} onClick={onDiscard}>Descartar</Button>
        <Button kind="primary" disabled={changedCount === 0 || disableSave} onClick={onSave}>Salvar alterações</Button>
      </span>
    </div>
  );
}

function RestartConfirm({ open, onClose, onConfirm }: { open: boolean; onClose: () => void; onConfirm: () => void }) {
  return (
    <Modal
      open={open}
      danger
      modalHeading="Reiniciar a API?"
      modalLabel="Configurações"
      primaryButtonText="Reiniciar"
      secondaryButtonText="Cancelar"
      onRequestClose={onClose}
      onRequestSubmit={onConfirm}
      size="sm"
    >
      <p>A API fica fora do ar por alguns segundos. Auditorias em andamento são interrompidas.</p>
    </Modal>
  );
}

/** Tela de Configurações: as variáveis de ambiente da instalação, explicadas e editáveis (quando é seguro). */
export function SettingsPage() {
  const [notice, setNotice] = useState<Notice | null>(null);
  const flow = useRestartFlow(setNotice);
  const { query } = flow;
  const [section, setSection] = useState(SETTINGS_SECTIONS[0].id);
  const [search, setSearch] = useState('');

  const settings = useMemo(() => query.data?.settings ?? [], [query.data]);
  const editor = useSettingsEditor(settings, setNotice);

  const list = filterSettings(settings, section, search);
  const searching = search.trim() !== '';

  return (
    <PageShell crumbs={[{ label: 'Início', to: '/' }, { label: 'Configurações' }]}>
      <h1 style={{ margin: '8px 0 4px', fontSize: 28, fontWeight: 400 }}>Configurações</h1>
      <p style={{ margin: '0 0 24px', color: 'var(--s-text-2)', maxWidth: 760 }}>
        Tudo que antes ficava em <code>.env</code> e em <code>application.properties</code>, com a explicação ao lado.
        O que muda o acesso ao servidor (banco, chaves, caminhos e execução de programas) aparece só para leitura.
      </p>

      <SettingsBanners
        query={query}
        restarting={flow.restarting}
        restartRequired={flow.restartRequired}
        notice={notice}
        onAskRestart={() => flow.setConfirm(true)}
        onCloseNotice={() => setNotice(null)}
      />

      <div style={{ margin: '16px 0' }}>
        <Search id="settings-search" labelText="Buscar configuração" placeholder="Buscar por nome, variável ou descrição (ex.: trivy, timeout)" value={search} onChange={(e) => setSearch(e.target.value)} size="lg" />
      </div>

      <div style={{ display: 'flex', gap: 24, alignItems: 'flex-start', flexWrap: 'wrap' }}>
        <SectionNav settings={settings} section={section} searching={searching} onPick={(id) => { setSearch(''); setSection(id); }} />
        <section aria-label={searching ? 'Resultados da busca' : sectionLabel(section)} style={{ flex: '1 1 560px', minWidth: 0 }}>
          <SectionHeader searching={searching} section={section} found={list.length} />
          <SectionBody loading={query.isLoading} list={list} searching={searching} drafts={editor.drafts} onChange={editor.onChange} onReset={editor.onReset} />
        </section>
      </div>

      <SaveBar changedCount={editor.changedCount} disableSave={editor.invalid || editor.saving} onDiscard={editor.discard} onSave={editor.onSave} />
      <RestartConfirm open={flow.confirm} onClose={() => flow.setConfirm(false)} onConfirm={flow.onRestart} />

      {flow.showOverlay && (
        <RestartOverlay
          key={flow.waitRound}
          phase={flow.restarting ? 'waiting' : 'requesting'}
          onKeepWaiting={flow.keepWaiting}
          onClose={flow.dismissOverlay}
        />
      )}
    </PageShell>
  );
}
