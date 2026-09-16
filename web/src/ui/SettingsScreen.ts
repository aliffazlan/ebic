// The settings screen, reached from the lobby's "Settings" button. A full
// Screen like Auth/Lobby/Codex rather than a document.body-appended popup -
// it's navigated to and from with the same slide transitions as everything
// else, so it needs to live inside #app and participate in the same
// mount/unmount/getElement lifecycle.

import { audioManager } from "../audio/AudioManager";
import { getFastTransitions, setFastTransitions, getHotkey, setHotkey, type HotkeyAction } from "../app/AppSettings";
import { api, ApiError } from "../net/api";
import type { UnitDefinitionSnapshot } from "../types/contract";
import type { Screen } from "./Screen";
import { volumeRow, hotkeyRow } from "./SettingsRows";

const HOTKEY_ROWS: ReadonlyArray<{ action: HotkeyAction; label: string }> = [
  { action: "move", label: "Move" },
  { action: "attack", label: "Attack" },
  { action: "ability1", label: "Ability 1" },
  { action: "ability2", label: "Ability 2" },
  { action: "ability3", label: "Ability 3" },
  { action: "cancel", label: "Deselect / cancel" },
];

export interface SettingsCallbacks {
  onBack(): void;
  onFavouriteUnitChange(definitionId: string | null): void;
  onOpenChangelog(): void;
}

export class SettingsScreen implements Screen {
  private el: HTMLElement | null = null;
  private root: HTMLElement;
  private callbacks: SettingsCallbacks;
  private favouriteUnit: string | null;

  constructor(root: HTMLElement, favouriteUnit: string | null, callbacks: SettingsCallbacks) {
    this.root = root;
    this.favouriteUnit = favouriteUnit;
    this.callbacks = callbacks;
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
    title.textContent = "Settings";
    const backBtn = document.createElement("button");
    backBtn.textContent = "Back";
    backBtn.addEventListener("click", () => this.callbacks.onBack());
    header.append(title, backBtn);
    panel.appendChild(header);

    panel.appendChild(
      volumeRow("Music volume", audioManager.getMusicVolume(), (v) => audioManager.setMusicVolume(v)),
    );
    panel.appendChild(
      volumeRow("SFX volume", audioManager.getSfxVolume(), (v) => audioManager.setSfxVolume(v)),
    );
    panel.appendChild(
      this.toggleRow("Fast Transitions", getFastTransitions(), (on) => setFastTransitions(on)),
    );

    const hotkeysHeading = document.createElement("h3");
    hotkeysHeading.textContent = "Hotkeys";
    panel.appendChild(hotkeysHeading);
    for (const { action, label } of HOTKEY_ROWS) {
      panel.appendChild(hotkeyRow(label, getHotkey(action), (code) => setHotkey(action, code)));
    }

    const changelogBtn = document.createElement("button");
    changelogBtn.style.width = "100%";
    changelogBtn.textContent = "Changelog";
    changelogBtn.addEventListener("click", () => this.callbacks.onOpenChangelog());
    panel.appendChild(changelogBtn);

    const errorText = document.createElement("div");
    errorText.className = "error-text";
    const setError = (err: unknown) => {
      errorText.textContent = err instanceof ApiError ? err.message : "Something went wrong.";
    };

    panel.appendChild(this.favouriteRow(setError));
    panel.appendChild(errorText);

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

  /** Same .settings-row shape as volumeRow, with a checkbox instead of a slider. */
  private toggleRow(label: string, initial: boolean, onChange: (on: boolean) => void): HTMLElement {
    const row = document.createElement("div");
    row.className = "settings-row";

    const labelEl = document.createElement("label");
    labelEl.textContent = label;

    const box = document.createElement("input");
    box.type = "checkbox";
    box.checked = initial;
    box.addEventListener("change", () => onChange(box.checked));

    row.append(labelEl, box);
    return row;
  }

  /**
   * The hero this account always wants offered. Saved on change rather than behind a
   * button - there is one setting and no way to get it half-right, so a Save step would
   * only be something to forget.
   *
   * The list is the same GET /api/units the codex uses, so what can be favourited and what
   * can be drafted are one question with one answer.
   */
  private favouriteRow(setError: (err: unknown) => void): HTMLElement {
    const wrap = document.createElement("div");

    const row = document.createElement("div");
    row.className = "settings-row";

    const labelEl = document.createElement("label");
    labelEl.textContent = "Favourite unit";
    row.appendChild(labelEl);

    const select = document.createElement("select");
    select.style.flex = "2";
    select.disabled = true;
    const none = document.createElement("option");
    none.value = "";
    none.textContent = "None";
    select.appendChild(none);
    row.appendChild(select);
    wrap.appendChild(row);

    const hint = document.createElement("div");
    hint.className = "hint";
    hint.textContent = "Always offered as one of your two options in the matching draft round. "
      + "If your opponent has picked the same favourite, neither of you is offered them.";
    wrap.appendChild(hint);

    void api.getUnits()
      .then(({ units }) => {
        select.appendChild(optgroupFor("Champions", units.filter((u) => u.type === "CHAMPION")));
        select.appendChild(optgroupFor("Elites", units.filter((u) => u.type === "ELITE")));
        select.value = this.favouriteUnit ?? "";
        select.disabled = false;
      })
      .catch(setError);

    select.addEventListener("change", () => {
      const chosen = select.value === "" ? null : select.value;
      select.disabled = true;
      void api.setFavouriteUnit(chosen)
        .then((res) => {
          this.favouriteUnit = res.favouriteUnit;
          this.callbacks.onFavouriteUnitChange(res.favouriteUnit);
        })
        .catch((err) => {
          setError(err);
          select.value = this.favouriteUnit ?? "";
        })
        .finally(() => {
          select.disabled = false;
        });
    });

    return wrap;
  }
}

function optgroupFor(label: string, units: UnitDefinitionSnapshot[]): HTMLOptGroupElement {
  const group = document.createElement("optgroup");
  group.label = label;
  for (const unit of units) {
    const option = document.createElement("option");
    option.value = unit.definitionId;
    option.textContent = unit.name;
    group.appendChild(option);
  }
  return group;
}
