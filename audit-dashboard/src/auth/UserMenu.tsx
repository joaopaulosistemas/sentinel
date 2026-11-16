import { HeaderGlobalAction } from '@carbon/react';
import { Logout } from '@carbon/icons-react';
import { useAuth } from './AuthContext';

/** Papel mais alto do usuário, em português. */
export function rotuloDoPapel(admin: boolean, canExecute: boolean): string {
  if (admin) return 'Administrador';
  return canExecute ? 'Executor' : 'Sem papel no Sentinel';
}

/** Nome, papel e botão Sair. Some quando o login corporativo não está ligado. */
export function UserMenu() {
  const auth = useAuth();
  if (!auth.enabled) return null;
  return (
    <>
      <span className="sentinel-hide-narrow" style={{ color: '#c6c6c6', fontSize: '0.75rem', padding: '0 0.5rem', whiteSpace: 'nowrap' }}>
        {auth.username} · {rotuloDoPapel(auth.admin, auth.canExecute)}
      </span>
      <HeaderGlobalAction aria-label="Sair" tooltipAlignment="end" onClick={auth.logout}>
        <Logout size={20} />
      </HeaderGlobalAction>
    </>
  );
}
