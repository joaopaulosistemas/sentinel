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

const two = (n: number) => String(n).padStart(2, '0');

/** Início de uma auditoria na lista: "agora", "há 12 min", "hoje 09:30", "ontem 14:00" ou "19/10 14:00". */
export function startLabel(iso: string | null | undefined, now: number = Date.now()): string {
  if (!iso) return '-';
  const t = new Date(iso);
  if (Number.isNaN(t.getTime())) return '-';
  const min = Math.floor((now - t.getTime()) / 60000);
  if (min < 1) return 'agora';
  if (min < 60) return `há ${min} min`;
  const hhmm = `${two(t.getHours())}:${two(t.getMinutes())}`;
  const today = new Date(now);
  const startOfToday = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime();
  if (t.getTime() >= startOfToday) return `hoje ${hhmm}`;
  if (t.getTime() >= startOfToday - 86_400_000) return `ontem ${hhmm}`;
  return `${two(t.getDate())}/${two(t.getMonth() + 1)} ${hhmm}`;
}
