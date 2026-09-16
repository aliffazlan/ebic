import { api, ApiError } from "../net/api";
import { getFastTransitions } from "../app/AppSettings";
import type { AuthUser, BotLevel, Team } from "../types/contract";
import type { Screen } from "./Screen";
import { renderLogo } from "./Logo";
import { slideScreens, type SlideDirection } from "./ScreenTransition";

const POLL_INTERVAL_MS = 2000;

/** Difficulties offered in the lobby. Adding one here and server-side is the whole change. */
const BOT_LEVELS: ReadonlyArray<{ value: BotLevel; label: string }> = [
  { value: "STANDARD", label: "Standard" },
];

export interface LobbyCallbacks {
  onMatchReady(matchId: string, yourTeam: Team): void;
  onOpenCodex(): void;
  onOpenSettings(): void;
  onLogout(): void;
}

export class LobbyScreen implements Screen {
  private el: HTMLElement | null = null;
  private outgoingEl: HTMLElement | null = null;
  private pollHandle: ReturnType<typeof setInterval> | null = null;
  private root: HTMLElement;
  private user: AuthUser;
  private callbacks: LobbyCallbacks;
  private playIntro: boolean;

  constructor(root: HTMLElement, user: AuthUser, callbacks: LobbyCallbacks, playIntro = false) {
    this.root = root;
    this.user = user;
    this.callbacks = callbacks;
    this.playIntro = playIntro;
  }

  mount(): void {
    this.renderMenu();
  }

  unmount(): void {
    this.stopPolling();
    this.outgoingEl?.remove();
    this.outgoingEl = null;
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

  /**
   * Swaps one lobby sub-view for another. These two views belong to the same
   * Screen instance, so they never pass through App.setScreen - this is the
   * local equivalent, using the same shared slide helper and the same Fast
   * Transitions gate.
   */
  private swapView(outgoing: HTMLElement | null, incoming: HTMLElement, direction: SlideDirection): void {
    if (!outgoing) return; // first mount - nothing to slide away from
    if (getFastTransitions()) {
      outgoing.remove();
      return;
    }
    this.outgoingEl = outgoing;
    slideScreens(outgoing, incoming, direction, () => {
      outgoing.remove();
      if (this.outgoingEl === outgoing) this.outgoingEl = null;
    });
  }

  private renderMenu(): void {
    this.stopPolling();
    // Non-null only when coming back from the waiting screen (mount()'s first
    // call has no previous view) - which is exactly when the DOWN slide applies.
    const outgoing = this.el;

    const wrap = document.createElement("div");
    wrap.className = "centered-screen";
    wrap.appendChild(renderLogo(this.playIntro));

    const card = document.createElement("div");
    card.className = this.playIntro ? "card intro-reveal" : "card";
    wrap.appendChild(card);

    const header = document.createElement("div");
    header.className = "row";
    header.style.justifyContent = "center";
    header.style.alignItems = "center";
    const codexBtn = document.createElement("button");
    codexBtn.textContent = "Unit info";
    codexBtn.addEventListener("click", () => this.callbacks.onOpenCodex());
    const settingsBtn = document.createElement("button");
    settingsBtn.textContent = "Settings";
    settingsBtn.addEventListener("click", () => this.callbacks.onOpenSettings());
    const logoutBtn = document.createElement("button");
    logoutBtn.textContent = "Log out";
    logoutBtn.addEventListener("click", () => {
      void api.logout().finally(() => this.callbacks.onLogout());
    });
    header.append(codexBtn, settingsBtn, logoutBtn);
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

    // Play vs the computer - listed first because it is the only option that needs
    // nobody else: no join code to share, no waiting for an opponent to connect.
    const botHeading = document.createElement("h2");
    botHeading.textContent = "Play vs the computer";
    card.appendChild(botHeading);

    const botRow = document.createElement("div");
    botRow.className = "row";

    const botLevelSelect = document.createElement("select");
    botLevelSelect.style.flex = "1";
    // One level today. The control is rendered anyway so that adding a second is a new
    // option element here and a new BotConfig server-side, with no layout to rethink.
    for (const level of BOT_LEVELS) {
      const option = document.createElement("option");
      option.value = level.value;
      option.textContent = level.label;
      botLevelSelect.appendChild(option);
    }

    const botBtn = document.createElement("button");
    botBtn.className = "primary";
    botBtn.textContent = "Play vs Bot";
    botBtn.addEventListener("click", async () => {
      errorText.textContent = "";
      botBtn.disabled = true;
      try {
        const res = await api.createBotMatch(botLevelSelect.value as BotLevel);
        // The opponent is already seated, so this goes straight into the match rather
        // than through the waiting-for-opponent screen the join-code flow needs.
        const info = await api.getMatch(res.matchId);
        this.callbacks.onMatchReady(res.matchId, info.yourTeam);
      } catch (err) {
        setError(err);
        botBtn.disabled = false;
      }
    });

    botRow.append(botLevelSelect, botBtn);
    card.appendChild(botRow);

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
    // Consumed after one render - the waiting-screen's Back button below
    // calls renderMenu() again, and that re-render must never replay the intro.
    this.playIntro = false;
    this.swapView(outgoing, wrap, "down");
  }

  private renderWaitingForOpponent(matchId: string, joinCode: string): void {
    // Only ever reached from the Create Match button inside a rendered menu,
    // so this is always the menu's wrap.
    const outgoing = this.el;

    const wrap = document.createElement("div");
    wrap.className = "centered-screen";
    wrap.appendChild(renderLogo());

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
    this.swapView(outgoing, wrap, "up");

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
