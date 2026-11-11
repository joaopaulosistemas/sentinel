export function a() {
  try {
    executa();
  } catch (e) {
    // ruleid: sentinel-obs-catch-console-sem-erro
    console.error('falha ao executar');
  }
}

export function b() {
  try {
    executa();
  } catch (e) {
    // ok: sentinel-obs-catch-console-sem-erro
    console.error('falha ao executar', e);
  }
  // ok: sentinel-obs-catch-console-sem-erro
  console.error('erro fora de catch');
}

function executa() {}
