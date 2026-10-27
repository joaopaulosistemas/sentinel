interface ScoreGaugeProps {
  score: number | null;
  size?: 'sm' | 'lg';
}

function getScoreColor(score: number): string {
  if (score < 40) return '#da1e28'; // red-60
  if (score < 70) return '#f1c21b'; // yellow-30
  return '#24a148'; // green-50
}

function getScoreLabel(score: number): string {
  if (score < 40) return 'Crítico';
  if (score < 70) return 'Regular';
  return 'Bom';
}

export function ScoreGauge({ score, size = 'lg' }: ScoreGaugeProps) {
  if (score === null) {
    return (
      <div style={{ textAlign: 'center', color: 'var(--s-text-4)' }}>
        <span style={{ fontSize: size === 'lg' ? '2rem' : '1.25rem' }}>—</span>
        <div style={{ fontSize: '0.75rem' }}>Sem dados</div>
      </div>
    );
  }

  const color = getScoreColor(score);
  const label = getScoreLabel(score);
  const fontSize = size === 'lg' ? '3rem' : '1.5rem';
  const labelSize = size === 'lg' ? '0.875rem' : '0.75rem';

  return (
    <div style={{ textAlign: 'center' }}>
      <div
        style={{
          fontSize,
          fontWeight: 700,
          color,
          lineHeight: 1.1,
        }}
      >
        {score}
      </div>
      <div style={{ fontSize: labelSize, color, fontWeight: 600 }}>{label}</div>
    </div>
  );
}
