const SIDE_NAME: Record<string, string> = { front: 'front-end', back: 'back-end' };

/** Texto do selo ao lado da nota: "front-end desligado"; vazio quando nenhum lado estava desligado na última auditoria. */
export function scopeBadgeText(disabledSides: string[] | null | undefined): string {
  if (!disabledSides || disabledSides.length === 0) return '';
  return `${disabledSides.map((side) => SIDE_NAME[side] ?? side).join(' e ')} desligado`;
}
