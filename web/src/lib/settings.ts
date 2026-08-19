// Client-side preferences, stored locally. Presentation only — never affects authority.

export interface Settings {
  sounds: boolean;
  autoFlip: boolean; // orient the board to your colour
  autoQueen: boolean; // promote straight to a queen without the picker
  confirmResign: boolean;
}

const KEY = 'chessoc-settings';
const DEFAULTS: Settings = { sounds: true, autoFlip: true, autoQueen: false, confirmResign: true };

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
