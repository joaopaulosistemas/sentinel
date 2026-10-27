import {
  Content,
  Header,
  HeaderGlobalAction,
  HeaderGlobalBar,
  HeaderName,
  HeaderNavigation,
  HeaderMenuItem,
  SkipToContent,
  Theme,
} from '@carbon/react';
import React from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { usePendingClaude } from '../../hooks/usePendingClaude';
import { useThemeStore } from '../../store/useThemeStore';

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

const ITENS = [
  { nome: 'Visão geral', rota: '/', exata: true },
  { nome: 'Sistemas', rota: '/systems' },
  { nome: 'Informações de Auditoria', rota: '/findings' },
  { nome: 'Ranking de Sistemas', rota: '/ranking' },
  { nome: 'Relatórios', rota: '/reports' },
];

/** Cabeçalho escuro com a navegação principal em linha, como no protótipo. */
export function AppShell({ children }: AppShellProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const mode = useThemeStore((st) => st.mode);
  const toggleTheme = useThemeStore((st) => st.toggle);
  const dark = mode === 'dark';
  const pending = usePendingClaude().total;

  return (
    <>
      <Theme theme="g100">
      <Header aria-label="Sentinel Audit Tool">
        <SkipToContent />
        <HeaderName href="#" prefix="Sentinel" onClick={(e: React.MouseEvent) => { e.preventDefault(); navigate('/'); }}>
          Audit Tool
        </HeaderName>
        <HeaderNavigation aria-label="Navegação principal">
          {ITENS.map((i) => (
            <HeaderMenuItem
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
            </HeaderMenuItem>
          ))}
        </HeaderNavigation>
        <span style={{ marginLeft: 'auto', paddingRight: '1rem', color: '#c6c6c6', fontSize: '0.75rem' }}>
          v{__APP_VERSION__}
        </span>
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
      </Theme>

      {/* Os componentes do Carbon (campos, abas, modais) seguem o mesmo tema do restante da página. */}
      <Theme theme={dark ? 'g100' : 'white'}>
        <Content style={{ background: 'var(--s-bg)', color: 'var(--s-text)', minHeight: '100vh' }}>{children}</Content>
      </Theme>
    </>
  );
}
