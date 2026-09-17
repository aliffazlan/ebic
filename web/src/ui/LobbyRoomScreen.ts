// The waiting room shared by both sides of a human match: the owner sees it
// right after creating a match (with a join code to share), a joiner sees it
// right after using that code or picking the lobby from the public browser.
// Neither side is dropped into the game until the owner presses START GAME -
// joining only seats a player, it no longer starts anything (see MatchService).

import { api, ApiError } from "../net/api";
import { renderLogo } from "./Logo";
import type { Screen } from "./Screen";
import type { Team } from "../types/contract";

const POLL_INTERVAL_MS = 2000;

export interface LobbyRoomCallbacks {
  onBack(): void;
  onMatchReady(matchId: string, yourTeam: Team): void;
}

export class LobbyRoomScreen implements Screen {
  private el: HTMLElement | null = null;
  private root: HTMLElement;
  private matchId: string;
  private yourTeam: Team;
  /** Only the owner has one to display - a joiner already knows it or came from the browser. */
  private joinCode: string | null;
  private isPublic: boolean;
  private callbacks: LobbyRoomCallbacks;
  private pollHandle: ReturnType<typeof setInterval> | null = null;

  private playerTwoRow: HTMLElement | null = null;
  private startBtn: HTMLButtonElement | null = null;
  private errorText: HTMLElement | null = null;

  constructor(
    root: HTMLElement,
    matchId: string,
    yourTeam: Team,
    joinCode: string | null,
    isPublic: boolean,
    callbacks: LobbyRoomCallbacks,
  ) {
    this.root = root;
    this.matchId = matchId;
    this.yourTeam = yourTeam;
    this.joinCode = joinCode;
    this.isPublic = isPublic;
    this.callbacks = callbacks;
  }

  mount(): void {
    this.render();
    this.startPolling();
  }

  unmount(): void {
    this.stopPolling();
    this.el?.remove();
    this.el = null;
  }

  getElement(): HTMLElement | null {
    return this.el;
  }

  private stopPolling(): void {
    if (this.pollHandle !== null) {
      clearInterval(this.pollHandle);
      this.pollHandle = null;
    }
  }

  private render(): void {
    const isOwner = this.yourTeam === "PLAYER_ONE";

    const wrap = document.createElement("div");
    wrap.className = "centered-screen";
    wrap.appendChild(renderLogo());

    const card = document.createElement("div");
    card.className = "card";
    wrap.appendChild(card);

    const title = document.createElement("h1");
    title.textContent = "Match lobby";
    card.appendChild(title);

    const badge = document.createElement("div");
    badge.className = "hint";
    badge.textContent = this.isPublic ? "Visibility: PUBLIC" : "Visibility: PRIVATE";
    card.appendChild(badge);

    if (this.joinCode) {
      const hint = document.createElement("div");
      hint.className = "hint";
      hint.textContent = "Share this join code with your opponent:";
      card.appendChild(hint);

      const codeEl = document.createElement("div");
      codeEl.className = "join-code";
      codeEl.textContent = this.joinCode;
      card.appendChild(codeEl);
    }

    const playersHeading = document.createElement("h2");
    playersHeading.textContent = "Players";
    card.appendChild(playersHeading);

    const playerOneRow = document.createElement("div");
    playerOneRow.className = "hint";
    playerOneRow.textContent = isOwner ? "You (host)" : "Host";
    card.appendChild(playerOneRow);

    this.playerTwoRow = document.createElement("div");
    this.playerTwoRow.className = "hint";
    this.playerTwoRow.textContent = isOwner ? "Waiting for opponent to join..." : "You";
    card.appendChild(this.playerTwoRow);

    this.errorText = document.createElement("div");
    this.errorText.className = "error-text";
    card.appendChild(this.errorText);

    if (isOwner) {
      this.startBtn = document.createElement("button");
      this.startBtn.className = "primary";
      this.startBtn.textContent = "Start game";
      this.startBtn.disabled = true;
      this.startBtn.addEventListener("click", () => void this.doStart());
      card.appendChild(this.startBtn);
    } else {
      const waitingHint = document.createElement("div");
      waitingHint.className = "hint";
      waitingHint.textContent = "Waiting for the host to start the game...";
      card.appendChild(waitingHint);
    }

    const backBtn = document.createElement("button");
    backBtn.textContent = "Back";
    backBtn.addEventListener("click", () => {
      this.stopPolling();
      // Best-effort - harmless no-op if the game has already started by the time this lands.
      api.leaveLobby(this.matchId).catch(() => { });
      this.callbacks.onBack();
    });
    card.appendChild(backBtn);

    this.root.appendChild(wrap);
    this.el = wrap;
  }

  private async doStart(): Promise<void> {
    if (this.errorText) this.errorText.textContent = "";
    if (this.startBtn) this.startBtn.disabled = true;
    try {
      await api.startLobby(this.matchId);
      this.stopPolling();
      this.callbacks.onMatchReady(this.matchId, this.yourTeam);
    } catch (err) {
      if (this.errorText) {
        this.errorText.textContent = err instanceof ApiError ? err.message : "Something went wrong.";
      }
      if (this.startBtn) this.startBtn.disabled = false;
    }
  }

  private startPolling(): void {
    this.pollHandle = setInterval(async () => {
      try {
        const info = await api.getMatch(this.matchId);
        if (info.status !== "LOBBY") {
          this.stopPolling();
          this.callbacks.onMatchReady(this.matchId, this.yourTeam);
          return;
        }
        const isOwner = this.yourTeam === "PLAYER_ONE";
        if (isOwner && this.playerTwoRow) {
          this.playerTwoRow.textContent = info.playerTwoName
            ? `${info.playerTwoName} has joined`
            : "Waiting for opponent to join...";
        }
        if (this.startBtn) {
          this.startBtn.disabled = !info.playerTwoName;
        }
      } catch {
        // transient network hiccup - keep polling
      }
    }, POLL_INTERVAL_MS);
  }
}
