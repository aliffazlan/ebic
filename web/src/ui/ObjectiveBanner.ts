// Persistent "what am I supposed to do right now" reminder for the tutorial, so
// clicking through the dialogue fast doesn't lose the current task. Lives on the
// canvas host, same reasoning as TurnBanner.ts: Hud.render() wipes its subtree with
// innerHTML = "" on every store update and would tear this down mid-step otherwise.
// Unlike TurnBanner it doesn't auto-dismiss - it just tracks the active step's text.

export class ObjectiveBanner {
  private el: HTMLDivElement;

  constructor(host: HTMLElement) {
    this.el = document.createElement("div");
    this.el.className = "tutorial-objective";
    host.appendChild(this.el);
  }

  setText(text: string | null): void {
    if (text === null) {
      this.el.classList.remove("visible");
      return;
    }
    this.el.textContent = text;
    this.el.classList.add("visible");
  }

  destroy(): void {
    this.el.remove();
  }
}
