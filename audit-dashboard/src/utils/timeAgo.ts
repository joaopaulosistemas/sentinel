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

/** "hoje 09:10", "ontem 17:42" ou "18/10 11:05"; usado na lista de relatórios gerados. */
export function whenLabel(iso: string, now: Date = new Date()): string {
  const d = new Date(iso);
  const two = (n: number) => String(n).padStart(2, '0');
  const hm = `${two(d.getHours())}:${two(d.getMinutes())}`;
  const day = (x: Date) => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime();
  const diff = Math.round((day(now) - day(d)) / 86_400_000);
  if (diff === 0) return `hoje ${hm}`;
  if (diff === 1) return `ontem ${hm}`;
  return `${two(d.getDate())}/${two(d.getMonth() + 1)} ${hm}`;
}
