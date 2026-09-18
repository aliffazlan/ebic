// Dedicated top-level screen for finding a match to join: a manually-refreshed
// browser of public lobbies (select a row, then press JOIN LOBBY), plus a
// join-by-code box that works for both public and private lobbies (a public
// lobby's code stays valid even after it's listed - this box doesn't
// special-case how the code was found).

import { api, ApiError } from "../net/api";
import { showErrorModal } from "./ErrorModal";
import type { Screen } from "./Screen";
import type { PublicLobbySummary, RejoinableMatchSummary, Team } from "../types/contract";

export interface JoinMatchCallbacks {
  onBack(): void;
  onJoined(matchId: string, yourTeam: Team, playerOneName: string, isPublic: boolean): void;
  /** joinCode is only set for a private match spectated by code - the WS connection needs
   * it to prove eligibility, since a spectator is never a seated participant. */
  onSpectate(matchId: string, playerOneName: string, playerTwoName: string, joinCode?: string): void;
  /** Reconnecting to a match already in DRAFTING/IN_PROGRESS - skips the lobby room
   * entirely, same as resuming a match that never actually ended. */
  onRejoin(matchId: string, yourTeam: Team): void;
}

export class JoinMatchScreen implements Screen {
  private el: HTMLElement | null = null;
  private root: HTMLElement;
  private callbacks: JoinMatchCallbacks;
  private listEl: HTMLElement | null = null;
  private refreshBtn: HTMLButtonElement | null = null;
  private joinSelectedBtn: HTMLButtonElement | null = null;
  private rejoinBtn: HTMLButtonElement | null = null;
  private rejoinListEl: HTMLElement | null = null;

