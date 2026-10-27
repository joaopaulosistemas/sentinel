import { create } from 'zustand';

export type ThemeMode = 'light' | 'dark';

/**
 * Paletas de cor (valem para o tema claro e o escuro):
 * - classico: as cores originais, contraste máximo;
 * - suave: contraste reduzido, sem branco nem preto puros e cores dessaturadas (padrão, mais descansado);
 * - alternativo: claro em tom de papel (quente) e escuro em cinza-azulado neutro.
 */
export type Palette = 'classico' | 'suave' | 'alternativo';
export const PALETTES: Array<{ id: Palette; label: string }> = [
  { id: 'classico', label: 'Clássico' },
  { id: 'suave', label: 'Suave (recomendado)' },
  { id: 'alternativo', label: 'Alternativo' },
];

const STORAGE_KEY = 'sentinel.theme';
const PALETTE_KEY = 'sentinel.palette';

function readSavedPalette(): Palette {
  try {
    const v = window.localStorage.getItem(PALETTE_KEY);
    return v === 'classico' || v === 'alternativo' || v === 'suave' ? v : 'suave';
  } catch {
    return 'suave';
  }
}

/** Lê a escolha salva; o navegador pode bloquear o armazenamento, e aí vale o tema normal. */
function readSaved(): ThemeMode {
  try {
    return window.localStorage.getItem(STORAGE_KEY) === 'dark' ? 'dark' : 'light';
  } catch {
    return 'light';
  }
}

/** Marca o tema na página inteira: as variáveis de cor e o fundo do corpo dependem disto. */
export function applyTheme(mode: ThemeMode, palette: Palette = readSavedPalette()) {
  document.documentElement.setAttribute('data-theme', mode);
  document.documentElement.setAttribute('data-palette', palette);
  document.documentElement.style.colorScheme = mode;
}

interface ThemeStore {
  mode: ThemeMode;
  palette: Palette;
  setPalette: (palette: Palette) => void;
  setMode: (mode: ThemeMode) => void;
  toggle: () => void;
}

export const useThemeStore = create<ThemeStore>((set, get) => ({
  mode: readSaved(),
  palette: readSavedPalette(),
  setPalette: (palette) => {
    try {
      window.localStorage.setItem(PALETTE_KEY, palette);
    } catch {
      // Sem armazenamento, a escolha vale só até recarregar a página.
    }
    applyTheme(get().mode, palette);
    set({ palette });
  },
  setMode: (mode) => {
    try {
      window.localStorage.setItem(STORAGE_KEY, mode);
    } catch {
      // Sem armazenamento, a escolha vale só até recarregar a página.
    }
    applyTheme(mode, get().palette);
    set({ mode });
  },
  toggle: () => get().setMode(get().mode === 'dark' ? 'light' : 'dark'),
}));
