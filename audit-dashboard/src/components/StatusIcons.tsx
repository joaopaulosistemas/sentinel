interface IconProps {
  size?: number;
  color?: string;
  /** Texto lido por leitores de tela; sem ele o ícone é decorativo. */
  label?: string;
}

function attrs(label?: string) {
  return label ? { role: 'img' as const, 'aria-label': label } : { 'aria-hidden': true as const };
}

/** Arco que gira: algo está em andamento. */
export function SpinnerIcon({ size = 16, color = '#0f62fe', label }: IconProps) {
  return (
    <svg className="sentinel-spin" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
      strokeWidth="3" strokeLinecap="round" {...attrs(label)}>
      <path d="M12 3a9 9 0 1 0 9 9" />
    </svg>
  );
}

export function CheckIcon({ size = 16, color = '#198038', label, strokeWidth = 3 }: IconProps & { strokeWidth?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth={strokeWidth}
      strokeLinecap="round" strokeLinejoin="round" {...attrs(label)}>
      <path d="M5 12.5l4.5 4.5L19 7.5" />
    </svg>
  );
}

/** Relógio: na fila, esperando a vez. */
export function ClockIcon({ size = 16, color = '#6f6f6f', label }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2.5"
      strokeLinecap="round" {...attrs(label)}>
      <circle cx="12" cy="12" r="8" />
      <path d="M12 8v4l2.5 2" />
    </svg>
  );
}

/** X: falhou. */
export function CrossIcon({ size = 16, color = '#da1e28', label }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="3"
      strokeLinecap="round" {...attrs(label)}>
      <path d="M6 6l12 12M18 6L6 18" />
    </svg>
  );
}

/** Traço: não se aplica / ferramenta ausente. */
export function DashIcon({ size = 16, color = '#6f6f6f', label }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="3"
      strokeLinecap="round" {...attrs(label)}>
      <path d="M6 12h12" />
    </svg>
  );
}
