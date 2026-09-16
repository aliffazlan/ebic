// Reads CHANGELOG.md straight from the repo root via the "virtual:changelog"
// module (see vite.config.ts's changelogPlugin) rather than a copy anywhere
// under web/ - the file has exactly one home either way. A plain `?raw`
// import can't resolve outside the project root, hence the plugin.

import CHANGELOG_MD from "virtual:changelog";
import { marked } from "marked";
import type { Screen } from "./Screen";

/**
 * Reached only from the main-menu Settings screen (not the in-match Settings
 * tab - see Hud.ts). Same "own bordered window" treatment as CodexScreen,
 * whose .codex-screen/.codex-window/.codex-toolbar classes are already
 * generic enough to reuse verbatim rather than duplicating that CSS here.
 */
export class ChangelogScreen implements Screen {
  private root: HTMLElement;
  private onBack: () => void;
  private el: HTMLElement | null = null;

  constructor(root: HTMLElement, onBack: () => void) {
    this.root = root;
    this.onBack = onBack;
  }

  mount(): void {
    const wrap = document.createElement("div");
    wrap.className = "codex-screen";
    this.el = wrap;
    this.root.appendChild(wrap);

    const windowEl = document.createElement("div");
    windowEl.className = "codex-window";
    wrap.appendChild(windowEl);

    const toolbar = document.createElement("div");
    toolbar.className = "codex-toolbar";
    windowEl.appendChild(toolbar);

    const backBtn = document.createElement("button");
    backBtn.textContent = "Back";
    backBtn.addEventListener("click", () => this.onBack());
    toolbar.appendChild(backBtn);

    const title = document.createElement("h1");
    title.textContent = "Changelog";
    toolbar.appendChild(title);

    const body = document.createElement("div");
    body.className = "changelog-body";
    // Synchronous: no async marked extensions are registered anywhere in this app.
    body.innerHTML = marked.parse(CHANGELOG_MD) as string;
    windowEl.appendChild(body);
  }

  unmount(): void {
    this.el?.remove();
    this.el = null;
  }

  getElement(): HTMLElement | null {
    return this.el;
  }
}
