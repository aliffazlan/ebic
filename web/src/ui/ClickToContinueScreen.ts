// Sits between LoadingScreen and the real auth/lobby flow, for one reason:
// browsers block audio autoplay before a genuine user gesture, and this
// click is that gesture - App calls audioManager.playMusic() from the same
// handler that advances past this screen.

import type { Screen } from "./Screen";

export class ClickToContinueScreen implements Screen {
  private el: HTMLDivElement | null = null;
  private root: HTMLElement;
  private onContinue: () => void;

  constructor(root: HTMLElement, onContinue: () => void) {
    this.root = root;
    this.onContinue = onContinue;
  }

  mount(): void {
    const wrap = document.createElement("div");
    wrap.className = "click-to-continue-screen";
    wrap.textContent = "Click to continue";
    wrap.addEventListener("click", () => this.onContinue(), { once: true });
    this.root.appendChild(wrap);
    this.el = wrap;
  }

  unmount(): void {
    this.el?.remove();
    this.el = null;
  }
}
