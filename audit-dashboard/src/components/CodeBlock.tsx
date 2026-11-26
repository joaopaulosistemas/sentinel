import { codeLines, codeLocation } from '../utils/codeContext';

interface ICodeBlockProps {
  filePath: string;
  /** linha do achado no arquivo */
  line: number;
  context: string;
  /** número da primeira linha de `context` no arquivo */
  start: number;
}

const mono = "'IBM Plex Mono', ui-monospace, monospace";

/** Código real do arquivo ao redor do achado, com número de linha e a linha do achado marcada (cor, ▶ e texto para leitor de tela). */
export function CodeBlock({ filePath, line, context, start }: ICodeBlockProps) {
  const lines = codeLines(context, start, line);
  if (lines.length === 0) return null;
  const where = codeLocation(filePath, line);
  return (
    <div style={{ background: '#161616', color: '#f4f4f4', fontFamily: mono, fontSize: '0.8125rem', lineHeight: '22px', marginTop: '0.5rem' }}>
      <div style={{ padding: '6px 16px', borderBottom: '1px solid #3a4150', fontSize: 12, color: '#c6cbd4', overflowWrap: 'anywhere' }}>{where}</div>
      <div role="region" aria-label={`Código de ${where}`} tabIndex={0} style={{ padding: '8px 0', overflowX: 'auto' }}>
        {lines.map((l) => (
          <div key={l.n} style={{ display: 'flex', whiteSpace: 'pre', background: l.hit ? '#5a2328' : 'transparent' }}>
            <span aria-hidden="true" style={{ flex: 'none', width: 20, textAlign: 'center', color: '#ffb3b8' }}>{l.hit ? '▶' : ''}</span>
            <span aria-hidden="true" style={{ flex: 'none', width: 36, paddingRight: 12, textAlign: 'right', color: '#aab3c2' }}>{l.n}</span>
            <span style={{ paddingRight: 16 }}>{l.text}</span>
            {l.hit && <span className="sentinel-sr-only"> (linha do achado)</span>}
          </div>
        ))}
      </div>
    </div>
  );
}
