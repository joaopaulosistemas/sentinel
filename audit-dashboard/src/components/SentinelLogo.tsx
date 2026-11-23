/** Logo: escudo com um olho (o "vigia" do Sentinel). */
export function SentinelLogo({ size = 24 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true" focusable="false">
      <path d="M16 2.5 4.5 6.8v8.2c0 7.2 4.9 12.4 11.5 14.5 6.6-2.1 11.5-7.3 11.5-14.5V6.8z" fill="#4589ff" />
      <ellipse cx="16" cy="15" rx="6.2" ry="4" fill="none" stroke="#fff" strokeWidth="2" />
      <circle cx="16" cy="15" r="1.9" fill="#fff" />
    </svg>
  );
}
