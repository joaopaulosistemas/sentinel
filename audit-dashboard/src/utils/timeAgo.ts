/** "agora", "há 12 min", "há 2 h", "ontem", "há 5 dias" — como nas listas do protótipo. */
const MS_PER_MINUTE = 60_000;
const MINUTES_PER_HOUR = 60;
const HOURS_PER_DAY = 24;
const MS_PER_DAY = 86_400_000;

export function timeAgo(iso: string | null | undefined, now: number = Date.now()): string {
  if (!iso) return '-';
  const timestamp = Date.parse(iso);
  if (Number.isNaN(timestamp)) return '-';
  const minutes = Math.max(0, Math.floor((now - timestamp) / MS_PER_MINUTE));
  if (minutes < 1) return 'agora';
  if (minutes < MINUTES_PER_HOUR) return `há ${minutes} min`;
  const hours = Math.floor(minutes / MINUTES_PER_HOUR);
  if (hours < HOURS_PER_DAY) return `há ${hours} h`;
  const days = Math.floor(hours / HOURS_PER_DAY);
  if (days === 1) return 'ontem';
  return `há ${days} dias`;
}

const two = (n: number) => String(n).padStart(2, '0');

/** Início de uma auditoria na lista: "agora", "há 12 min", "hoje 09:30", "ontem 14:00" ou "19/10 14:00". */
export function startLabel(iso: string | null | undefined, now: number = Date.now()): string {
  if (!iso) return '-';
  const startedAt = new Date(iso);
  if (Number.isNaN(startedAt.getTime())) return '-';
  const minutes = Math.floor((now - startedAt.getTime()) / MS_PER_MINUTE);
  if (minutes < 1) return 'agora';
  if (minutes < MINUTES_PER_HOUR) return `há ${minutes} min`;
  const hhmm = `${two(startedAt.getHours())}:${two(startedAt.getMinutes())}`;
  const today = new Date(now);
  const startOfToday = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime();
  if (startedAt.getTime() >= startOfToday) return `hoje ${hhmm}`;
  if (startedAt.getTime() >= startOfToday - MS_PER_DAY) return `ontem ${hhmm}`;
  return `${two(startedAt.getDate())}/${two(startedAt.getMonth() + 1)} ${hhmm}`;
}

/** "hoje 09:10", "ontem 17:42" ou "18/10 11:05"; usado na lista de relatórios gerados. */
export function whenLabel(iso: string, now: Date = new Date()): string {
  const moment = new Date(iso);
  const two = (n: number) => String(n).padStart(2, '0');
  const hm = `${two(moment.getHours())}:${two(moment.getMinutes())}`;
  const day = (x: Date) => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime();
  const diff = Math.round((day(now) - day(moment)) / MS_PER_DAY);
  if (diff === 0) return `hoje ${hm}`;
  if (diff === 1) return `ontem ${hm}`;
  return `${two(moment.getDate())}/${two(moment.getMonth() + 1)} ${hm}`;
}
