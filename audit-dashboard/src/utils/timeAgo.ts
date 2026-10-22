/** "agora", "há 12 min", "há 2 h", "ontem", "há 5 dias" — como nas listas do protótipo. */
export function timeAgo(iso: string | null | undefined, now: number = Date.now()): string {
  if (!iso) return '-';
  const t = Date.parse(iso);
  if (Number.isNaN(t)) return '-';
  const min = Math.max(0, Math.floor((now - t) / 60000));
  if (min < 1) return 'agora';
  if (min < 60) return `há ${min} min`;
  const h = Math.floor(min / 60);
  if (h < 24) return `há ${h} h`;
  const d = Math.floor(h / 24);
  if (d === 1) return 'ontem';
  return `há ${d} dias`;
}
