import {
  Content,
  Header,
  HeaderName,
  HeaderNavigation,
  HeaderMenuItem,
  SkipToContent,
} from '@carbon/react';
import React from 'react';
import { useNavigate, useLocation } from 'react-router-dom';

interface AppShellProps {
  children: React.ReactNode;
}

const ITENS = [
  { nome: 'Visão geral', rota: '/', exata: true },
  { nome: 'Sistemas', rota: '/systems' },
  { nome: 'Findings', rota: '/findings' },
  { nome: 'Ranking', rota: '/ranking' },
  { nome: 'Relatórios', rota: '/reports' },
];

/** Cabeçalho escuro com a navegação principal em linha, como no protótipo. */
export function AppShell({ children }: AppShellProps) {
  const navigate = useNavigate();
  const location = useLocation();

  return (
    <>
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
            </HeaderMenuItem>
          ))}
        </HeaderNavigation>
        <span style={{ marginLeft: 'auto', paddingRight: '1rem', color: '#c6c6c6', fontSize: '0.75rem' }}>
          v{__APP_VERSION__}
        </span>
      </Header>

      <Content>{children}</Content>
    </>
  );
}
