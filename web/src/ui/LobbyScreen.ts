import { api, ApiError } from "../net/api";
import type { AuthUser, Team } from "../types/contract";
import type { Screen } from "./Screen";

const POLL_INTERVAL_MS = 2000;

export interface LobbyCallbacks {
  onMatchReady(matchId: string, yourTeam: Team): void;
  onLogout(): void;
}

export class LobbyScreen implements Screen {
  private el: HTMLElement | null = null;
  private pollHandle: ReturnType<typeof setInterval> | null = null;
  private root: HTMLElement;
  private user: AuthUser;
  private callbacks: LobbyCallbacks;

  constructor(root: HTMLElement, user: AuthUser, callbacks: LobbyCallbacks) {
    this.root = root;
    this.user = user;
    this.callbacks = callbacks;
  }

  mount(): void {
    this.renderMenu();
  }

  unmount(): void {
    this.stopPolling();
    this.el?.remove();
    this.el = null;
  }

  private stopPolling(): void {
    if (this.pollHandle !== null) {
      clearInterval(this.pollHandle);
      this.pollHandle = null;
    }
  }

  private renderMenu(): void {
    this.stopPolling();
    this.el?.remove();

    const wrap = document.createElement("div");
    wrap.className = "centered-screen";

    const card = document.createElement("div");
    card.className = "card";
    wrap.appendChild(card);

    const header = document.createElement("div");
    header.className = "row";
    header.style.justifyContent = "space-between";
    header.style.alignItems = "center";
    const title = document.createElement("h1");
    title.textContent = "EBIC";
    const logoutBtn = document.createElement("button");
    logoutBtn.textContent = "Log out";
    logoutBtn.addEventListener("click", () => {
      void api.logout().finally(() => this.callbacks.onLogout());
    });
    header.append(title, logoutBtn);
    card.appendChild(header);

    const welcome = document.createElement("div");
    welcome.className = "hint";
    welcome.textContent = `Signed in as ${this.user.username}`;
    card.appendChild(welcome);

    const errorText = document.createElement("div");
    errorText.className = "error-text";
    card.appendChild(errorText);

    const setError = (err: unknown) => {
      errorText.textContent = err instanceof ApiError ? err.message : "Something went wrong.";
    };

    // Create match
    const createHeading = document.createElement("h2");
    createHeading.textContent = "Create a match";
    card.appendChild(createHeading);

    const createBtn = document.createElement("button");
    createBtn.className = "primary";
    createBtn.textContent = "Create match";
    createBtn.addEventListener("click", async () => {
      errorText.textContent = "";
      createBtn.disabled = true;
      try {
        const res = await api.createMatch();
        this.renderWaitingForOpponent(res.matchId, res.joinCode);
      } catch (err) {
        setError(err);
        createBtn.disabled = false;
      }
    });
    card.appendChild(createBtn);

    // Join match
    const joinHeading = document.createElement("h2");
    joinHeading.textContent = "Join a match";
    card.appendChild(joinHeading);

    const joinRow = document.createElement("div");
    joinRow.className = "row";
    const joinInput = document.createElement("input");
    joinInput.placeholder = "JOIN CODE";
    joinInput.maxLength = 12;
    joinInput.style.flex = "1";
    joinInput.style.textTransform = "uppercase";
    const joinBtn = document.createElement("button");
    joinBtn.textContent = "Join";
    joinRow.append(joinInput, joinBtn);
    card.appendChild(joinRow);

    const doJoin = async () => {
      const code = joinInput.value.trim().toUpperCase();
      if (!code) {
        errorText.textContent = "Enter a join code.";
        return;
      }
      errorText.textContent = "";
      joinBtn.disabled = true;
      try {
        const res = await api.joinMatch(code);
        const info = await api.getMatch(res.matchId);
        this.callbacks.onMatchReady(res.matchId, info.yourTeam);
      } catch (err) {
        setError(err);
        joinBtn.disabled = false;
      }
    };
    joinBtn.addEventListener("click", () => void doJoin());
    joinInput.addEventListener("keydown", (e) => {
      if (e.key === "Enter") void doJoin();
    });

    this.root.appendChild(wrap);
    this.el = wrap;
  }

  private renderWaitingForOpponent(matchId: string, joinCode: string): void {
    this.el?.remove();

    const wrap = document.createElement("div");
    wrap.className = "centered-screen";

    const card = document.createElement("div");
    card.className = "card";
    wrap.appendChild(card);

    const title = document.createElement("h1");
    title.textContent = "Match created";
    card.appendChild(title);

    const hint = document.createElement("div");
    hint.className = "hint";
    hint.textContent = "Share this join code with your opponent:";
    card.appendChild(hint);

    const codeEl = document.createElement("div");
    codeEl.className = "join-code";
    codeEl.textContent = joinCode;
    card.appendChild(codeEl);

    const status = document.createElement("div");
    status.className = "hint";
    status.textContent = "Waiting for opponent to join...";
    card.appendChild(status);

    const cancelBtn = document.createElement("button");
    cancelBtn.textContent = "Back";
    cancelBtn.addEventListener("click", () => this.renderMenu());
    card.appendChild(cancelBtn);

    this.root.appendChild(wrap);
    this.el = wrap;

    this.pollHandle = setInterval(async () => {
      try {
        const info = await api.getMatch(matchId);
        if (info.status !== "WAITING") {
          this.stopPolling();
          this.callbacks.onMatchReady(matchId, info.yourTeam);
        }
      } catch {
        // transient network hiccup - keep polling
      }
    }, POLL_INTERVAL_MS);
  }
}
