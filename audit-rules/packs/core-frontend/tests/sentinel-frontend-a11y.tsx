export function A() {
  return (
    <div>
      // ruleid: sentinel-fe-img-sem-alt
      <img src="a.png" />
      // ok: sentinel-fe-img-sem-alt
      <img src="b.png" alt="Logo" />
      // ok: sentinel-fe-img-sem-alt
      <img src="c.png" alt="" />
      // ruleid: sentinel-fe-click-sem-role
      <div onClick={() => go()}>Abrir</div>
      // ok: sentinel-fe-click-sem-role
      <div role="button" tabIndex={0} onClick={() => go()}>Abrir</div>
      // ok: sentinel-fe-click-sem-role
      <button onClick={() => go()}>Abrir</button>
      // ruleid: sentinel-fe-target-blank
      <a href="https://x.com" target="_blank">x</a>
      // ok: sentinel-fe-target-blank
      <a href="https://x.com" target="_blank" rel="noopener noreferrer">x</a>
      // ok: sentinel-fe-target-blank
      <a href="/home">interno</a>
    </div>
  );
}
