// Casos de teste do pacote ts-clean-code-labs42. Cada "ruleid:/ok:" vale para a linha seguinte.

// ok: sentinel-cct-parametros-demais
function emitir(nome: string, valor: number, vencimento: Date) {}
// ok: sentinel-cct-parametros-demais
function emitirCinco(a: string, b: number, c: Date, d: string, e: string) {}
// ruleid: sentinel-cct-parametros-demais
function emitirSeis(a: string, b: number, c: Date, d: string, e: string, f: number) {}
// ruleid: sentinel-cct-parametros-demais
const emitirSeisSeta = (a: string, b: number, c: Date, d: string, e: string, f: number) => {};
// ok: sentinel-cct-parametros-demais
function emitirComObjeto(boleto: Boleto, banco: string) {}

// ruleid: sentinel-cct-parametro-booleano
function gerarRelatorio(mes: number, resumido: boolean) {}
// ok: sentinel-cct-parametro-booleano
function gerarRelatorioResumido(mes: number) {}

function marcar(conta: Conta) {
  // ruleid: sentinel-cct-altera-parametro
  conta.ativa = true;
}
// ok: sentinel-cct-altera-parametro
function marcarCopia(conta: Conta) {
  return { ...conta, ativa: true };
}

// ruleid: sentinel-cct-escreve-no-global
window.usuarioAtual = usuario;
// ruleid: sentinel-cct-escreve-no-global
Array.prototype.ultimo = function () { return this[this.length - 1]; };
// ok: sentinel-cct-escreve-no-global
const janela = window.innerWidth;

function saudar(nome: string, titulo?: string) {
  // ruleid: sentinel-cct-valor-padrao-com-ou
  const tratamento = titulo || 'Sr.';
  // ok: sentinel-cct-valor-padrao-com-ou
  const nomeLimpo = nome.trim();
}

function liberar(conta: Conta) {
  // ruleid: sentinel-cct-condicao-composta
  if (conta.ativa && conta.saldo > 0 && !conta.bloqueada) {
    abrir();
  }
  // ok: sentinel-cct-condicao-composta
  if (conta.ativa && conta.saldo > 0) {
    abrir();
  }
}

function tratar(valor: unknown) {
  // ruleid: sentinel-cct-verificacao-de-tipo-em-cadeia
  if (typeof valor === 'string') {
    return valor;
  } else if (typeof valor === 'number') {
    return String(valor);
  }
  // ok: sentinel-cct-verificacao-de-tipo-em-cadeia
  if (typeof valor === 'string') {
    return valor;
  }
}

function sair(): number {
  // ruleid: sentinel-cct-codigo-inalcancavel
  return 1;
  limpar();
}
function sairLimpo(x: number): number {
  switch (x) {
    case 1:
      // ok: sentinel-cct-codigo-inalcancavel
      return 1;
      break;
    default:
      return 0;
  }
}

function somar(lista: number[]) {
  // ruleid: sentinel-cct-laco-com-indice
  for (let indice = 0; indice < lista.length; indice++) {
    total += lista[indice];
  }
  // ok: sentinel-cct-laco-com-indice
  for (const item of lista) {
    total += item;
  }
}

function falhar() {
  // ruleid: sentinel-cct-throw-sem-error
  throw 'saldo insuficiente';
}
function falharCerto() {
  // ok: sentinel-cct-throw-sem-error
  throw new Error('saldo insuficiente');
}
function relancar(erro: Error) {
  // ok: sentinel-cct-throw-sem-error
  throw erro;
}

async function ler() {
  // ruleid: sentinel-cct-catch-so-registra
  try {
    await buscar();
  } catch (e) {
    console.log(e);
  }
  // ok: sentinel-cct-catch-so-registra
  try {
    await buscar();
  } catch (e) {
    avisarUsuario(e);
    throw e;
  }
}

function promessas() {
  // ruleid: sentinel-cct-promessa-rejeicao-ignorada
  buscar().catch(() => {});
  // ok: sentinel-cct-promessa-rejeicao-ignorada
  buscar().catch(erro => tratar(erro));
}

function encadeadas() {
  // ruleid: sentinel-cct-then-encadeado
  buscar().then(a => b(a)).then(c => d(c));
  // ok: sentinel-cct-then-encadeado
  buscar().then(a => b(a));
}

// ruleid: sentinel-cct-parametro-callback
function carregar(id: number, callback: (x: Conta) => void) {}
// ok: sentinel-cct-parametro-callback
function carregarPromessa(id: number): Promise<Conta> { return buscar(id); }

function nomes() {
  // ruleid: sentinel-cct-nome-sem-significado
  const d = new Date();
  // ruleid: sentinel-cct-nome-sem-significado
  let temp = 0;
  // ok: sentinel-cct-nome-sem-significado
  const dataEmissao = new Date();
  // ok: sentinel-cct-nome-sem-significado
  for (const item of itens) {}
}

function limites(tentativas: number, status: number) {
  // ruleid: sentinel-cct-numero-magico
  if (tentativas > 5) { parar(); }
  // ok: sentinel-cct-numero-magico
  if (tentativas > LIMITE_DE_TENTATIVAS) { parar(); }
  // ok: sentinel-cct-numero-magico
  if (status === 0) { parar(); }
}

// ruleid: sentinel-cct-import-relativo-profundo
import { Conta } from '../../../shared/models/conta';
// ok: sentinel-cct-import-relativo-profundo
import { Cliente } from '../../shared/models/cliente';
// ok: sentinel-cct-import-relativo-profundo
import { Banco } from '@app/shared/models/banco';

// ruleid: sentinel-cct-codigo-comentado
// const antigo = calcular(valor);
// ok: sentinel-cct-codigo-comentado
// Calcula o saldo considerando apenas lançamentos efetivados

// ruleid: sentinel-cct-comentario-de-diario
// Alterado por joao em 2019-03-10
// ok: sentinel-cct-comentario-de-diario
// Regra do Banco Central, circular 3.978

// ruleid: sentinel-cct-marcador-de-posicao
// ================ GETTERS ================
// ok: sentinel-cct-marcador-de-posicao
// Getters
