/** Common interface every top-level screen implements (auth / lobby / match). */
export interface Screen {
  mount(): void;
  unmount(): void;
  /**
   * The screen's root DOM node, for screens that support an animated
   * hand-off (see App.ts's setScreen). Without it, App always falls back
   * to an instant swap.
   */
  getElement?(): HTMLElement | null;
}
