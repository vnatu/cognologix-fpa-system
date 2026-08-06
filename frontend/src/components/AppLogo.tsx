/**
 * Cognologix logo component.
 *
 * Logo rule:
 *   dark background  → gradient glyph + white wordmark
 *   light background → gradient glyph + Grey #525957 wordmark
 *
 * Glyph is the official brand asset (src/assets/cglx-glyph.png).
 */

import glyphUrl from '@/assets/cglx-glyph.png';

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
      <img
        src={glyphUrl}
        alt=""
        height={height}
        style={{ width: 'auto', display: 'block', flexShrink: 0 }}
        aria-hidden="true"
        draggable={false}
      />

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
