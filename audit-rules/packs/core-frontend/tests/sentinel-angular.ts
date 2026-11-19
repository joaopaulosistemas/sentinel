export class Exemplo {
  constructor(private _san: DomSanitizer, private _http: HttpClient) {}

  public mostrar(valor: string): SafeHtml {
    // ruleid: sentinel-ng-bypass-security
    return this._san.bypassSecurityTrustHtml(valor);
  }

  public texto(el: ElementRef, v: string): void {
    // ruleid: sentinel-ng-nativeelement-html
    el.nativeElement.innerHTML = v;
    // ok: sentinel-ng-nativeelement-html
    el.nativeElement.textContent = v;
  }

  public buscar(): void {
    // ruleid: sentinel-ng-http-any
    this._http.get<any>('/api/contas').subscribe();
    // ok: sentinel-ng-http-any
    this._http.get<ContaInterface>('/api/contas').subscribe();
    // ok: sentinel-ng-http-any
    this._rest.get<any>('/api/contas');
  }
}

export function angularJsConfiaNoHtml ($sce: any, resposta: string) {
  // ruleid: sentinel-angularjs-trust-as
  return $sce.trustAsHtml(resposta)
}

export function angularJsConfiaNaUrl ($sce: any, destino: string) {
  // ruleid: sentinel-angularjs-trust-as
  return $sce.trustAsResourceUrl(destino)
}

export function angularJsSanitiza ($sanitize: any, resposta: string) {
  // ok: sentinel-angularjs-trust-as
  return $sanitize(resposta)
}

export function angularJsTextoPuro ($sce: any, resposta: string) {
  // ok: sentinel-angularjs-trust-as
  return $sce.getTrustedHtml(resposta)
}

export function angularJsTrechoComentado ($sce: any, vm: any) {
  // vm.zz = $sce.trustAsHtml("<b>teste</b>");
  // ok: sentinel-angularjs-trust-as
  return vm.zz
}
