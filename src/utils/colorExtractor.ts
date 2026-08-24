export function getTrackGlowStyle(accentColor?: string): { [key: string]: string } {
  const primary = accentColor || '#8b5cf6';
  
  return {
    '--glow-primary': primary,
    '--glow-secondary': adjustColorBrightness(primary, -20),
    '--glow-subtle': adjustColorBrightness(primary, 40)
  };
}

export function adjustColorBrightness(hex: string, percent: number): string {
  let num = parseInt(hex.replace('#', ''), 16);
  if (isNaN(num)) return hex;

  let r = (num >> 16) + percent;
  let g = ((num >> 8) & 0x00ff) + percent;
  let b = (num & 0x0000ff) + percent;

  r = Math.min(255, Math.max(0, r));
  g = Math.min(255, Math.max(0, g));
  b = Math.min(255, Math.max(0, b));

  return `#${((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1)}`;
}
