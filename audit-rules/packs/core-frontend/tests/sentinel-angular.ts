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

  public achar(): void {
    // ruleid: sentinel-ng-dom-direto
    document.getElementById('x');
    // ok: sentinel-ng-dom-direto
    this._renderer.setProperty(this._el.nativeElement, 'id', 'x');
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