  private selectedLobby: PublicLobbySummary | null = null;
  private selectedRow: HTMLElement | null = null;
  private rejoinable: RejoinableMatchSummary[] = [];

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
        // A code for a match that's already running can't be joined as a player, but it can
        // still be spectated - the same box handles both rather than making the user guess
        // which one applies.
        if (err instanceof ApiError && err.message === "this match has already started") {
          try {
            const info = await api.spectateByCode(code);
            this.callbacks.onSpectate(info.matchId, info.playerOneName, info.playerTwoName, code);
            return;
          } catch (spectateErr) {
            this.showError(spectateErr);
            return;
          }
        }
        this.showError(err);
      } finally {
        codeBtn.disabled = false;
      }
    };
    codeBtn.addEventListener("click", () => void doJoinByCode());
    codeInput.addEventListener("keydown", (e) => {
      if (e.key === "Enter") void doJoinByCode();
    });

    const rejoinHeading = document.createElement("h2");
    rejoinHeading.textContent = "Rejoin match";
    windowEl.appendChild(rejoinHeading);

    this.rejoinBtn = document.createElement("button");
    this.rejoinBtn.className = "primary";
    this.rejoinBtn.textContent = "Rejoin match";
    this.rejoinBtn.disabled = true;
    this.rejoinBtn.title = "No matches to rejoin.";
    this.rejoinBtn.addEventListener("click", () => this.onRejoinClicked());
    windowEl.appendChild(this.rejoinBtn);

    this.rejoinListEl = document.createElement("div");
    this.rejoinListEl.className = "public-lobby-list";
    this.rejoinListEl.style.display = "none";
    windowEl.appendChild(this.rejoinListEl);
  }

  private clearSelection(): void {
    this.selectedLobby = null;
    this.selectedRow?.classList.remove("selected");
    this.selectedRow = null;
    if (this.joinSelectedBtn) {
      this.joinSelectedBtn.disabled = true;
      this.joinSelectedBtn.textContent = "Join lobby";
    }
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
    // Best-effort, separate from the public lobby fetch above so one failing doesn't
    // block the other - a broken rejoin check shouldn't stop the browser from loading.
    try {
      const res = await api.getRejoinableMatches();
      this.rejoinable = res.matches;
    } catch {
      this.rejoinable = [];
    }
    this.renderRejoinButton();
  }

  /**
   * Reflects the current rejoinable list on the button: disabled with a reason when there's
   * nothing to rejoin (none at all, or the only candidate(s) are already connected elsewhere -
   * see RejoinableMatchSummary.connected), otherwise enabled and ready for onRejoinClicked.
   */
  private renderRejoinButton(): void {
    if (!this.rejoinBtn || !this.rejoinListEl) return;
    this.rejoinListEl.replaceChildren();
    this.rejoinListEl.style.display = "none";

    const available = this.rejoinable.filter((m) => !m.connected);

    if (this.rejoinable.length === 0) {
      this.rejoinBtn.disabled = true;
      this.rejoinBtn.title = "No matches to rejoin.";
      this.rejoinBtn.textContent = "Rejoin match";
      return;
    }
    if (available.length === 0) {
      this.rejoinBtn.disabled = true;
      this.rejoinBtn.title = "You're already connected to that match in another tab.";
      this.rejoinBtn.textContent = "Rejoin match";
      return;
    }

    this.rejoinBtn.disabled = false;
    this.rejoinBtn.title = "";
    this.rejoinBtn.textContent = available.length === 1 ? `Rejoin vs ${available[0].opponentName}` : "Rejoin match";
  }

  /** A single candidate rejoins immediately; more than one opens a small picker instead of
   * guessing which match the player meant. */
  private onRejoinClicked(): void {
    const available = this.rejoinable.filter((m) => !m.connected);
    if (available.length === 0) return;
    if (available.length === 1) {
      void this.confirmAndRejoin(available[0].matchId, available[0].team);
      return;
    }
    if (!this.rejoinListEl) return;
    const isOpen = this.rejoinListEl.style.display !== "none";
    if (isOpen) {
      this.rejoinListEl.style.display = "none";
      return;
    }
    this.rejoinListEl.replaceChildren(...available.map((m) => this.renderRejoinRow(m)));
    this.rejoinListEl.style.display = "";
  }

  private renderRejoinRow(match: RejoinableMatchSummary): HTMLElement {
    const row = document.createElement("div");
    row.className = "public-lobby-row";

    const name = document.createElement("span");
    name.textContent = `vs ${match.opponentName}`;
    row.appendChild(name);

    const status = document.createElement("span");
    status.className = "hint";
    status.textContent = match.status;
    row.appendChild(status);

    row.addEventListener("click", () => void this.confirmAndRejoin(match.matchId, match.team));
    return row;
  }

  /**
   * The rejoinable list can go stale just by sitting on this screen (the match gets
   * abandoned and destroyed - GameSession.abandonIfStillEmpty - while the user hasn't
   * clicked yet). Re-checks against a fresh fetch right before actually connecting, rather
   * than trusting whatever this.rejoinable last held, so a since-destroyed match surfaces as
   * a clear error instead of the socket connecting into a dead match and hanging.
   */
  private async confirmAndRejoin(matchId: string, team: Team): Promise<void> {
    if (this.rejoinBtn) this.rejoinBtn.disabled = true;
    try {
      const res = await api.getRejoinableMatches();
      const stillValid = res.matches.some((m) => m.matchId === matchId && !m.connected);
      if (!stillValid) {
        this.showMessage("That match is no longer available to rejoin.", () => void this.refresh());
        return;
      }
      this.callbacks.onRejoin(matchId, team);
    } catch (err) {
      this.showError(err, () => void this.refresh());
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
    const spectatable = lobby.status === "IN PROGRESS";
    const selectable = joinable || spectatable;

    const row = document.createElement("div");
    row.className = "public-lobby-row";
    if (!selectable) row.classList.add("full");
    if (spectatable) row.classList.add("spectatable");

    const name = document.createElement("span");
    name.textContent = `${lobby.playerOneName}'s lobby`;
    row.appendChild(name);

    const status = document.createElement("span");
    status.className = "hint";
    status.textContent = lobby.status;
    row.appendChild(status);

    if (selectable) {
      row.addEventListener("click", () => {
        this.selectedRow?.classList.remove("selected");
        this.selectedLobby = lobby;
        this.selectedRow = row;
        row.classList.add("selected");
        if (this.joinSelectedBtn) {
          this.joinSelectedBtn.disabled = false;
          this.joinSelectedBtn.textContent = spectatable ? "Spectate" : "Join lobby";
        }
      });
    }

    return row;
  }

  private async doJoinSelected(): Promise<void> {
    const lobby = this.selectedLobby;
    if (!lobby || !this.joinSelectedBtn) return;
    if (lobby.status === "IN PROGRESS") {
      return this.doSpectateSelected();
    }
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

  private async doSpectateSelected(): Promise<void> {
    const lobby = this.selectedLobby;
    if (!lobby || !this.joinSelectedBtn) return;
    this.joinSelectedBtn.disabled = true;
    try {
      const info = await api.spectatePublicLobby(lobby.matchId);
      this.callbacks.onSpectate(info.matchId, info.playerOneName, info.playerTwoName);
    } catch (err) {
      // Same staleness story as doJoinSelected - the match could have finished, or gone
      // private, between the last refresh and this click.
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
