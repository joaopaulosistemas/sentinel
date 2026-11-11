import { Component, SecurityContext } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';

@Component({ selector: 'app-seguro', template: '<div [innerHTML]="html"></div>' })
export class SeguroComponent {
  html: string | null;

  constructor(private sanitizer: DomSanitizer) {
    // ok: sentinel-ng-bypass-security
    this.html = this.sanitizer.sanitize(SecurityContext.HTML, '<b>texto</b>');
  }
}
