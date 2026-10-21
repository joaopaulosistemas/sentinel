import { Component, OnDestroy } from '@angular/core';

class SemLimpeza {
  timer: any;
  iniciar() {
    // ruleid: sentinel-mem-setinterval-sem-clear
    this.timer = setInterval(() => this.tick(), 1000);
  }
  ouvir() {
    // ruleid: sentinel-mem-addeventlistener-sem-remove
    window.addEventListener('resize', this.onResize);
  }
  tick() {}
  onResize = () => {};
}

class ComLimpeza {
  timer: any;
  iniciar() {
    // ok: sentinel-mem-setinterval-sem-clear
    this.timer = setInterval(() => this.tick(), 1000);
  }
  parar() {
    clearInterval(this.timer);
  }
  ouvir() {
    // ok: sentinel-mem-addeventlistener-sem-remove
    window.addEventListener('resize', this.onResize);
  }
  soltar() {
    window.removeEventListener('resize', this.onResize);
  }
  tick() {}
  onResize = () => {};
}

class Assina {
  dados$: any;
  usuario$: any;
  sub: any;
  a() {
    // ruleid: sentinel-mem-subscribe-sem-desinscrever
    this.dados$.subscribe((d: any) => d);
  }
  b() {
    // ok: sentinel-mem-subscribe-sem-desinscrever
    this.usuario$.pipe(takeUntilDestroyed()).subscribe((d: any) => d);
  }
  c() {
    // ok: sentinel-mem-subscribe-sem-desinscrever
    this.sub = this.dados$.subscribe((d: any) => d);
  }
  d() {
    // ok: sentinel-mem-subscribe-sem-desinscrever
    this.dados$.pipe(take(1)).subscribe((d: any) => d);
  }
}
declare function takeUntilDestroyed(): any;
declare function take(n: number): any;
