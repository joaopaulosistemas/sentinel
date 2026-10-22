function redefinir(token: string, usuario: { id: number; email: string }) {
  // ruleid: sentinel-log-segredo-js
  console.log('Token:', token);
  // ruleid: sentinel-log-segredo-js
  console.log('Segredo:', process.env.RESET_PASSWORD_SECRET);
  // ruleid: sentinel-log-segredo-js
  console.log(`Enviando e-mail para ${usuario.email} com token ${token}`);
  // ruleid: sentinel-log-segredo-js
  this.logger.debug(senhaNova);
  // ruleid: sentinel-log-segredo-js
  logger.info('login', credenciais.password);
}

function seguro(token: string, tokens: string[], usuario: { id: number }) {
  // ok: sentinel-log-segredo-js
  console.log('Token recebido');
  // ok: sentinel-log-segredo-js
  console.log('Usuário', usuario.id);
  // ok: sentinel-log-segredo-js
  console.log(`Quantidade de tokens: ${tokens.length}`);
  // ok: sentinel-log-segredo-js
  this.logger.log(`Redefinição solicitada para o usuário ${usuario.id}`);
  // ok: sentinel-log-segredo-js
  console.log(tokenizer.nome);
  // ok: sentinel-log-segredo-js
  meuObjeto.log(token);
}
