// Owns the one <audio> element for menu music and the persisted volume
// settings. A module-level singleton, same pattern as `api` in net/api.ts,
// so any screen can import { audioManager } directly rather than threading
// it through every constructor.
//
// localStorage is a genuinely new pattern for this codebase (auth is
// cookie-based, nothing else persists client-side) - wrapped in try/catch
// throughout so a disabled/unavailable localStorage (private browsing,
// etc.) degrades to in-memory defaults rather than throwing.

const STORAGE_KEY = "ebic:settings";
const DEFAULT_VOLUME = 0.5;
const MUSIC_URL = "/assets/music/main_menu.mp3";

interface PersistedSettings {
  musicVolume: number;
  sfxVolume: number;
}

function clamp01(value: number): number {
  return Math.min(1, Math.max(0, value));
}

function loadSettings(): PersistedSettings {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return { musicVolume: DEFAULT_VOLUME, sfxVolume: DEFAULT_VOLUME };
    const parsed = JSON.parse(raw) as Partial<PersistedSettings>;
    return {
      musicVolume: typeof parsed.musicVolume === "number" ? clamp01(parsed.musicVolume) : DEFAULT_VOLUME,
      sfxVolume: typeof parsed.sfxVolume === "number" ? clamp01(parsed.sfxVolume) : DEFAULT_VOLUME,
    };
  } catch {
    return { musicVolume: DEFAULT_VOLUME, sfxVolume: DEFAULT_VOLUME };
  }
}

function saveSettings(settings: PersistedSettings): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
  } catch {
    // Private browsing / storage disabled / quota exceeded - the volume
    // choice just won't survive a reload. Not worth surfacing to the user.
  }
}

class AudioManager {
  private music = new Audio(MUSIC_URL);
  private settings: PersistedSettings;

  constructor() {
    this.settings = loadSettings();
    this.music.loop = true;
    this.music.preload = "auto";
    this.music.volume = this.settings.musicVolume;
  }

  /**
   * Warms the browser cache for the music file - one of the loading
   * screen's preload tasks. Resolves on "canplaythrough" or "error",
   * whichever comes first (a failed load is not a loading failure, same
   * philosophy as the rest of the app's art preloading); resolves
   * immediately if already sufficiently buffered.
   */
  preloadMusic(): Promise<void> {
    if (this.music.readyState >= HTMLMediaElement.HAVE_ENOUGH_DATA) {
      return Promise.resolve();
    }
    return new Promise((resolve) => {
      const done = () => {
        this.music.removeEventListener("canplaythrough", done);
        this.music.removeEventListener("error", done);
        resolve();
      };
      this.music.addEventListener("canplaythrough", done);
      this.music.addEventListener("error", done);
      this.music.load();
    });
  }

  /**
   * Starts the looping menu music. Call only from a real user gesture
   * (click-to-continue) - browsers block audio autoplay before one, and
   * .play() rejects in that case; .catch()'d defensively regardless so a
   * blocked/failed play never throws into the caller.
   */
  playMusic(): void {
    void this.music.play().catch(() => {});
  }

  getMusicVolume(): number {
    return this.settings.musicVolume;
  }

  setMusicVolume(volume: number): void {
    this.settings.musicVolume = clamp01(volume);
    this.music.volume = this.settings.musicVolume;
    saveSettings(this.settings);
  }

  /** Persisted only - no sfx playback exists yet. */
  getSfxVolume(): number {
    return this.settings.sfxVolume;
  }

  setSfxVolume(volume: number): void {
    this.settings.sfxVolume = clamp01(volume);
    saveSettings(this.settings);
  }
}

export const audioManager = new AudioManager();
