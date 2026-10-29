import { useEffect } from 'react';

export function Lista() {
  useEffect(() => {
    // ruleid: sentinel-boas-react-fetch-em-effect
    fetch('/api/contas').then((r) => r.json());
  }, []);

  // ok: sentinel-boas-react-fetch-em-effect
  const salvar = () => fetch('/api/contas', { method: 'POST' });
  return null;
}
