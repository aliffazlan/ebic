// Shared `.settings-row` builders, used by both the lobby SettingsScreen and
// the in-match Settings tab (Hud.renderSettingsPanel) - the same setting must
// look and behave identically wherever it's edited from.

import { hotkeyLabel } from "../app/AppSettings";

export function volumeRow(label: string, initial: number, onChange: (volume: number) => void): HTMLElement {
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

/**
 * A `.settings-row` with a "press a key" capture button. Escape while listening aborts
 * the capture (does not bind Escape itself) - the conventional rebind-UI affordance,
 * distinct from Escape's own default binding as the CANCEL hotkey.
 */
export function hotkeyRow(label: string, currentCode: string, onRebind: (code: string) => void): HTMLElement {
  const row = document.createElement("div");
  row.className = "settings-row";

  const labelEl = document.createElement("label");
  labelEl.textContent = label;

  const btn = document.createElement("button");
  btn.textContent = hotkeyLabel(currentCode);
  btn.addEventListener("click", () => {
    btn.textContent = "Press any key…";
    btn.classList.add("listening");
    const capture = (e: KeyboardEvent) => {
      window.removeEventListener("keydown", capture, true);
      btn.classList.remove("listening");
      if (e.key === "Escape") {
        btn.textContent = hotkeyLabel(currentCode);
        return;
      }
      e.preventDefault();
      btn.textContent = hotkeyLabel(e.code);
      onRebind(e.code);
    };
    window.addEventListener("keydown", capture, true);
  });

  row.append(labelEl, btn);
  return row;
}
