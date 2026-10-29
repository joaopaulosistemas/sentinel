import {
  Content,
  Header,
  HeaderGlobalAction,
  HeaderGlobalBar,
  HeaderMenuButton,
  HeaderName,
  SideNav,
  SideNavItems,
  SideNavLink,
  SkipToContent,
  Theme,
} from '@carbon/react';
import { Application, Book, ChartBar, Dashboard, Report, Security, Settings } from '@carbon/icons-react';
import React, { useEffect, useRef, useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { usePendingClaude } from '../../hooks/usePendingClaude';
import { MENU_MIN, MENU_PADRAO, MENU_TRILHO, clampMenu, menuMaxFor, readMenuWidth, saveMenuWidth } from '../../utils/menuWidth';
import { PALETTES, TEXT_SIZES, useThemeStore, type Palette, type TextSize } from '../../store/useThemeStore';

interface AppShellProps {
  children: React.ReactNode;
}

/** Sol (mostrado no tema escuro, leva ao claro) e lua (mostrada no tema claro, leva ao escuro). */
function ThemeIcon({ dark }: { dark: boolean }) {
  return dark ? (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true">
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
    </svg>
  ) : (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M21 12.8A9 9 0 1111.2 3a7 7 0 009.8 9.8z" />
    </svg>
  );
}

/** Logo: escudo com um olho (o "vigia" do Sentinel). */
export function SentinelLogo({ size = 24 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true" focusable="false">
      <path d="M16 2.5 4.5 6.8v8.2c0 7.2 4.9 12.4 11.5 14.5 6.6-2.1 11.5-7.3 11.5-14.5V6.8z" fill="#4589ff" />
      <ellipse cx="16" cy="15" rx="6.2" ry="4" fill="none" stroke="#fff" strokeWidth="2" />
      <circle cx="16" cy="15" r="1.9" fill="#fff" />
    </svg>
  );
}

const ITENS = [
  { nome: 'Visão geral', rota: '/', exata: true, icone: Dashboard },
  { nome: 'Sistemas', rota: '/systems', icone: Application },
  { nome: 'Informações de Auditoria', rota: '/findings', icone: Security },
  { nome: 'Ranking de Sistemas', rota: '/ranking', icone: ChartBar },
  { nome: 'Guia de código', rota: '/guide', icone: Book },
  { nome: 'Relatórios', rota: '/reports', icone: Report },
  { nome: 'Configurações', rota: '/settings', icone: Settings },
];

/** Largura a partir da qual o menu lateral fica fixo (66rem = 1056 px, o "lg" do Carbon). */
const MENU_FIXO = '(min-width: 66rem)';

function useMenuFixo(): boolean {
  const [fixo, setFixo] = useState(() => (typeof window.matchMedia === 'function' ? window.matchMedia(MENU_FIXO).matches : true));
  useEffect(() => {
    if (typeof window.matchMedia !== 'function') return;
    const mq = window.matchMedia(MENU_FIXO);
    const onChange = () => setFixo(mq.matches);
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }, []);
  return fixo;
}


/** Para onde a tecla leva a largura do menu; `null` = a tecla não é do separador. */
export function larguraPorTecla(key: string, shift: boolean, atual: number, max: number): number | null {
  const passo = shift ? 48 : 16;
  switch (key) {
    case 'ArrowRight':
      return atual === MENU_TRILHO ? MENU_MIN : Math.min(max, atual + passo);
    case 'ArrowLeft':
      return atual - passo < MENU_MIN ? 0 : atual - passo;
    case 'Home':
      return 0;
    case 'End':
      return max;
    case 'Enter':
      return atual === MENU_TRILHO ? MENU_PADRAO : MENU_TRILHO;
    default:
      return null;
  }
}

/** Largura do menu (arrastável e salva no navegador) e o gesto de arrastar. */
function useMenuLargura() {
  const [largura, setLargura] = useState(() => readMenuWidth(window.innerWidth));
  const [arrastando, setArrastando] = useState(false);
  const ref = useRef(largura);
  ref.current = largura;
  const aplicar = (w: number) => {
    const v = clampMenu(w, window.innerWidth);
    setLargura(v);
    saveMenuWidth(v);
  };
  const iniciar = (e: React.PointerEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.currentTarget.setPointerCapture?.(e.pointerId);
    setArrastando(true);
  };
  const mover = (e: React.PointerEvent<HTMLDivElement>) => {
    if (arrastando) setLargura(clampMenu(e.clientX, window.innerWidth));
  };
  const soltar = () => {
    if (!arrastando) return;
    setArrastando(false);
    saveMenuWidth(ref.current);
  };
  const teclado = (e: React.KeyboardEvent<HTMLDivElement>) => {
    const alvo = larguraPorTecla(e.key, e.shiftKey, ref.current, menuMaxFor(window.innerWidth));
    if (alvo === null) return;
    aplicar(alvo);
    e.preventDefault();
  };
  return { largura, arrastando, props: { onPointerDown: iniciar, onPointerMove: mover, onPointerUp: soltar, onPointerCancel: soltar, onKeyDown: teclado, onDoubleClick: () => aplicar(MENU_PADRAO) } };
}

const headerSelectStyle = { marginRight: '0.5rem', height: 32, background: 'transparent', color: '#c6c6c6', border: '1px solid #525252', fontSize: 12, padding: '0 8px' } as const;

function HeaderSelect<T extends string>({ label, title, value, options, onChange }: {
  label: string;
  title: string;
  value: T;
  options: ReadonlyArray<{ id: T; label: string }>;
  onChange: (v: T) => void;
}) {
  return (
    <select aria-label={label} className="sentinel-hide-narrow" title={title} value={value} onChange={(e) => onChange(e.target.value as T)} style={headerSelectStyle}>
      {options.map((o) => (
        <option key={o.id} value={o.id} style={{ color: '#161616' }}>{o.label}</option>
      ))}
    </select>
  );
}

function ShellHeader({ fixo, aberto, onToggleMenu }: { fixo: boolean; aberto: boolean; onToggleMenu: () => void }) {
  const navigate = useNavigate();
  const dark = useThemeStore((st) => st.mode) === 'dark';
  const toggleTheme = useThemeStore((st) => st.toggle);
  const palette = useThemeStore((st) => st.palette);
  const setPalette = useThemeStore((st) => st.setPalette);
  const textSize = useThemeStore((st) => st.textSize);
  const setTextSize = useThemeStore((st) => st.setTextSize);
  return (
    <Header aria-label="Sentinel Audit Tool">
      <SkipToContent />
      {!fixo && (
        <HeaderMenuButton aria-label={aberto ? 'Fechar o menu' : 'Abrir o menu'} isActive={aberto} onClick={onToggleMenu} isCollapsible />
      )}
      <span aria-hidden="true" style={{ display: 'flex', alignItems: 'center', paddingLeft: '1rem' }}>
        <SentinelLogo />
      </span>
      <HeaderName href="#" prefix="Sentinel" style={{ paddingLeft: '0.5rem' }} onClick={(e: React.MouseEvent) => { e.preventDefault(); navigate('/'); }}>
        Audit Tool
      </HeaderName>
      <span className="sentinel-hide-narrow" style={{ marginLeft: 'auto', paddingRight: '1rem', color: '#c6c6c6', fontSize: '0.75rem' }}>
        v{__APP_VERSION__}
      </span>
      <HeaderSelect<TextSize> label="Tamanho do texto" title="Tamanho do texto da área de conteúdo" value={textSize} options={TEXT_SIZES} onChange={setTextSize} />
      <HeaderSelect<Palette>
        label="Paleta de cores"
        title="Paleta de cores: Suave e Alternativo reduzem o contraste para cansar menos a vista"
        value={palette}
        options={PALETTES}
        onChange={setPalette}
      />
      <HeaderGlobalBar style={{ flex: '0 0 auto' }}>
        <HeaderGlobalAction
          aria-label={dark ? 'Trocar para o tema normal' : 'Trocar para o tema escuro'}
          tooltipAlignment="end"
          onClick={toggleTheme}
        >
          <ThemeIcon dark={dark} />
        </HeaderGlobalAction>
      </HeaderGlobalBar>
    </Header>
  );
}

type MenuItem = (typeof ITENS)[number];

function pendingLabel(pending: number): string {
  return `${pending} ${pending === 1 ? 'pedido do Claude espera' : 'pedidos do Claude esperam'} sua decisão`;
}

function MenuLink({ item, active, trilho, pending }: { item: MenuItem; active: boolean; trilho: boolean; pending: number }) {
  const navigate = useNavigate();
  return (
    <SideNavLink
      href={item.rota}
      renderIcon={item.icone}
      isActive={active}
      onClick={(e: React.MouseEvent) => {
        e.preventDefault();
        navigate(item.rota);
      }}
    >
      <span className="sentinel-menu-rotulo" title={trilho ? item.nome : undefined}>{item.nome}</span>
      {item.rota === '/findings' && pending > 0 && (
        <span aria-label={pendingLabel(pending)} className="sentinel-menu-selo">
          {pending}
        </span>
      )}
    </SideNavLink>
  );
}

function isActiveRoute(item: MenuItem, pathname: string): boolean {
  return item.exata ? pathname === item.rota : pathname.startsWith(item.rota);
}

interface ShellMenuProps {
  fixo: boolean;
  aberto: boolean;
  largura: number;
  trilho: boolean;
  onClose: () => void;
}

function ShellMenu({ fixo, aberto, largura, trilho, onClose }: ShellMenuProps) {
  const location = useLocation();
  const pending = usePendingClaude().total;
  const className = fixo ? `sentinel-menu-redim${trilho ? ' sentinel-menu-trilho' : ''}` : undefined;
  const style = fixo ? ({ '--s-menu-w': `${largura}px` } as React.CSSProperties) : undefined;
  return (
    <SideNav isFixedNav expanded={fixo || aberto} className={className} style={style} isChildOfHeader={!fixo} onOverlayClick={onClose} aria-label="Navegação principal">
      <SideNavItems>
        {ITENS.map((i) => (
          <MenuLink key={i.rota} item={i} active={isActiveRoute(i, location.pathname)} trilho={trilho} pending={pending} />
        ))}
      </SideNavItems>
    </SideNav>
  );
}

/** Separador focável = "window splitter" do WAI-ARIA: o teclado também ajusta a largura. */
function MenuSeparator({ menu }: { menu: ReturnType<typeof useMenuLargura> }) {
  return (
    <div
      role="separator"
      aria-orientation="vertical"
      aria-label="Largura do menu"
      aria-valuenow={menu.largura}
      aria-valuemin={MENU_TRILHO}
      aria-valuemax={menuMaxFor(window.innerWidth)}
      // eslint-disable-next-line jsx-a11y/no-noninteractive-tabindex
      tabIndex={0}
      className={`sentinel-menu-divisor${menu.arrastando ? ' sentinel-menu-divisor--ativo' : ''}`}
      style={{ left: menu.largura - 3 }}
      title="Arraste para ajustar a largura do menu; duplo clique restaura"
      {...menu.props}
    />
  );
}

/** Cabeçalho escuro no topo e a navegação principal fixa na lateral esquerda. */
export function AppShell({ children }: AppShellProps) {
  const location = useLocation();
  const dark = useThemeStore((st) => st.mode) === 'dark';
  const fixo = useMenuFixo();
  const menu = useMenuLargura();
  const trilho = fixo && menu.largura === MENU_TRILHO;
  const [aberto, setAberto] = useState(false);
  // Ao trocar de tela ou ao voltar para a tela larga, o menu sobreposto fecha.
  useEffect(() => setAberto(false), [location.pathname, fixo]);

  return (
    <>
      <Theme theme="g100">
        <ShellHeader fixo={fixo} aberto={aberto} onToggleMenu={() => setAberto((a) => !a)} />
        <ShellMenu fixo={fixo} aberto={aberto} largura={menu.largura} trilho={trilho} onClose={() => setAberto(false)} />
        {fixo && <MenuSeparator menu={menu} />}
      </Theme>

      {/* Os componentes do Carbon (campos, abas, modais) seguem o mesmo tema do restante da página. */}
      <Theme theme={dark ? 'g100' : 'white'} className="sentinel-content-theme">
        <Content className="sentinel-content" style={{ background: 'var(--s-bg)', color: 'var(--s-text)', minHeight: '100vh', marginLeft: fixo ? menu.largura : 0, padding: 0 }}>{children}</Content>
      </Theme>
    </>
  );
}
