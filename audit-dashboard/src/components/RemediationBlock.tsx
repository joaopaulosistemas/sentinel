import type { Remediation } from '../types';

const codeStyle = {
  margin: 0,
  padding: '0.75rem',
  borderRadius: '4px',
  overflow: 'auto',
  fontSize: '0.8125rem',
  whiteSpace: 'pre-wrap' as const,
};

/** Como corrigir: resumo, exemplo antes/depois e referências oficiais. */
export function RemediationBlock({ remediation }: { remediation: Remediation }) {
  const hasExamples = !!(remediation.before || remediation.after);
  return (
    <div
      style={{
        marginTop: '0.75rem',
        padding: '0.75rem 1rem',
        background: '#edf5ff',
        borderLeft: '4px solid #0f62fe',
        borderRadius: '2px',
      }}
    >
      <strong>Como corrigir</strong>
      {remediation.summary && <p style={{ margin: '0.25rem 0 0.5rem' }}>{remediation.summary}</p>}
      {hasExamples && (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
          {remediation.before && (
            <div>
              <div style={{ fontSize: '0.75rem', color: '#a2191f', marginBottom: '0.25rem' }}>Antes</div>
              <pre style={{ ...codeStyle, background: '#fff1f1', color: '#161616' }}>
                <code>{remediation.before}</code>
              </pre>
            </div>
          )}
          {remediation.after && (
            <div>
              <div style={{ fontSize: '0.75rem', color: '#0e6027', marginBottom: '0.25rem' }}>Depois</div>
              <pre style={{ ...codeStyle, background: '#defbe6', color: '#161616' }}>
                <code>{remediation.after}</code>
              </pre>
            </div>
          )}
        </div>
      )}
      {remediation.references.length > 0 && (
        <p style={{ marginTop: '0.5rem', fontSize: '0.8125rem' }}>
          <strong>Referências:</strong>{' '}
          {remediation.references.map((url, i) => (
            <span key={url}>
              {i > 0 && ' · '}
              <a href={url} target="_blank" rel="noreferrer noopener">
                {url.replace(/^https?:\/\//, '').split('/').slice(0, 2).join('/')}
              </a>
            </span>
          ))}
        </p>
      )}
    </div>
  );
}
