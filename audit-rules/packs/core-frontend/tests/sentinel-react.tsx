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
