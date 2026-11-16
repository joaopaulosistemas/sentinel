import { createContext, useContext } from 'react';

export interface IAuth {
  /** Login corporativo ligado? Sem ele o painel funciona como sempre (API key no nginx). */
  enabled: boolean;
  username: string;
  roles: string[];
  admin: boolean;
  canExecute: boolean;
  logout: () => void;
}

/** Sem login ligado todos os recursos aparecem, como antes. */
export const SEM_LOGIN: IAuth = { enabled: false, username: '', roles: [], admin: true, canExecute: true, logout: () => undefined };

export const AuthContext = createContext<IAuth>(SEM_LOGIN);

export function useAuth(): IAuth {
  return useContext(AuthContext);
}
