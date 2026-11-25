export function envia(janela: Window, dados: unknown) {
  // ruleid: sentinel-postmessage-origem-curinga
  janela.postMessage(dados, '*');
  // ok: sentinel-postmessage-origem-curinga
  janela.postMessage(dados, 'https://app.exemplo.com');
}

export function escuta() {
  // ruleid: sentinel-message-listener-sem-origem
  window.addEventListener('message', (e) => {
    console.log(e.data);
  });

  // ruleid: sentinel-message-listener-sem-origem
  window.addEventListener('message', function (e) {
    console.log(e.data);
  });

  // ok: sentinel-message-listener-sem-origem
  window.addEventListener('message', (e) => {
    if (e.origin !== 'https://app.exemplo.com') {
      return;
    }
    console.log(e.data);
  });
}

// ---------- redirecionamento aberto no navegador (DOM): destino vindo da URL da página ----------
export function irParaUrlDaConsulta(props: any) {
  const destino = QueryString.parse(props.location.search).url as string;
  // ruleid: sentinel-dom-open-redirect
  window.location.href = destino;
}

export function irParaUrlDireta(props: any) {
  // ruleid: sentinel-dom-open-redirect
  window.location.href = QueryString.parse(props.location.search).url as string;
}

export function irComUrlSearchParams() {
  const params = new URLSearchParams(window.location.search);
  const proximo = params.get('next');
  // ruleid: sentinel-dom-open-redirect
  window.location.assign(proximo);
}

export function irComHash() {
  // ruleid: sentinel-dom-open-redirect
  location.replace(location.hash.slice(1));
}

export function irParaRotaInterna() {
  const params = new URLSearchParams(window.location.search);
  // ok: sentinel-dom-open-redirect
  window.location.href = '/painel?aba=' + encodeURIComponent(params.get('aba') || '');
}

export function irParaDestinoFixo(props: any) {
  console.log(props.location.search);
  // ok: sentinel-dom-open-redirect
  window.location.href = 'https://app.exemplo.com/inicio';
}

export function irComHostFixo() {
  const params = new URLSearchParams(window.location.search);
  // ok: sentinel-dom-open-redirect
  window.location.href = 'https://app.exemplo.com/login?retorno=' + params.get('retorno');
}

export function irSoSeForCaminhoDoSite(props: any) {
  const destino = QueryString.parse(props.location.search).url as string;
  if (destino.startsWith('/') && !destino.startsWith('//')) {
    // ok: sentinel-dom-open-redirect
    window.location.href = destino;
  }
}

export function irSoParaHostPermitido() {
  const params = new URLSearchParams(window.location.search);
  const destino = params.get('next') || '';
  if (PERMITIDOS.includes(destino)) {
    // ok: sentinel-dom-open-redirect
    window.location.assign(destino);
  }
}

export function irSemConferir() {
  const params = new URLSearchParams(window.location.search);
  const destino = params.get('next') || '';
  if (destino) {
    // ruleid: sentinel-dom-open-redirect
    window.location.assign(destino);
  }
}
