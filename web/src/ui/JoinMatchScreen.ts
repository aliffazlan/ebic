// Dedicated top-level screen for finding a match to join: a manually-refreshed
// browser of public lobbies, plus a join-by-code box that works for both
// public and private lobbies (a public lobby's code stays valid even after
// it's listed - this box doesn't special-case how the code was found).

import { api, ApiError } from "../net/api";
import type { Screen } from "./Screen";
import type { PublicLobbySummary, Team } from "../types/contract";

export interface JoinMatchCallbacks {
  onBack(): void;
  onJoined(matchId: string, yourTeam: Team): void;
}

export class JoinMatchScreen implements Screen {
  private el: HTMLElement | null = null;
  private root: HTMLElement;
  private callbacks: JoinMatchCallbacks;
  private listEl: HTMLElement | null = null;
  private errorText: HTMLElement | null = null;
  private refreshBtn: HTMLButtonElement | null = null;

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

  private setError(err: unknown): void {
    if (this.errorText) {
      this.errorText.textContent = err instanceof ApiError ? err.message : "Something went wrong.";
    }
  }

  private render(): void {
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
    backBtn.addEventListener("click", () => this.callbacks.onBack());
    toolbar.appendChild(backBtn);

    const title = document.createElement("h1");
    title.textContent = "Join a match";
    toolbar.appendChild(title);

    this.refreshBtn = document.createElement("button");
    this.refreshBtn.textContent = "Refresh";
    this.refreshBtn.addEventListener("click", () => void this.refresh());
    toolbar.appendChild(this.refreshBtn);

    this.errorText = document.createElement("div");
    this.errorText.className = "error-text";
    windowEl.appendChild(this.errorText);

    const listHeading = document.createElement("h2");
    listHeading.textContent = "Public lobbies";
    windowEl.appendChild(listHeading);

    this.listEl = document.createElement("div");
    this.listEl.className = "public-lobby-list";
    windowEl.appendChild(this.listEl);

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
    codeBtn.textContent = "Join";
    codeRow.append(codeInput, codeBtn);
    windowEl.appendChild(codeRow);

    const doJoinByCode = async () => {
      const code = codeInput.value.trim().toUpperCase();
      if (!code) {
        if (this.errorText) this.errorText.textContent = "Enter a join code.";
        return;
      }
      if (this.errorText) this.errorText.textContent = "";
      codeBtn.disabled = true;
      try {
        await this.doJoin(code);
      } catch (err) {
        this.setError(err);
      } finally {
        codeBtn.disabled = false;
      }
    };
    codeBtn.addEventListener("click", () => void doJoinByCode());
    codeInput.addEventListener("keydown", (e) => {
      if (e.key === "Enter") void doJoinByCode();
    });
  }

  private async refresh(): Promise<void> {
    if (!this.listEl) return;
    if (this.refreshBtn) this.refreshBtn.disabled = true;
    this.listEl.replaceChildren(hint("Loading lobbies..."));
    try {
      const res = await api.listPublicLobbies();
      this.renderLobbies(res.lobbies);
    } catch (err) {
      this.setError(err);
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
    const joinable = lobby.status === "LOBBY";

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
        if (this.errorText) this.errorText.textContent = "";
        void this.doJoin(lobby.joinCode).catch((err) => this.setError(err));
      });
    }

    return row;
  }

  private async doJoin(code: string): Promise<void> {
    const res = await api.joinMatch(code);
    const info = await api.getMatch(res.matchId);
    this.callbacks.onJoined(res.matchId, info.yourTeam);
  }
}

function hint(text: string): HTMLElement {
  const el = document.createElement("div");
  el.className = "hint";
  el.textContent = text;
  return el;
}
