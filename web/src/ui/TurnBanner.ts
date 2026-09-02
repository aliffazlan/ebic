// The big "YOUR TURN" flash over the board.
//
// Lives on the canvas host rather than inside the HUD, because Hud.render()
// wipes its whole subtree with innerHTML = "" on every store update and would
// tear the banner down mid-animation - the same reason Tooltip escapes to
// document.body. The canvas host is already position: relative, so absolute
// positioning inside it lands over the board and nowhere else.

const VISIBLE_CLASS = "visible";

export class TurnBanner {
  private el: HTMLDivElement;

  constructor(host: HTMLElement, text = "YOUR TURN") {
    this.el = document.createElement("div");
    this.el.className = "turn-banner";
    this.el.textContent = text;
    host.appendChild(this.el);
  }

  /**
   * Plays the banner from the start, even if it's already playing - two turns
   * in quick succession should each get a full flash rather than the second
   * being swallowed. Removing the class and forcing a reflow before re-adding
   * it is what restarts a CSS animation.
   */
  show(): void {
    this.el.classList.remove(VISIBLE_CLASS);
    void this.el.offsetWidth;
    this.el.classList.add(VISIBLE_CLASS);
  }

  destroy(): void {
    this.el.remove();
  }
}
