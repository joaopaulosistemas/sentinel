// ruleid: sentinel-react-componente-classe
class Antigo extends React.Component {
  render() {
    return <div />;
  }
}

export function Novo({ html, valor }: { html: string; valor: string }) {
  // ruleid: sentinel-react-estado-derivado
  const [v, setV] = useState(props.valor);
  // ok: sentinel-react-estado-derivado
  const [aberto, setAberto] = useState(false);
  // ruleid: sentinel-react-dangerously
  return <div dangerouslySetInnerHTML={{ __html: html }} />;
}

export function Sanitizado({ html }: { html: string }) {
  // ok: sentinel-react-dangerously
  return <div dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(html) }} />;
}

export function SanitizadoEmVariasLinhas({ html }: { html: string }) {
  return (
    <li
      // ok: sentinel-react-dangerously
      dangerouslySetInnerHTML={{
        __html: DOMPurify.sanitize(html, { USE_PROFILES: { html: true } }),
      }}
    />
  );
}

export function Links({ url, outro }: { url: string; outro: string }) {
  // ruleid: sentinel-react-target-blank-sem-noopener
  const a = <a href={url} target="_blank">externo</a>;
  // ruleid: sentinel-react-target-blank-sem-noopener
  const b = <a className="x" href={outro} target="_blank" title="t">outro</a>;
  return [a, b];
}
