async function carregar(r: Response, texto: string) {
  // ruleid: sentinel-boas-ts-json-sem-validacao
  const a = JSON.parse(texto) as Conta;
  // ruleid: sentinel-boas-ts-json-sem-validacao
  const b = (await r.json()) as Conta;
  // ok: sentinel-boas-ts-json-sem-validacao
  const c = ContaSchema.parse(JSON.parse(texto));
  return [a, b, c];
}

// ruleid: sentinel-boas-ng-componente-nao-standalone
@Component({ selector: 'app-x', standalone: false, template: '' })
export class XComponent {}

// ok: sentinel-boas-ng-componente-nao-standalone
@Component({ selector: 'app-y', standalone: true, template: '' })
export class YComponent {}
