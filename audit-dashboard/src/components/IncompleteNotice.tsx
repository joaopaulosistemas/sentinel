interface IIncompleteNoticeProps {
  incomplete: boolean | undefined;
  reasons: string[] | undefined;
}

/** Avisa, ao lado da nota, que algum analisador falhou e que a nota pode estar mais alta que a realidade. */
export function IncompleteNotice({ incomplete, reasons }: IIncompleteNoticeProps) {
  if (!incomplete) return null;
  return (
    <div
      role="status"
      style={{
        borderLeft: '3px solid var(--s-warning-text, #b28600)',
        background: 'var(--s-surface-2, transparent)',
        padding: '8px 12px',
        fontSize: 13,
      }}
    >
      <strong style={{ fontWeight: 600 }}>Resultado incompleto.</strong> A nota pode estar mais alta que a realidade, porque
      parte da análise não terminou:
      <ul style={{ margin: '4px 0 0', paddingLeft: 18 }}>
        {(reasons ?? []).map((reason) => (
          <li key={reason}>{reason}</li>
        ))}
      </ul>
    </div>
  );
}
