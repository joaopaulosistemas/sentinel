// App React escrito em .js (create-react-app): JSX também fica em arquivos .js
export function Avaliacoes ({ reviews }) {
  return reviews.map(r => (
    // ruleid: sentinel-react-dangerously
    <p key={r.id} dangerouslySetInnerHTML={{ __html: r.review }} />
  ))
}

export function AvaliacoesSeguras ({ reviews, DOMPurify }) {
  return reviews.map(r => (
    // ok: sentinel-react-dangerously
    <p key={r.id} dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(r.review) }} />
  ))
}
