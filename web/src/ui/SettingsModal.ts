// A standalone volume-settings popup, opened from the lobby's "Settings"
// button. Owns its own open()/close() lifecycle rather than being tied to a
// parent screen's render cycle (unlike Hud.ts's modals, which are rebuilt
// wholesale on every store update) - appended straight to document.body
// with position: fixed so it works regardless of which screen it's opened
// from and doesn't depend on a positioned ancestor the way Hud's
// .modal-backdrop (position: absolute) does.

import { audioManager } from "../audio/AudioManager";

export class SettingsModal {
  private el: HTMLDivElement | null = null;

  open(): void {
    if (this.el) return;

    const backdrop = document.createElement("div");
    backdrop.className = "settings-backdrop";
    backdrop.addEventListener("click", (e) => {
      if (e.target === backdrop) this.close();
    });

    const panel = document.createElement("div");
    panel.className = "settings-panel";

    const header = document.createElement("div");
    header.className = "row";
    header.style.justifyContent = "space-between";
    header.style.alignItems = "center";
    const title = document.createElement("h2");
    title.textContent = "Settings";
    const closeBtn = document.createElement("button");
    closeBtn.textContent = "Close";
    closeBtn.addEventListener("click", () => this.close());
    header.append(title, closeBtn);
    panel.appendChild(header);

    panel.appendChild(
      this.volumeRow("Music volume", audioManager.getMusicVolume(), (v) => audioManager.setMusicVolume(v)),
    );
    panel.appendChild(
      this.volumeRow("SFX volume", audioManager.getSfxVolume(), (v) => audioManager.setSfxVolume(v)),
    );

    backdrop.appendChild(panel);
    document.body.appendChild(backdrop);
    this.el = backdrop;
  }

  close(): void {
    this.el?.remove();
    this.el = null;
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
}
