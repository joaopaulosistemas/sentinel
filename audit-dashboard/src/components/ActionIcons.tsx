/** Ícones das ações da lista (traço fino, herdam a cor do botão). Decorativos: o texto do botão já diz a ação. */
function Svg({ children }: { children: React.ReactNode }) {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false" style={{ flex: '0 0 auto' }}>
      {children}
    </svg>
  );
}

/** Lista: ver as auditorias. */
export const ListIcon = () => (
  <Svg>
    <path d="M8 6h12M8 12h12M8 18h12" />
    <circle cx="4" cy="6" r="1" fill="currentColor" />
    <circle cx="4" cy="12" r="1" fill="currentColor" />
    <circle cx="4" cy="18" r="1" fill="currentColor" />
  </Svg>
);

/** Play: iniciar uma auditoria. */
export const PlayIcon = () => (
  <Svg>
    <path d="M7 4.5v15l12-7.5z" />
  </Svg>
);

/** Lápis: editar. */
export const EditIcon = () => (
  <Svg>
    <path d="M4 20h4L19 9a2.8 2.8 0 00-4-4L4 16z" />
    <path d="M13.5 6.5l4 4" />
  </Svg>
);

/** Lixeira: excluir. */
export const TrashIcon = () => (
  <Svg>
    <path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v6M14 11v6" />
  </Svg>
);
