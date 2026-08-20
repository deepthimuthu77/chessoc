// Client-side preferences, stored locally. Presentation only — never affects authority.

export interface Settings {
  sounds: boolean;
  autoFlip: boolean; // orient the board to your colour
  autoQueen: boolean; // promote straight to a queen without the picker
  confirmResign: boolean;
  reducedMotion: boolean; // disable non-essential animations
}

const KEY = 'chessoc-settings';
const DEFAULTS: Settings = {
  sounds: true,
  autoFlip: true,
  autoQueen: false,
  confirmResign: true,
  reducedMotion: false,
};

/** True when the user (setting) or the OS (prefers-reduced-motion) asks for reduced motion. */
export function prefersReducedMotion(): boolean {
  if (typeof window === 'undefined') {
    return false;
  }
  return getSettings().reducedMotion || window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

export function getSettings(): Settings {
  if (typeof window === 'undefined') {
    return DEFAULTS;
  }
  try {
    return { ...DEFAULTS, ...(JSON.parse(window.localStorage.getItem(KEY) ?? '{}') as Partial<Settings>) };
  } catch {
    return DEFAULTS;
  }
}

export function setSettings(s: Settings): void {
  window.localStorage.setItem(KEY, JSON.stringify(s));
}
