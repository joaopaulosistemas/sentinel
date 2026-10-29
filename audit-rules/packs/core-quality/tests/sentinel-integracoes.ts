import axios from 'axios';

export async function chamadas(url: string): Promise<void> {
  // ruleid: sentinel-int-fetch-sem-timeout
  await fetch(url);

  // ruleid: sentinel-int-fetch-sem-timeout
  await fetch(url, { method: 'POST' });

  // ok: sentinel-int-fetch-sem-timeout
  await fetch(url, { signal: AbortSignal.timeout(5000) });

  // ok: sentinel-int-fetch-sem-timeout
  await fetch(url, { method: 'POST', signal: controller.signal });

  // ruleid: sentinel-int-axios-sem-timeout
  const a = axios.create({ baseURL: url });

  // ok: sentinel-int-axios-sem-timeout
  const b = axios.create({ baseURL: url, timeout: 5000 });
}

// ruleid: sentinel-int-http-sem-tls
export const URL_PARCEIRO = 'http://api.parceiro.com.br/v1';

// ok: sentinel-int-http-sem-tls
export const URL_SEGURA = 'https://api.parceiro.com.br/v1';

// ok: sentinel-int-http-sem-tls
export const URL_LOCAL = 'http://localhost:3000';

// ok: sentinel-int-http-sem-tls
export const NAMESPACE_PMD = 'http://pmd.sourceforge.net/ruleset/2.0.0';
