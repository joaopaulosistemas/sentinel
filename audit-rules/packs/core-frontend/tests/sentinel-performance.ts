export async function a(ids: string[]) {
  // ruleid: sentinel-perf-await-em-laco
  for (const id of ids) {
    await buscar(id);
  }
}

export async function b(ids: string[]) {
  // ok: sentinel-perf-await-em-laco
  await Promise.all(ids.map((id) => buscar(id)));
}

export async function c(stream: AsyncIterable<string>) {
  // ok: sentinel-perf-await-em-laco
  for await (const x of stream) {
    console.info(x);
  }
}
