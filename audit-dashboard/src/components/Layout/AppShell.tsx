import {
  Content,
  Header,
  HeaderName,
  SideNav,
  SideNavItems,
  SideNavLink,
  SideNavMenu,
  SkipToContent,
} from '@carbon/react';
import React from 'react';
import { useNavigate, useLocation } from 'react-router-dom';

interface AppShellProps {
  children: React.ReactNode;
}

export function AppShell({ children }: AppShellProps) {
  const navigate = useNavigate();
  const location = useLocation();

  return (
    <>
      <Header aria-label="Sentinel Audit Tool">
        <SkipToContent />
        <HeaderName prefix="SENTINEL" onClick={() => navigate('/')}>
          Audit Tool
        </HeaderName>
        <span style={{ marginLeft: 'auto', paddingRight: '1rem', color: '#c6c6c6', fontSize: '0.75rem' }}>
          v{__APP_VERSION__}
        </span>
      </Header>

      <SideNav
        aria-label="Navegação principal"
        isFixedNav
        expanded
        isPersistent
      >
        <SideNavItems>
          <SideNavLink
            isActive={location.pathname === '/'}
            onClick={() => navigate('/')}
            href="#"
          >
            Visão Geral
          </SideNavLink>
          <SideNavLink
            isActive={location.pathname.startsWith('/ranking')}
            onClick={() => navigate('/ranking')}
            href="#"
          >
            Ranking
          </SideNavLink>
          <SideNavLink
            isActive={location.pathname.startsWith('/findings')}
            onClick={() => navigate('/findings')}
            href="#"
          >
            Findings
          </SideNavLink>
          <SideNavLink
            isActive={location.pathname.startsWith('/reports')}
            onClick={() => navigate('/reports')}
            href="#"
          >
            Relatórios
          </SideNavLink>
          <SideNavMenu title="Sistemas" defaultExpanded={false}>
            <SideNavLink
              isActive={location.pathname.startsWith('/systems')}
              onClick={() => navigate('/systems')}
              href="#"
            >
              Todos os Sistemas
            </SideNavLink>
          </SideNavMenu>
        </SideNavItems>
      </SideNav>

      <Content>{children}</Content>
    </>
  );
}
