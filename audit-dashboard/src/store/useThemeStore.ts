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

/** Tamanho do texto: "pequeno" mantém as fontes originais das telas; "normal" sobe o piso (11→12, 12→13 px); "grande" amplia o conteúdo em ~12%. */
export type TextSize = 'pequeno' | 'normal' | 'grande';
export const TEXT_SIZES: Array<{ id: TextSize; label: string }> = [
  { id: 'pequeno', label: 'Texto pequeno' },
  { id: 'normal', label: 'Texto normal' },
  { id: 'grande', label: 'Texto grande' },
];
const TEXT_KEY = 'sentinel.textsize';

function readSavedTextSize(): TextSize {
  try {
    const v = window.localStorage.getItem(TEXT_KEY);
    return v === 'grande' || v === 'pequeno' ? v : 'normal';
  } catch {
    return 'normal';
  }
}

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
  document.documentElement.setAttribute('data-texto', readSavedTextSize());
}

interface ThemeStore {
  mode: ThemeMode;
  palette: Palette;
  textSize: TextSize;
  setTextSize: (size: TextSize) => void;
  setPalette: (palette: Palette) => void;
  setMode: (mode: ThemeMode) => void;
  toggle: () => void;
}

export const useThemeStore = create<ThemeStore>((set, get) => ({
  mode: readSaved(),
  palette: readSavedPalette(),
  textSize: readSavedTextSize(),
  setTextSize: (textSize) => {
    try {
      window.localStorage.setItem(TEXT_KEY, textSize);
    } catch {
      // Sem armazenamento, a escolha vale só até recarregar a página.
    }
    document.documentElement.setAttribute('data-texto', textSize);
    set({ textSize });
  },
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
