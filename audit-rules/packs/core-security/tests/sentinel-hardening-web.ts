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
