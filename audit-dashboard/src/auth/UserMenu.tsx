import { ColorPalette, ChevronDown, Logout } from '@carbon/icons-react';
import { useEffect, useRef, useState } from 'react';
import { PALETTES, TEXT_SIZES, useThemeStore, type Palette, type TextSize } from '../store/useThemeStore';
import { useAuth } from './AuthContext';

/** O que o login permite: administrador e/ou quem pode disparar auditorias. */
export interface IPermissoes {
  admin: boolean;
  canExecute: boolean;
}

/** Papel mais alto do usuário, em português. */
export function rotuloDoPapel({ admin, canExecute }: IPermissoes): string {
  if (admin) return 'Administrador';
  return canExecute ? 'Executor' : 'Sem papel no Sentinel';
}

type Papel = 'admin' | 'executor' | 'nenhum';

export function papelDe({ admin, canExecute }: IPermissoes): Papel {
  if (admin) return 'admin';
  return canExecute ? 'executor' : 'nenhum';
}

/** Cores da etiqueta e do avatar por papel (o cabeçalho é sempre escuro, nos dois temas). */
const CORES: Record<Papel, { texto: string; fundo: string }> = {
  admin: { texto: '#d0e2ff', fundo: '#001d6c' },
  executor: { texto: '#9ef0f0', fundo: '#022b30' },
  nenhum: { texto: '#c6c6c6', fundo: '#393939' },
};

/** Inicial do nome para o avatar; "?" se o nome vier vazio. */
export function inicial(nome: string): string {
  const letra = nome.trim().charAt(0);
  return letra ? letra.toUpperCase() : '?';
}

/** Rótulos curtos do tamanho do texto, para os botões do menu. */
const TAMANHOS: Record<TextSize, string> = { pequeno: 'Pequeno', normal: 'Normal', grande: 'Grande' };

function Avatar({ nome, papel, tamanho }: { nome: string; papel: Papel; tamanho: number }) {
  const cor = CORES[papel];
  return (
    <span
      aria-hidden="true"
      style={{
        width: tamanho, height: tamanho, borderRadius: '50%', background: cor.fundo, border: `1.5px solid ${cor.texto}`,
        color: cor.texto, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: Math.round(tamanho * 0.42),
        fontWeight: 600, flex: 'none',
      }}
    >
      {inicial(nome)}
    </span>
  );
}

function EtiquetaDoPapel({ papel, rotulo }: { papel: Papel; rotulo: string }) {
  const cor = CORES[papel];
  return (
    <span
      style={{
        fontSize: 11, fontWeight: 600, color: cor.texto, background: cor.fundo, border: `1px solid ${cor.texto}55`,
        borderRadius: 10, padding: '2px 8px', whiteSpace: 'nowrap', alignSelf: 'flex-start',
      }}
    >
      {rotulo}
    </span>
  );
}

/** A lista da caixa abre sobre o fundo do navegador: cor e fundo explícitos mantêm o texto legível nos dois temas. */
export const OPCAO_ESTILO = { background: '#262626', color: '#f4f4f4' } as const;

const ROTULO_ESTILO = { fontSize: 12, color: '#c6c6c6' } as const;

/** Fecha o menu com Esc ou clique fora dele. */
function useFecharFora(aberto: boolean, fechar: () => void) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!aberto) return;
    const aoClicar = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) fechar();
    };
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape') fechar();
    };
    document.addEventListener('mousedown', aoClicar);
    document.addEventListener('keydown', aoTeclar);
    return () => {
      document.removeEventListener('mousedown', aoClicar);
      document.removeEventListener('keydown', aoTeclar);
    };
  }, [aberto, fechar]);
  return ref;
}

const SEPARADOR = <div style={{ height: 1, background: '#393939' }} />;

interface IGatilho {
  aberto: boolean;
  comLogin: boolean;
  username: string;
  papel: Papel;
  rotulo: string;
  aoAlternar: () => void;
}

/** O botão do cabeçalho que abre o menu. */
function Gatilho({ aberto, comLogin, username, papel, rotulo, aoAlternar }: IGatilho) {
  return (
    <button
      type="button"
      aria-haspopup="true"
      aria-expanded={aberto}
      aria-label={comLogin ? `Menu de ${username}` : 'Aparência'}
      onClick={aoAlternar}
      style={{
        all: 'unset', boxSizing: 'border-box', height: 36, display: 'flex', alignItems: 'center', gap: 10,
        padding: comLogin ? '0 8px 0 4px' : '0 10px', borderRadius: 18, background: aberto ? '#393939' : '#262626',
        color: '#f4f4f4', cursor: 'pointer',
      }}
    >
      {comLogin ? (
        <>
          <Avatar nome={username} papel={papel} tamanho={28} />
          <span className="sentinel-hide-narrow" style={{ fontSize: 13, fontWeight: 500 }}>{username}</span>
          <span className="sentinel-hide-narrow" style={{ display: 'flex' }}><EtiquetaDoPapel papel={papel} rotulo={rotulo} /></span>
        </>
      ) : (
        <>
          <ColorPalette size={18} />
          <span className="sentinel-hide-narrow" style={{ fontSize: 13 }}>Aparência</span>
        </>
      )}
      <ChevronDown size={16} style={{ color: '#c6c6c6' }} />
    </button>
  );
}

