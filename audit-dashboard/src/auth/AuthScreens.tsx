import { Button, InlineNotification, Loading, Theme } from '@carbon/react';
import { ArrowRight } from '@carbon/icons-react';
import type { ReactNode } from 'react';
import { SentinelLogo } from '../components/SentinelLogo';

/**
 * Moldura das telas de entrada: coluna estreita centralizada, com o logo em cima. Sempre no tema escuro do Carbon (cinza 100),
 * como a tela de login do Keycloak, seja qual for o tema escolhido no painel.
 */
function AuthScreen({ children }: { children: ReactNode }) {
  return (
    <Theme theme="g100" style={{ minHeight: '100dvh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '1.5rem', background: '#161616', color: '#f4f4f4' }}>
      <main style={{ width: '100%', maxWidth: 400 }}>
        <div style={{ marginBottom: '2rem' }}>
          <SentinelLogo size={40} />
        </div>
        {children}
      </main>
    </Theme>
  );
}

const titleStyle = { margin: '0 0 0.5rem', fontSize: '2rem', lineHeight: 1.25, fontWeight: 400 } as const;
const textStyle = { margin: '0 0 2rem', fontSize: '0.875rem', lineHeight: 1.43, color: '#c6c6c6' } as const;

/** Enquanto o painel confirma a sessão com o servidor de login. */
export function AuthLoadingScreen() {
  return (
    <AuthScreen>
      <div role="status" aria-live="polite">
        <div style={{ marginBottom: '1.5rem' }}>
          <Loading small withOverlay={false} description="Carregando" />
        </div>
        {/* Não é um h1: é um estado passageiro, e os testes e leitores de tela procuram o título da página que vem depois. */}
        <p style={{ ...titleStyle, color: '#f4f4f4' }}>Entrando…</p>
        <p style={textStyle}>Confirmando a sua sessão com o servidor de login.</p>
      </div>
    </AuthScreen>
  );
}

interface IErrorProps {
  message: string;
  onRetry: () => void;
}

/** Quando o login ou a conferência do perfil falha: diz o que houve e oferece tentar de novo. */
export function AuthErrorScreen({ message, onRetry }: IErrorProps) {
  return (
    <AuthScreen>
      <h1 style={titleStyle}>Não foi possível entrar</h1>
      <InlineNotification
        kind="error"
        lowContrast
        hideCloseButton
        title={message}
        subtitle="A sessão pode ter expirado ou o servidor ficou indisponível."
        style={{ maxWidth: 'none', margin: '1.5rem 0 2rem' }}
      />
      <Button kind="primary" size="xl" renderIcon={ArrowRight} onClick={onRetry} style={{ width: '100%', maxWidth: 'none' }}>
        Entrar de novo
      </Button>
    </AuthScreen>
  );
}
