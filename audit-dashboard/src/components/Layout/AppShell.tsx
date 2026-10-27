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
import React, { useEffect, useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { usePendingClaude } from '../../hooks/usePendingClaude';
import { PALETTES, useThemeStore, type Palette } from '../../store/useThemeStore';

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
  { nome: 'Visão geral', rota: '/', exata: true },
  { nome: 'Sistemas', rota: '/systems' },
  { nome: 'Informações de Auditoria', rota: '/findings' },
  { nome: 'Ranking de Sistemas', rota: '/ranking' },
  { nome: 'Relatórios', rota: '/reports' },
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
  const [aberto, setAberto] = useState(false);
  // Ao trocar de tela ou ao voltar para a tela larga, o menu sobreposto fecha.
  useEffect(() => setAberto(false), [location.pathname, fixo]);

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
      <SideNav isFixedNav expanded={fixo || aberto} isChildOfHeader={!fixo} onOverlayClick={() => setAberto(false)} aria-label="Navegação principal">
        <SideNavItems>
          {ITENS.map((i) => (
            <SideNavLink
              key={i.rota}
              href={i.rota}
              isActive={i.exata ? location.pathname === i.rota : location.pathname.startsWith(i.rota)}
              onClick={(e: React.MouseEvent) => {
                e.preventDefault();
                navigate(i.rota);
              }}
            >
              {i.nome}
              {i.rota === '/findings' && pending > 0 && (
                <span
                  aria-label={`${pending} ${pending === 1 ? 'pedido do Claude espera' : 'pedidos do Claude esperam'} sua decisão`}
                  style={{ marginLeft: 8, minWidth: 18, padding: '0 6px', borderRadius: 9, background: 'var(--s-link)', color: '#fff', fontSize: 11, lineHeight: '18px', display: 'inline-block', textAlign: 'center' }}
                >
                  {pending}
                </span>
              )}
            </SideNavLink>
          ))}
        </SideNavItems>
      </SideNav>
      </Theme>

      {/* Os componentes do Carbon (campos, abas, modais) seguem o mesmo tema do restante da página. */}
      <Theme theme={dark ? 'g100' : 'white'} className="sentinel-content-theme">
        <Content className="sentinel-content" style={{ background: 'var(--s-bg)', color: 'var(--s-text)', minHeight: '100vh', marginLeft: fixo ? '16rem' : 0, padding: 0 }}>{children}</Content>
      </Theme>
    </>
  );
}
