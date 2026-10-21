class A {
  m() {
    // ruleid: sentinel-ng-detectchanges-manual
    this.cdr.detectChanges();
  }
  n() {
    // ok: sentinel-ng-detectchanges-manual
    this.cdr.markForCheck();
  }
}

// ruleid: sentinel-ng-componente-sem-onpush
@Component({ selector: 'a', template: '' })
class SemOnPush {}

// ok: sentinel-ng-componente-sem-onpush
@Component({ selector: 'b', template: '', changeDetection: ChangeDetectionStrategy.OnPush })
class ComOnPush {}
