export class AcharComponent {
  public achar(): void {
    // ruleid: sentinel-ng-dom-direto
    document.getElementById('x');
    // ok: sentinel-ng-dom-direto
    this._renderer.setProperty(this._el.nativeElement, 'id', 'x');
  }
}
