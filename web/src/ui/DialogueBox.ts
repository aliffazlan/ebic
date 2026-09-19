// The tutorial's story dialogue box: portrait + speaker name + line, click (or
// Space/Enter) to advance through a queued list. Its backdrop is a full-viewport,
// pointer-capturing overlay - the same "input lock" idiom as ScreenTransition's fade
// overlay - so board/Hud clicks are physically impossible while a line is showing,
// with no cooperation needed from either of them.

import { faceUrl } from "../units/UnitArt";
import type { DialogueLine, Speaker } from "../tutorial/types";

const SPEAKER_NAMES: Record<Speaker, string> = {
  valor: "Valor",
  harbinger: "Harbinger",
  auroth: "Auroth",
  evayne: "Evayne",
  thaddeus: "Thaddeus",
};

export class DialogueBox {
  private root: HTMLElement;
  private backdrop: HTMLDivElement | null = null;
  private lines: DialogueLine[] = [];
  private index = 0;
  private resolveShow: (() => void) | null = null;
  private keyListener = (e: KeyboardEvent) => {
    if (e.code === "Space" || e.code === "Enter") {
      e.preventDefault();
      this.advance();
    }
  };

  constructor(root: HTMLElement) {
    this.root = root;
  }

  /** Shows each line in order; resolves once the player has clicked/pressed through the last one. */
  showLines(lines: DialogueLine[]): Promise<void> {
    this.lines = lines;
    this.index = 0;
    this.mountBackdrop();
    this.renderCurrent();
    return new Promise((resolve) => {
      this.resolveShow = resolve;
    });
  }

  destroy(): void {
    window.removeEventListener("keydown", this.keyListener);
    this.backdrop?.remove();
    this.backdrop = null;
    this.resolveShow = null;
  }

  private mountBackdrop(): void {
    if (this.backdrop) return;
    const backdrop = document.createElement("div");
    backdrop.className = "dialogue-backdrop";
    backdrop.addEventListener("click", () => this.advance());
    window.addEventListener("keydown", this.keyListener);
    this.root.appendChild(backdrop);
    this.backdrop = backdrop;
  }

  private renderCurrent(): void {
    if (!this.backdrop) return;
    const line = this.lines[this.index];
    this.backdrop.innerHTML = "";

    const box = document.createElement("div");
    box.className = "dialogue-box";

    const portrait = document.createElement("img");
    portrait.className = "dialogue-portrait";
    portrait.src = faceUrl(line.speaker);
    portrait.alt = SPEAKER_NAMES[line.speaker];
    box.appendChild(portrait);

    const col = document.createElement("div");
    col.className = "dialogue-text-col";

    const name = document.createElement("div");
    name.className = "dialogue-speaker";
    name.textContent = SPEAKER_NAMES[line.speaker];
    col.appendChild(name);

    const text = document.createElement("div");
    text.className = "dialogue-text";
    text.textContent = line.text;
    col.appendChild(text);

    const hint = document.createElement("div");
    hint.className = "dialogue-advance-hint";
    hint.textContent = this.index < this.lines.length - 1 ? "Click to continue" : "Click to close";
    col.appendChild(hint);

    box.appendChild(col);
    this.backdrop.appendChild(box);
  }

  private advance(): void {
    this.index++;
    if (this.index >= this.lines.length) {
      this.close();
      return;
    }
    this.renderCurrent();
  }

  private close(): void {
    window.removeEventListener("keydown", this.keyListener);
    this.backdrop?.remove();
    this.backdrop = null;
    const resolve = this.resolveShow;
    this.resolveShow = null;
    resolve?.();
  }
}
