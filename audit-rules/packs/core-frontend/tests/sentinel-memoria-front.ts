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
class AssinaEncadeado {
  roteador: any;
  destroy$: any;
  e() {
    // ruleid: sentinel-mem-subscribe-sem-desinscrever
    this.roteador.events.pipe(filtrar()).subscribe(() => 1);
  }
  f() {
    // ok: sentinel-mem-subscribe-sem-desinscrever
    this.roteador.events.pipe(filtrar(), takeUntil(this.destroy$)).subscribe(() => 1);
  }
  g() {
    // ok: sentinel-mem-subscribe-sem-desinscrever
    this.roteador.events.pipe(takeUntilDestroyed()).subscribe(() => 1);
  }
}
declare function filtrar(): any;
declare function takeUntil(o: any): any;
declare function takeUntilDestroyed(): any;
declare function take(n: number): any;

// Código fora de classe (hook de React, função utilitária) não é Angular: o ciclo de vida é outro e a regra
// de efeito do React (sentinel-mem-react-effect-sem-cleanup) cobre o caso. Antes, estes geravam falso positivo.
function usaHook(useEffect: (f: () => () => void) => void) {
  useEffect(() => {
    // ok: sentinel-mem-addeventlistener-sem-remove
    document.addEventListener('click', clicou);
    // ok: sentinel-mem-setinterval-sem-clear
    const id = setInterval(() => clicou(), 1000);
    return () => {
      document.removeEventListener('click', clicou);
      clearInterval(id);
    };
  });
}
function leArquivo(leitor: FileReader) {
  // ok: sentinel-mem-addeventlistener-sem-remove
  leitor.addEventListener('load', () => clicou());
}
function clicou() {}
