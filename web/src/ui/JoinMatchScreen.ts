// Dedicated top-level screen for finding a match to join: a manually-refreshed
// browser of public lobbies (select a row, then press JOIN LOBBY), plus a
// join-by-code box that works for both public and private lobbies (a public
// lobby's code stays valid even after it's listed - this box doesn't
// special-case how the code was found).

import { api, ApiError } from "../net/api";
import { showErrorModal } from "./ErrorModal";
import type { Screen } from "./Screen";
import type { PublicLobbySummary, Team } from "../types/contract";

export interface JoinMatchCallbacks {
  onBack(): void;
  onJoined(matchId: string, yourTeam: Team, playerOneName: string, isPublic: boolean): void;
}

export class JoinMatchScreen implements Screen {
  private el: HTMLElement | null = null;
  private root: HTMLElement;
  private callbacks: JoinMatchCallbacks;
  private listEl: HTMLElement | null = null;
  private refreshBtn: HTMLButtonElement | null = null;
  private joinSelectedBtn: HTMLButtonElement | null = null;

  private selectedLobby: PublicLobbySummary | null = null;
  private selectedRow: HTMLElement | null = null;

  constructor(root: HTMLElement, callbacks: JoinMatchCallbacks) {
    this.root = root;
    this.callbacks = callbacks;
  }

  mount(): void {
    this.render();
    void this.refresh();
  }

  unmount(): void {
    this.el?.remove();
    this.el = null;
  }

  getElement(): HTMLElement | null {
    return this.el;
  }

  private showMessage(message: string, onDismiss?: () => void): void {
    if (this.el) {
      showErrorModal(this.el, message, onDismiss);
    }
  }

  private showError(err: unknown, onDismiss?: () => void): void {
    this.showMessage(err instanceof ApiError ? err.message : "Something went wrong.", onDismiss);
  }

  private render(): void {
    const wrap = document.createElement("div");
    wrap.className = "codex-screen";
    this.el = wrap;
    this.root.appendChild(wrap);

    const windowEl = document.createElement("div");
    windowEl.className = "codex-window join-match-window";
    wrap.appendChild(windowEl);

    const toolbar = document.createElement("div");
    toolbar.className = "codex-toolbar";
    windowEl.appendChild(toolbar);

    const backBtn = document.createElement("button");
    backBtn.textContent = "Back";
    backBtn.addEventListener("click", () => this.callbacks.onBack());
    toolbar.appendChild(backBtn);

    const title = document.createElement("h1");
    title.textContent = "Join a match";
    toolbar.appendChild(title);

    this.refreshBtn = document.createElement("button");
    this.refreshBtn.textContent = "Refresh";
    this.refreshBtn.addEventListener("click", () => void this.refresh());
    toolbar.appendChild(this.refreshBtn);

    const listHeading = document.createElement("h2");
    listHeading.textContent = "Public lobbies";
    windowEl.appendChild(listHeading);

    this.listEl = document.createElement("div");
    this.listEl.className = "public-lobby-list";
    windowEl.appendChild(this.listEl);

    this.joinSelectedBtn = document.createElement("button");
    this.joinSelectedBtn.className = "primary";
    this.joinSelectedBtn.textContent = "Join lobby";
    this.joinSelectedBtn.disabled = true;
    this.joinSelectedBtn.addEventListener("click", () => void this.doJoinSelected());
    windowEl.appendChild(this.joinSelectedBtn);

    const codeHeading = document.createElement("h2");
    codeHeading.textContent = "Join by code";
    windowEl.appendChild(codeHeading);

    const codeRow = document.createElement("div");
    codeRow.className = "row";
    const codeInput = document.createElement("input");
    codeInput.placeholder = "JOIN CODE";
    codeInput.maxLength = 12;
    codeInput.style.flex = "1";
    codeInput.style.textTransform = "uppercase";
    const codeBtn = document.createElement("button");
    codeBtn.className = "primary";
    codeBtn.textContent = "Join with code";
    codeRow.append(codeInput, codeBtn);
    windowEl.appendChild(codeRow);

    const doJoinByCode = async () => {
      const code = codeInput.value.trim().toUpperCase();
      if (!code) {
        this.showMessage("Enter a join code.");
        return;
      }
      codeBtn.disabled = true;
      try {
        const res = await api.joinMatch(code);
        const info = await api.getMatch(res.matchId);
        this.callbacks.onJoined(res.matchId, info.yourTeam, info.playerOneName, info.isPublic);
      } catch (err) {
        this.showError(err);
      } finally {
        codeBtn.disabled = false;
      }
    };
    codeBtn.addEventListener("click", () => void doJoinByCode());
    codeInput.addEventListener("keydown", (e) => {
      if (e.key === "Enter") void doJoinByCode();
    });
  }

  private clearSelection(): void {
    this.selectedLobby = null;
    this.selectedRow?.classList.remove("selected");
    this.selectedRow = null;
    if (this.joinSelectedBtn) this.joinSelectedBtn.disabled = true;
  }

  private async refresh(): Promise<void> {
    if (!this.listEl) return;
    this.clearSelection();
    if (this.refreshBtn) this.refreshBtn.disabled = true;
    this.listEl.replaceChildren(hint("Loading lobbies..."));
    try {
      const res = await api.listPublicLobbies();
      this.renderLobbies(res.lobbies);
    } catch (err) {
      this.showError(err);
      this.listEl.replaceChildren(hint("Could not load lobbies."));
    } finally {
      if (this.refreshBtn) this.refreshBtn.disabled = false;
    }
  }

  private renderLobbies(lobbies: PublicLobbySummary[]): void {
    if (!this.listEl) return;
    if (lobbies.length === 0) {
      this.listEl.replaceChildren(hint("No public lobbies right now."));
      return;
    }
    this.listEl.replaceChildren(...lobbies.map((lobby) => this.renderLobbyRow(lobby)));
  }

  private renderLobbyRow(lobby: PublicLobbySummary): HTMLElement {
    const joinable = lobby.status === "LOBBY" && !lobby.full;

    const row = document.createElement("div");
    row.className = joinable ? "public-lobby-row" : "public-lobby-row full";

    const name = document.createElement("span");
    name.textContent = `${lobby.playerOneName}'s lobby`;
    row.appendChild(name);

    const status = document.createElement("span");
    status.className = "hint";
    status.textContent = lobby.status;
    row.appendChild(status);

    if (joinable) {
      row.addEventListener("click", () => {
        this.selectedRow?.classList.remove("selected");
        this.selectedLobby = lobby;
        this.selectedRow = row;
        row.classList.add("selected");
        if (this.joinSelectedBtn) this.joinSelectedBtn.disabled = false;
      });
    }

    return row;
  }

  private async doJoinSelected(): Promise<void> {
    const lobby = this.selectedLobby;
    if (!lobby || !this.joinSelectedBtn) return;
    this.joinSelectedBtn.disabled = true;
    try {
      const res = await api.joinPublicLobby(lobby.matchId);
      const info = await api.getMatch(res.matchId);
      this.callbacks.onJoined(res.matchId, info.yourTeam, info.playerOneName, info.isPublic);
    } catch (err) {
      // The list can go stale between a refresh and this click (the owner went private,
      // someone else filled the seat, etc.) - re-sync the list once the popup is dismissed
      // rather than leaving a now-wrong row selected and joinable-looking.
      this.showError(err, () => void this.refresh());
      this.joinSelectedBtn.disabled = false;
    }
  }
}

function hint(text: string): HTMLElement {
  const el = document.createElement("div");
  el.className = "hint";
  el.textContent = text;
  return el;
}
