import { useEffect } from 'react';

export function Ruim() {
  // ruleid: sentinel-mem-react-effect-sem-cleanup
  useEffect(() => {
    setInterval(() => console.log('x'), 1000);
  }, []);
  // ruleid: sentinel-mem-react-effect-sem-cleanup
  useEffect(() => {
    window.addEventListener('resize', onResize);
  }, []);
  return null;
}

export function Bom() {
  // ok: sentinel-mem-react-effect-sem-cleanup
  useEffect(() => {
    const id = setInterval(() => console.log('x'), 1000);
    return () => clearInterval(id);
  }, []);
  // ok: sentinel-mem-react-effect-sem-cleanup
  useEffect(() => {
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);
  return null;
}
function onResize() {}
