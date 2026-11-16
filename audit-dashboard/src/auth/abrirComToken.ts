import { authHeader, temToken } from './session';

/**
 * Abre um endereço da API numa nova aba. Com login, o navegador não envia o token ao navegar (daria 401), então
 * baixamos com o cabeçalho Authorization e abrimos o arquivo recebido. Sem login, abre o endereço direto.
 */
export async function abrirComToken(url: string): Promise<void> {
  if (!temToken()) {
    window.open(url, '_blank', 'noreferrer');
    return;
  }
  // Abre a aba já no clique (antes do download) para o navegador não bloquear como pop-up.
  const aba = window.open('', '_blank');
  try {
    const res = await fetch(url, { headers: authHeader() });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const blob = await res.blob();
    const endereco = URL.createObjectURL(blob);
    if (aba) {
      aba.location.href = endereco;
    } else {
      window.open(endereco, '_blank');
    }
  } catch (e) {
    const motivo = e instanceof Error ? e.message : 'erro desconhecido';
    if (aba) aba.document.body.textContent = `Não foi possível abrir o relatório (${motivo}). Feche esta aba e tente de novo.`;
  }
}

/** Para links <a>: com login intercepta o clique e baixa com o token; sem login deixa o navegador seguir o href. */
export function aoClicarLink(url: string) {
  return (e: { preventDefault: () => void }) => {
    if (!temToken()) return;
    e.preventDefault();
    void abrirComToken(url);
  };
}
