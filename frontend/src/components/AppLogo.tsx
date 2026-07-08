/**
 * Cognologix logo component.
 *
 * Logo rule (ADR-013):
 *   dark background  → gradient glyph + white wordmark
 *   light background → gradient glyph + Grey #525957 wordmark
 *
 * Glyph SVG paths are the exact originals from the Claude Design handoff
 * (viewBox 0 0 361.37 183.17, group translated to origin).
 */

interface AppLogoProps {
  variant?: 'dark' | 'light';
  /** Height of the glyph in px; wordmark scales to match */
  height?: number;
  showWordmark?: boolean;
}


export default function AppLogo({
  variant = 'light',
  height = 26,
  showWordmark = true,
}: AppLogoProps) {
  const wordmarkColor = variant === 'dark' ? '#ffffff' : '#525957';
  // stable per-instance id so multiple logos on one page don't share a gradient def
  const gradId = `cglx-grad-${variant}`;

  return (
    <div
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: Math.round(height * 0.38),
        lineHeight: 1,
      }}
      role="img"
      aria-label="Cognologix"
    >
      {/* ── Glyph (exact paths from design handoff) ── */}
      <svg
        viewBox="0 0 361.37 183.17"
        height={height}
        style={{ width: 'auto', display: 'block', flexShrink: 0 }}
        aria-hidden="true"
      >
        <defs>
          <linearGradient
            id={gradId}
            x1="45.44"
            y1="190.63"
            x2="315.62"
            y2="48.78"
            gradientUnits="userSpaceOnUse"
          >
            <stop offset="0" stopColor="#fbcf32" />
            <stop offset="1" stopColor="#f05757" />
          </linearGradient>
        </defs>
        <g transform="translate(-779.77,-447.96)">
          {/* C bracket — grey on light bg, white override handled via fill prop */}
          <path
            fill={variant === 'dark' ? '#ffffff' : '#525957'}
            d="M937.34,594.56a84.73,84.73,0,0,1-70.47,36.57c-24.27,0-46.87-9.64-62.83-27.59
               -15.62-16.62-24.27-39.56-24.27-64.16,0-24.27,8.65-47.21,24.27-63.83
               C820,457.6,842.6,448,866.87,448a84.75,84.75,0,0,1,70.47,36.57,
               19.57,19.57,0,0,1-4.65,26.92c-9,6-20.94,4-26.92-5a46.88,46.88,0,0,
               0-38.9-20c-27.93,0-48.2,22.27-48.2,52.86,0,30.92,20.27,53.19,48.2,
               53.19a46.88,46.88,0,0,0,38.9-20c6-9,18-11,26.92-5A19.57,19.57,0,0,1,937.34,594.56Z"
          />
          {/* Interlocked circles — always gradient */}
          <path
            fill={`url(#${gradId})`}
            d="M1049.39,448c-41.55,0-76.53,27.9-87.66,65.9-8,18.52-15.94,18.17-21,
               18.17-1,0-17.46.6-23-.06-8.37-1-12.81-10.22-12.81-10.22v.1a40,40,0,1,0,
               .07,33.84v.1c3.76-8.55,11.17-9.52,11.17-9.52s15.19-.32,31.13-.32c5.42,0,
               9.79,6.49,13,13.6A91.3,91.3,0,0,0,966,576.86c.12.48.19.82.19.82v-.44a91.51,
               91.51,0,0,0,83.24,53.89c50.53,0,91.75-41.22,91.75-91.42A91.93,91.93,0,0,0,
               1049.39,448Zm0,144.61a53,53,0,1,1,52.86-52.86A53,53,0,0,1,1049.39,592.57Z"
          />
        </g>
      </svg>

      {/* ── Wordmark ── */}
      {showWordmark && (
        <span
          style={{
            fontFamily: "'Montserrat', 'Trebuchet MS', system-ui, sans-serif",
            fontWeight: 700,
            fontSize: Math.round(height * 0.9),
            color: wordmarkColor,
            letterSpacing: '-0.02em',
            whiteSpace: 'nowrap',
            lineHeight: 1,
          }}
        >
          cognologix
        </span>
      )}
    </div>
  );
}
