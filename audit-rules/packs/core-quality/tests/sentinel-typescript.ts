import { Observable } from 'rxjs';

// ruleid: sentinel-ts-any-explicito
export function a(x: any): void {}

// ruleid: sentinel-ts-any-explicito
const b = valor as any;

// ok: sentinel-ts-any-explicito
export function c(x: unknown): void {}

// ok: sentinel-ts-any-explicito
const company = 'many';

// ruleid: sentinel-ts-supressao-de-tipos
// @ts-ignore
const d: number = 'x';

// ok: sentinel-ts-supressao-de-tipos
// @ts-expect-error o contrato da lib legada devolve string aqui
const e: number = 'x';

export function logs(): void {
  // ruleid: sentinel-ts-console-log
  console.log('depuração');

  // ok: sentinel-ts-console-log
  console.error('falha real');
}

export function catches(): void {
  // ruleid: sentinel-ts-catch-vazio
  try { run(); } catch (e) {}

  // ruleid: sentinel-ts-catch-vazio
  try { run(); } catch {}

  // ok: sentinel-ts-catch-vazio
  try { run(); } catch (e) { report(e); }

  // ok: sentinel-ts-catch-vazio
  try { run(); } catch { /* corpo vazio ou não JSON: seguimos sem detalhe */ }
}

// ruleid: sentinel-ts-var
var antigo = 1;

// ok: sentinel-ts-var
const novo = 1;

export function igualdade(a: string, b: number): void {
  // ruleid: sentinel-ts-igualdade-frouxa
  if (a == b) { run(); }

  // ruleid: sentinel-ts-igualdade-frouxa
  if (a != b) { run(); }

  // ok: sentinel-ts-igualdade-frouxa
  if (a === b) { run(); }

  // ok: sentinel-ts-igualdade-frouxa
  if (a == null) { run(); }

  // ok: sentinel-ts-igualdade-frouxa
  if (a != undefined) { run(); }
}

// ruleid: sentinel-ts-export-let
export let contador = 0;

// ok: sentinel-ts-export-let
export const LIMITE = 10;

export function fluxos(a$: Observable<number>, b$: Observable<number>): void {
  // ruleid: sentinel-ts-subscribe-aninhado
  a$.subscribe((x) => {
    b$.subscribe((y) => run(x, y));
  });

  // ok: sentinel-ts-subscribe-aninhado
  a$.subscribe((x) => run(x));
}

export function armazenamento(t: string): void {
  // ruleid: sentinel-ts-token-em-localstorage
  localStorage.setItem('access_token', t);

  // ruleid: sentinel-ts-token-em-localstorage
  sessionStorage.setItem('jwt', t);

  // ok: sentinel-ts-token-em-localstorage
  localStorage.setItem('tema', 'escuro');
}
