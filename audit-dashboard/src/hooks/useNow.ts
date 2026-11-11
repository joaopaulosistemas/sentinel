import { useEffect, useState } from 'react';

/** Hora atual em ms, reatualizada a cada intervalo: os tempos relativos ("há 3 dias") não congelam numa aba aberta. */
export function useNow(intervalMs: number): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), intervalMs);
    return () => window.clearInterval(id);
  }, [intervalMs]);
  return now;
}
