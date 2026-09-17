// A small, non-animated popup for surfacing an error message with a single OK button,
// reusing the same .modal-backdrop/.modal-panel chrome as the draft/attribute/game-over
// overlays in Hud.ts. Used by the lobby screens (Create/Join Match, the lobby room)
// instead of inline error text.

export function showErrorModal(host: HTMLElement, message: string, onDismiss?: () => void): void {
  const backdrop = document.createElement("div");
  backdrop.className = "modal-backdrop";

  const panel = document.createElement("div");
  panel.className = "modal-panel error-modal-panel";
  backdrop.appendChild(panel);

  const text = document.createElement("div");
  text.textContent = message;
  panel.appendChild(text);

  const okBtn = document.createElement("button");
  okBtn.className = "primary";
  okBtn.textContent = "OK";
  okBtn.addEventListener("click", () => {
    backdrop.remove();
    onDismiss?.();
  });
  panel.appendChild(okBtn);

  host.appendChild(backdrop);
}
