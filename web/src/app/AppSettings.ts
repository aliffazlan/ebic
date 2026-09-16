// General app-level settings, persisted separately from AudioManager's
// "ebic:settings" key - a second independent module loading/saving that same
// blob would clobber whichever one wrote last, and a boolean like
// fastTransitions isn't an audio concern anyway. Same try/catch-wrapped
// load/save pattern as AudioManager for a disabled/unavailable localStorage.
//
// fastTransitions has no behaviour wired to it yet - it's a persisted UI stub
// for a future pass.

const STORAGE_KEY = "ebic:app-settings";

/** One in-match action a hotkey can trigger. Move/Attack are two entries in a
 *  unit's ability array like any other (see Hud.renderUnitPanel); ability1-3
 *  bind to the first three non-move/attack abilities by array position,
 *  regardless of whether they're passive - see Hud.ts for how a passive
 *  ability still consumes its slot but never shows a hotkey label. */
export type HotkeyAction = "move" | "attack" | "ability1" | "ability2" | "ability3" | "cancel";

const HOTKEY_ACTIONS: HotkeyAction[] = ["move", "attack", "ability1", "ability2", "ability3", "cancel"];

const DEFAULT_HOTKEYS: Record<HotkeyAction, string> = {
  move: "KeyM",
  attack: "KeyA",
  ability1: "Digit1",
  ability2: "Digit2",
  ability3: "Digit3",
  cancel: "Escape",
};

interface PersistedAppSettings {
  fastTransitions: boolean;
  hotkeys: Record<HotkeyAction, string>;
}

const DEFAULTS: PersistedAppSettings = { fastTransitions: false, hotkeys: { ...DEFAULT_HOTKEYS } };

function load(): PersistedAppSettings {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return { fastTransitions: DEFAULTS.fastTransitions, hotkeys: { ...DEFAULT_HOTKEYS } };
    const parsed = JSON.parse(raw) as Partial<PersistedAppSettings>;
    const hotkeys = { ...DEFAULT_HOTKEYS };
    const parsedHotkeys = parsed.hotkeys as Partial<Record<HotkeyAction, unknown>> | undefined;
    if (parsedHotkeys) {
      for (const action of HOTKEY_ACTIONS) {
        const code = parsedHotkeys[action];
        if (typeof code === "string") hotkeys[action] = code;
      }
    }
    return {
      fastTransitions:
        typeof parsed.fastTransitions === "boolean" ? parsed.fastTransitions : DEFAULTS.fastTransitions,
      hotkeys,
    };
  } catch {
    return { fastTransitions: DEFAULTS.fastTransitions, hotkeys: { ...DEFAULT_HOTKEYS } };
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

export function getHotkeys(): Record<HotkeyAction, string> {
  return settings.hotkeys;
}

export function getHotkey(action: HotkeyAction): string {
  return settings.hotkeys[action];
}

/**
 * Rebinds `action` to `code`, swapping with whichever other action currently
 * holds that code (if any) rather than leaving two actions on the same key or
 * one action with none at all.
 */
export function setHotkey(action: HotkeyAction, code: string): void {
  const hotkeys = { ...settings.hotkeys };
  const previousCode = hotkeys[action];
  const conflicting = HOTKEY_ACTIONS.find((other) => other !== action && hotkeys[other] === code);
  if (conflicting) hotkeys[conflicting] = previousCode;
  hotkeys[action] = code;
  settings = { ...settings, hotkeys };
  save(settings);
}

/** Reverse lookup for the keydown dispatcher - null if `code` isn't bound to anything. */
export function actionForHotkeyCode(code: string): HotkeyAction | null {
  return HOTKEY_ACTIONS.find((action) => settings.hotkeys[action] === code) ?? null;
}

const CODE_LABEL_OVERRIDES: Record<string, string> = {
  Escape: "Esc",
  Space: "Space",
  ArrowUp: "↑",
  ArrowDown: "↓",
  ArrowLeft: "←",
  ArrowRight: "→",
};

/** Display string for a stored `KeyboardEvent.code`, e.g. "KeyM" -> "M", "Digit1" -> "1". */
export function hotkeyLabel(code: string): string {
  if (CODE_LABEL_OVERRIDES[code]) return CODE_LABEL_OVERRIDES[code];
  if (code.startsWith("Key")) return code.slice(3);
  if (code.startsWith("Digit")) return code.slice(5);
  return code;
}
