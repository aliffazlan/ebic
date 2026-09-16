// The settings screen, reached from the lobby's "Settings" button. A full
// Screen like Auth/Lobby/Codex rather than a document.body-appended popup -
// it's navigated to and from with the same slide transitions as everything
// else, so it needs to live inside #app and participate in the same
// mount/unmount/getElement lifecycle.

import { audioManager } from "../audio/AudioManager";
import { getFastTransitions, setFastTransitions } from "../app/AppSettings";
import { api, ApiError } from "../net/api";
import type { UnitDefinitionSnapshot } from "../types/contract";
import type { Screen } from "./Screen";

export interface SettingsCallbacks {
  onBack(): void;
  onFavouriteUnitChange(definitionId: string | null): void;
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
      this.volumeRow("Music volume", audioManager.getMusicVolume(), (v) => audioManager.setMusicVolume(v)),
    );
    panel.appendChild(
      this.volumeRow("SFX volume", audioManager.getSfxVolume(), (v) => audioManager.setSfxVolume(v)),
    );
    panel.appendChild(
      this.toggleRow("Fast Transitions", getFastTransitions(), (on) => setFastTransitions(on)),
    );

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

  private volumeRow(label: string, initial: number, onChange: (volume: number) => void): HTMLElement {
    const row = document.createElement("div");
    row.className = "settings-row";

    const labelEl = document.createElement("label");
    labelEl.textContent = label;

    const slider = document.createElement("input");
    slider.type = "range";
    slider.min = "0";
    slider.max = "100";
    slider.value = String(Math.round(initial * 100));

    const valueEl = document.createElement("span");
    valueEl.className = "settings-row-value";
    valueEl.textContent = `${slider.value}%`;

    slider.addEventListener("input", () => {
      const volume = Number(slider.value) / 100;
      valueEl.textContent = `${slider.value}%`;
      onChange(volume);
    });

    row.append(labelEl, slider, valueEl);
    return row;
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
