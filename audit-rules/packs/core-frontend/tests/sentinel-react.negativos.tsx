import React from 'react';

// ok: sentinel-react-componente-classe
export function Saudacao({ nome }: { nome: string }) {
  // ok: sentinel-react-dangerously
  return <p>{nome}</p>;
}
