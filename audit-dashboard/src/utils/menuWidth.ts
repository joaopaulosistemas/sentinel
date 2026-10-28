/** Largura do menu lateral: limites, ajuste e leitura/gravação da escolha da pessoa. */
export const MENU_PADRAO = 256;
export const MENU_MIN = 200;
export const MENU_TRILHO = 48;
export const MENU_SNAP = 140;
export const MENU_MAX = 480;
export const MENU_CHAVE = 'sentinel.menu-largura';

/** Maior largura permitida: 480 px e, no máximo, 40% da janela (nunca abaixo do mínimo). */
export function menuMaxFor(viewport: number): number {
  return Math.max(MENU_MIN, Math.min(MENU_MAX, Math.floor(viewport * 0.4)));
}

/** Ajusta a largura arrastada: abaixo do ponto de encaixe vira trilho; entre o encaixe e o mínimo, mínimo. */
export function clampMenu(width: number, viewport: number): number {
  if (!Number.isFinite(width)) return MENU_PADRAO;
  if (width < MENU_SNAP) return MENU_TRILHO;
  return Math.min(menuMaxFor(viewport), Math.max(MENU_MIN, Math.round(width)));
}

export function readMenuWidth(viewport: number): number {
  try {
    const raw = window.localStorage.getItem(MENU_CHAVE);
    if (raw === null) return MENU_PADRAO;
    return clampMenu(Number(raw), viewport);
  } catch {
    return MENU_PADRAO;
  }
}

export function saveMenuWidth(width: number): void {
  try {
    window.localStorage.setItem(MENU_CHAVE, String(width));
  } catch {
    /* sem armazenamento: a largura vale só nesta sessão */
  }
}
