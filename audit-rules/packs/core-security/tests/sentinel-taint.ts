function a() {
  const q = location.search;
  // ruleid: sentinel-taint-html-parametro-url
  document.getElementById('x').innerHTML = q;
}
function b() {
  const q = location.search;
  // ok: sentinel-taint-html-parametro-url
  document.getElementById('x').textContent = q;
}
