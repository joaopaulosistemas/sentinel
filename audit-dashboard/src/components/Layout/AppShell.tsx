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
import { Application, ChartBar, Dashboard, Report, Security } from '@carbon/icons-react';
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
  { nome: 'Relatórios', rota: '/reports', icone: Report },
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
    const passo = e.shiftKey ? 48 : 16;
    const max = menuMaxFor(window.innerWidth);
    if (e.key === 'ArrowRight') aplicar(ref.current === MENU_TRILHO ? MENU_MIN : Math.min(max, ref.current + passo));
    else if (e.key === 'ArrowLeft') aplicar(ref.current - passo < MENU_MIN ? 0 : ref.current - passo);
    else if (e.key === 'Home') aplicar(0);
    else if (e.key === 'End') aplicar(max);
    else if (e.key === 'Enter') aplicar(ref.current === MENU_TRILHO ? MENU_PADRAO : MENU_TRILHO);
    else return;
    e.preventDefault();
  };
  return { largura, arrastando, props: { onPointerDown: iniciar, onPointerMove: mover, onPointerUp: soltar, onPointerCancel: soltar, onKeyDown: teclado, onDoubleClick: () => aplicar(MENU_PADRAO) } };
}

/** Cabeçalho escuro no topo e a navegação principal fixa na lateral esquerda. */
export function AppShell({ children }: AppShellProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const mode = useThemeStore((st) => st.mode);
  const toggleTheme = useThemeStore((st) => st.toggle);
  const dark = mode === 'dark';
  const pending = usePendingClaude().total;
  const palette = useThemeStore((st) => st.palette);
  const setPalette = useThemeStore((st) => st.setPalette);
  const fixo = useMenuFixo();
  const menu = useMenuLargura();
  const trilho = fixo && menu.largura === MENU_TRILHO;
  const [aberto, setAberto] = useState(false);
  // Ao trocar de tela ou ao voltar para a tela larga, o menu sobreposto fecha.
  useEffect(() => setAberto(false), [location.pathname, fixo]);
  const textSize = useThemeStore((st) => st.textSize);
  const setTextSize = useThemeStore((st) => st.setTextSize);

  return (
    <>
      <Theme theme="g100">
      <Header aria-label="Sentinel Audit Tool">
        <SkipToContent />
        {!fixo && (
          <HeaderMenuButton aria-label={aberto ? 'Fechar o menu' : 'Abrir o menu'} isActive={aberto} onClick={() => setAberto((a) => !a)} isCollapsible />
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
        <select
          aria-label="Tamanho do texto"
          className="sentinel-hide-narrow"
          title="Tamanho do texto da área de conteúdo"
          value={textSize}
          onChange={(e) => setTextSize(e.target.value as TextSize)}
          style={{ marginRight: '0.5rem', height: 32, background: 'transparent', color: '#c6c6c6', border: '1px solid #525252', fontSize: 12, padding: '0 8px' }}
        >
          {TEXT_SIZES.map((t) => (
            <option key={t.id} value={t.id} style={{ color: '#161616' }}>{t.label}</option>
          ))}
        </select>
        <select
          aria-label="Paleta de cores"
          className="sentinel-hide-narrow"
          title="Paleta de cores: Suave e Alternativo reduzem o contraste para cansar menos a vista"
          value={palette}
          onChange={(e) => setPalette(e.target.value as Palette)}
          style={{ marginRight: '0.5rem', height: 32, background: 'transparent', color: '#c6c6c6', border: '1px solid #525252', fontSize: 12, padding: '0 8px' }}
        >
          {PALETTES.map((p) => (
            <option key={p.id} value={p.id} style={{ color: '#161616' }}>{p.label}</option>
          ))}
        </select>
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
      <SideNav isFixedNav expanded={fixo || aberto} className={fixo ? `sentinel-menu-redim${trilho ? ' sentinel-menu-trilho' : ''}` : undefined} style={fixo ? ({ '--s-menu-w': `${menu.largura}px` } as React.CSSProperties) : undefined} isChildOfHeader={!fixo} onOverlayClick={() => setAberto(false)} aria-label="Navegação principal">
        <SideNavItems>
          {ITENS.map((i) => (
            <SideNavLink
              key={i.rota}
              href={i.rota}
              renderIcon={i.icone}
              isActive={i.exata ? location.pathname === i.rota : location.pathname.startsWith(i.rota)}
              onClick={(e: React.MouseEvent) => {
                e.preventDefault();
                navigate(i.rota);
              }}
            >
              <span className="sentinel-menu-rotulo" title={trilho ? i.nome : undefined}>{i.nome}</span>
              {i.rota === '/findings' && pending > 0 && (
                <span
                  aria-label={`${pending} ${pending === 1 ? 'pedido do Claude espera' : 'pedidos do Claude esperam'} sua decisão`}
                  className="sentinel-menu-selo"
                  style={{ marginLeft: 8, minWidth: 18, padding: '0 6px', borderRadius: 9, background: 'var(--s-link)', color: '#fff', fontSize: 11, lineHeight: '18px', display: 'inline-block', textAlign: 'center' }}
                >
                  {pending}
                </span>
              )}
            </SideNavLink>
          ))}
        </SideNavItems>
      </SideNav>
      {fixo && (
        // Separador focável = "window splitter" do WAI-ARIA: o teclado também ajusta a largura.
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
      )}
      </Theme>

      {/* Os componentes do Carbon (campos, abas, modais) seguem o mesmo tema do restante da página. */}
      <Theme theme={dark ? 'g100' : 'white'} className="sentinel-content-theme">
        <Content className="sentinel-content" style={{ background: 'var(--s-bg)', color: 'var(--s-text)', minHeight: '100vh', marginLeft: fixo ? menu.largura : 0, padding: 0 }}>{children}</Content>
      </Theme>
    </>
  );
}
