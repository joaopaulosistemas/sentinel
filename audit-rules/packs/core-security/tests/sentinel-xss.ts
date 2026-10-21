declare const el: HTMLElement;
declare const entrada: string;

// ruleid: sentinel-xss-inner-html
el.innerHTML = entrada;
// ruleid: sentinel-xss-inner-html
el.insertAdjacentHTML('beforeend', entrada);
// ruleid: sentinel-xss-inner-html
document.write(entrada);
// ok: sentinel-xss-inner-html
el.innerHTML = '<b>fixo</b>';
// ok: sentinel-xss-inner-html
el.textContent = entrada;
