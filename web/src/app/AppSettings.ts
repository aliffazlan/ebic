// General app-level settings, persisted separately from AudioManager's
// "ebic:settings" key - a second independent module loading/saving that same
// blob would clobber whichever one wrote last, and a boolean like
// fastTransitions isn't an audio concern anyway. Same try/catch-wrapped
// load/save pattern as AudioManager for a disabled/unavailable localStorage.
//
// fastTransitions has no behaviour wired to it yet - it's a persisted UI stub
// for a future pass.

const STORAGE_KEY = "ebic:app-settings";

interface PersistedAppSettings {
  fastTransitions: boolean;
}

const DEFAULTS: PersistedAppSettings = { fastTransitions: false };

function load(): PersistedAppSettings {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return { ...DEFAULTS };
    const parsed = JSON.parse(raw) as Partial<PersistedAppSettings>;
    return {
      fastTransitions:
        typeof parsed.fastTransitions === "boolean" ? parsed.fastTransitions : DEFAULTS.fastTransitions,
    };
  } catch {
    return { ...DEFAULTS };
  }
}

function save(settings: PersistedAppSettings): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
  } catch {
    // Private browsing / storage disabled / quota exceeded - the choice
    // just won't survive a reload. Not worth surfacing to the user.
  }
}

let settings = load();

export function getFastTransitions(): boolean {
  return settings.fastTransitions;
}

export function setFastTransitions(value: boolean): void {
  settings = { ...settings, fastTransitions: value };
  save(settings);
}