/** Avatar, nome e papel de quem está logado, no alto do menu. */
function CabecalhoDoUsuario({ username, papel, rotulo }: { username: string; papel: Papel; rotulo: string }) {
  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <Avatar nome={username} papel={papel} tamanho={40} />
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <span style={{ fontSize: 14, fontWeight: 600 }}>{username}</span>
          <EtiquetaDoPapel papel={papel} rotulo={rotulo} />
        </div>
      </div>
      {SEPARADOR}
    </>
  );
}

function SeletorDeTamanho() {
  const textSize = useThemeStore((st) => st.textSize);
  const setTextSize = useThemeStore((st) => st.setTextSize);
  return (
    <div role="group" aria-label="Tamanho do texto" style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <span style={ROTULO_ESTILO}>Tamanho do texto</span>
      <div style={{ display: 'flex', gap: 2, background: '#161616', borderRadius: 6, padding: 2 }}>
        {TEXT_SIZES.map((t) => {
          const ativo = t.id === textSize;
          return (
            <button
              key={t.id}
              type="button"
              aria-pressed={ativo}
              onClick={() => setTextSize(t.id)}
              style={{
                all: 'unset', flex: 1, textAlign: 'center', padding: '6px 0', fontSize: 13, borderRadius: 4, cursor: 'pointer',
                background: ativo ? '#0f62fe' : 'transparent', color: ativo ? '#fff' : '#c6c6c6', fontWeight: ativo ? 600 : 400,
              }}
            >
              {TAMANHOS[t.id]}
            </button>
          );
        })}
      </div>
    </div>
  );
}

function SeletorDePaleta() {
  const palette = useThemeStore((st) => st.palette);
  const setPalette = useThemeStore((st) => st.setPalette);
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <span style={ROTULO_ESTILO}>Paleta de cores</span>
      <select
        aria-label="Paleta de cores"
        title="Suave e Alternativo reduzem o contraste para cansar menos a vista"
        value={palette}
        onChange={(e) => setPalette(e.target.value as Palette)}
        style={{ height: 36, background: '#161616', color: '#f4f4f4', colorScheme: 'dark', border: '1px solid #393939', borderRadius: 6, fontSize: 13, padding: '0 8px' }}
      >
        {PALETTES.map((p) => (
          <option key={p.id} value={p.id} style={OPCAO_ESTILO}>{p.label}</option>
        ))}
      </select>
    </label>
  );
}

function BotaoSair({ aoSair }: { aoSair: () => void }) {
  return (
    <>
      {SEPARADOR}
      <button
        type="button"
        onClick={aoSair}
        style={{
          all: 'unset', boxSizing: 'border-box', display: 'flex', alignItems: 'center', gap: 10, padding: '8px 6px',
          borderRadius: 6, color: '#ff8389', fontSize: 14, fontWeight: 500, cursor: 'pointer',
        }}
      >
        <Logout size={20} />
        Sair
      </button>
    </>
  );
}

/**
 * Menu do canto direito do cabeçalho: com o login ligado mostra o avatar, o nome e o papel; sem login vira só
 * "Aparência". Dentro ficam o tamanho do texto, a paleta e (com login) o Sair.
 */
export function UserMenu() {
  const auth = useAuth();
  const [aberto, setAberto] = useState(false);
  const fechar = () => setAberto(false);
  const ref = useFecharFora(aberto, fechar);
  const papel = papelDe(auth);
  const rotulo = rotuloDoPapel(auth);

  return (
    <div ref={ref} style={{ position: 'relative', display: 'flex', alignItems: 'center', paddingRight: '0.5rem' }}>
      <Gatilho
        aberto={aberto}
        comLogin={auth.enabled}
        username={auth.username}
        papel={papel}
        rotulo={rotulo}
        aoAlternar={() => setAberto((v) => !v)}
      />
      {aberto && (
        <div
          role="group"
          aria-label="Menu do usuário"
          style={{
            position: 'absolute', right: 8, top: 44, width: 300, boxSizing: 'border-box', background: '#262626',
            border: '1px solid #393939', borderRadius: 8, boxShadow: '0 8px 24px #000a', padding: 16, display: 'flex',
            flexDirection: 'column', gap: 14, zIndex: 9000, color: '#f4f4f4',
          }}
        >
          {auth.enabled && <CabecalhoDoUsuario username={auth.username} papel={papel} rotulo={rotulo} />}
          <SeletorDeTamanho />
          <SeletorDePaleta />
          {auth.enabled && <BotaoSair aoSair={() => { fechar(); auth.logout(); }} />}
        </div>
      )}
    </div>
  );
}
