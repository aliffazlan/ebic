// The small, non-blocking "wrong move, warrior!" rejection popup. Deliberately NOT a
// backdrop/modal - per the user's explicit direction, an off-script action should look
// like it simply did nothing (buttons/board stay interactive, exactly as a real match
// would if the server silently refused an illegal action); this toast is only a hint
// as to why nothing happened. Interrupt-and-replace: a new rejection immediately
// replaces whatever's showing and restarts its own auto-dismiss timer, so rapid
// mis-clicks never pile up stale scoldings.

import { faceUrl } from "../units/UnitArt";
import type { Speaker } from "../tutorial/types";

const SPEAKER_NAMES: Record<Speaker, string> = {
  valor: "Valor",
  harbinger: "Harbinger",
  auroth: "Auroth",
  evayne: "Evayne",
  thaddeus: "Thaddeus",
};

const AUTO_DISMISS_MS = 2600;

export class WrongMoveToast {
  private root: HTMLElement;
  private el: HTMLDivElement | null = null;
  private timer = 0;

  constructor(root: HTMLElement) {
    this.root = root;
  }

  show(speaker: Speaker, text: string): void {
    window.clearTimeout(this.timer);
    this.el?.remove();

    const el = document.createElement("div");
    el.className = "wrong-move-toast";

    const portrait = document.createElement("img");
    portrait.className = "wrong-move-portrait";
    portrait.src = faceUrl(speaker);
    portrait.alt = SPEAKER_NAMES[speaker];
    el.appendChild(portrait);

    const col = document.createElement("div");
    col.className = "wrong-move-text-col";
    const name = document.createElement("div");
    name.className = "wrong-move-speaker";
    name.textContent = SPEAKER_NAMES[speaker];
    col.appendChild(name);
    const body = document.createElement("div");
    body.className = "wrong-move-text";
    body.textContent = text;
    col.appendChild(body);
    el.appendChild(col);

    this.root.appendChild(el);
    this.el = el;
    this.timer = window.setTimeout(() => this.hide(), AUTO_DISMISS_MS);
  }

  private hide(): void {
    this.el?.remove();
    this.el = null;
  }

  destroy(): void {
    window.clearTimeout(this.timer);
    this.el?.remove();
    this.el = null;
  }
}
