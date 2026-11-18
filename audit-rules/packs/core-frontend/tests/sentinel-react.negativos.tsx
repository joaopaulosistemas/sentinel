import React from 'react';

// ok: sentinel-react-componente-classe
export function Saudacao({ nome }: { nome: string }) {
  // ok: sentinel-react-dangerously
  return <p>{nome}</p>;
}

export function LinksSeguros({ url }: { url: string }) {
  // ok: sentinel-react-target-blank-sem-noopener
  const a = <a href={url} target="_blank" rel="noopener noreferrer">externo</a>;
  // ok: sentinel-react-target-blank-sem-noopener
  const b = <a href="https://exemplo.com/fixo" target="_blank">fixo</a>;
  // ok: sentinel-react-target-blank-sem-noopener
  const c = <a href={url}>mesma aba</a>;
  return [a, b, c];
}
