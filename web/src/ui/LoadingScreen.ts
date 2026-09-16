// First screen the app shows: warms the browser cache for unit art and the
// menu music before anything else needs them, then hands off to whatever
// comes next (App wires that to ClickToContinueScreen).
//
// Deliberately lightweight - a plain <img> prefetch per id (same technique
// UnitArt.ts's warmPortraits already uses for portraits, extended here to
// icons too), not PixiJS's Assets.load()/RenderTexture pipeline
// (UnitIconFactory.preload), which needs a live Application/canvas context
// that doesn't exist this early. The real per-match preload still runs
// exactly as it does today when a match actually starts, just against an
// already-warm cache.

import { audioManager } from "../audio/AudioManager";
import { ALL_ART_IDS, faceUrl, portraitUrl } from "../units/UnitArt";
import type { Screen } from "./Screen";
import { LOGO_URL } from "./Logo";

function preloadImage(url: string): Promise<void> {
  return new Promise((resolve) => {
    const img = new Image();
    // A missing file is not a loading failure - same philosophy as
    // warmPortraits and UnitIconFactory: missing art degrades to a
    // placeholder later, it never blocks anything now.
    img.onload = () => resolve();
    img.onerror = () => resolve();
    img.src = url;
  });
}

export class LoadingScreen implements Screen {
  private el: HTMLDivElement | null = null;
  private fill: HTMLDivElement | null = null;
  private root: HTMLElement;
  private onDone: () => void;

  constructor(root: HTMLElement, onDone: () => void) {
    this.root = root;
    this.onDone = onDone;
  }

  mount(): void {
    const wrap = document.createElement("div");
    wrap.className = "loading-screen";

    const panel = document.createElement("div");
    panel.className = "loading-panel";

    const title = document.createElement("h1");
    title.textContent = "EBIC";
    panel.appendChild(title);

    const hint = document.createElement("div");
    hint.className = "hint";
    hint.textContent = "Loading...";
    panel.appendChild(hint);

    const track = document.createElement("div");
    track.className = "loading-progress-track";
    const fill = document.createElement("div");
    fill.className = "loading-progress-fill";
    track.appendChild(fill);
    panel.appendChild(track);
    this.fill = fill;

    wrap.appendChild(panel);
    this.root.appendChild(wrap);
    this.el = wrap;

    void this.runPreload();
  }

  unmount(): void {
    this.el?.remove();
    this.el = null;
    this.fill = null;
  }

  private setProgress(fraction: number): void {
    if (this.fill) this.fill.style.width = `${Math.round(fraction * 100)}%`;
  }

  private async runPreload(): Promise<void> {
    const ids = ALL_ART_IDS;
    const total = ids.length * 2 + 2; // icon + portrait per id, + music + logo
    let done = 0;
    const tick = () => {
      done += 1;
      this.setProgress(done / total);
    };

    const tasks = ids.flatMap((id) => [
      preloadImage(faceUrl(id)).then(tick),
      preloadImage(portraitUrl(id)).then(tick),
    ]);
    tasks.push(audioManager.preloadMusic().then(tick));
    tasks.push(preloadImage(LOGO_URL).then(tick));

    await Promise.all(tasks);
    this.onDone();
  }
}
