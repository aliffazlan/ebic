// Reached only from the main-menu SettingsScreen's "Hotkeys" button (see
// App.ts's showHotkeys) - the in-match Settings tab in Hud.ts keeps its own
// inline hotkey rows and does not link here. Same .centered-screen/
// .settings-panel chrome as SettingsScreen itself, since a short list of
// rebind rows doesn't need the larger .codex-window treatment.

import { getHotkey, setHotkey, type HotkeyAction } from "../app/AppSettings";
import type { Screen } from "./Screen";
import { hotkeyRow } from "./SettingsRows";

const HOTKEY_ROWS: ReadonlyArray<{ action: HotkeyAction; label: string }> = [
  { action: "move", label: "Move" },
  { action: "attack", label: "Attack" },
  { action: "ability1", label: "Ability 1" },
  { action: "ability2", label: "Ability 2" },
  { action: "ability3", label: "Ability 3" },
  { action: "cancel", label: "Deselect / cancel" },
];

export class HotkeysScreen implements Screen {
  private el: HTMLElement | null = null;
  private root: HTMLElement;
  private onBack: () => void;

  constructor(root: HTMLElement, onBack: () => void) {
    this.root = root;
    this.onBack = onBack;
  }

  mount(): void {
    const wrap = document.createElement("div");
    wrap.className = "centered-screen";

    const panel = document.createElement("div");
    panel.className = "settings-panel";
    wrap.appendChild(panel);

    const header = document.createElement("div");
    header.className = "row";
    header.style.justifyContent = "space-between";
    header.style.alignItems = "center";
    const title = document.createElement("h2");
    title.textContent = "Hotkeys";
    const backBtn = document.createElement("button");
    backBtn.textContent = "Back";
    backBtn.addEventListener("click", () => this.onBack());
    header.append(title, backBtn);
    panel.appendChild(header);

    for (const { action, label } of HOTKEY_ROWS) {
      panel.appendChild(hotkeyRow(label, getHotkey(action), (code) => setHotkey(action, code)));
    }

    this.root.appendChild(wrap);
    this.el = wrap;
  }

  unmount(): void {
    this.el?.remove();
    this.el = null;
  }

  getElement(): HTMLElement | null {
    return this.el;
  }
}
