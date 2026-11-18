import $ from 'jquery';

function mostra(user, texto) {
  const botao = $('#' + user.id + 'showContact');
  // ruleid: sentinel-xss-jquery-html
  botao.replaceWith(user.emailAddress);
  // ruleid: sentinel-xss-jquery-html
  $('#painel').html(texto);
  // ruleid: sentinel-xss-jquery-html
  $('#lista').append('<li>' + texto + '</li>');
  // ruleid: sentinel-xss-jquery-html
  $('#lista').prepend(`<li>${texto}</li>`);
  // ok: sentinel-xss-jquery-html
  $('#painel').html('<b>fixo</b>');
  // ok: sentinel-xss-jquery-html
  $('#painel').text(texto);
  // ok: sentinel-xss-jquery-html
  botao.replaceWith(document.createTextNode(texto));
  // ok: sentinel-xss-jquery-html
  $('#lista').append('<li>fixo</li>');
}
