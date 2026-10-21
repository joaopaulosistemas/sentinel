import { useEffect } from 'react';

export function Lista({ itens }: { itens: string[] }) {
  // ruleid: sentinel-ts-useeffect-async
  useEffect(async () => {
    await carregar();
  }, []);

  // ok: sentinel-ts-useeffect-async
  useEffect(() => {
    void carregar();
  }, []);

  return (
    <ul>
      {itens.map((item, index) => (
        // ruleid: sentinel-ts-react-key-indice
        <li key={index}>{item}</li>
      ))}
      {itens.map((item) => (
        // ok: sentinel-ts-react-key-indice
        <li key={item}>{item}</li>
      ))}
    </ul>
  );
}
