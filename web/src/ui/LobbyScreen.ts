import { api, ApiError } from "../net/api";
import type { AuthUser, BotLevel } from "../types/contract";
import type { Screen } from "./Screen";
import { renderLogo } from "./Logo";
import { showErrorModal } from "./ErrorModal";

/** Difficulties offered in the lobby. Adding one here and server-side is the whole change. */
const BOT_LEVELS: ReadonlyArray<{ value: BotLevel; label: string }> = [
  { value: "STANDARD", label: "Standard" },
];

export interface LobbyCallbacks {
  onMatchReady(matchId: string, yourTeam: "PLAYER_ONE" | "PLAYER_TWO"): void;
  onLobbyCreated(matchId: string, joinCode: string, isPublic: boolean): void;
  onOpenJoinMatch(): void;
  onOpenTutorial(): void;
  onOpenCodex(): void;
  onOpenSettings(): void;
  onLogout(): void;
}

export class LobbyScreen implements Screen {
  private el: HTMLElement | null = null;
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
    this.el?.remove();
    this.el = null;
  }

  getElement(): HTMLElement | null {
    return this.el;
  }

  private renderMenu(): void {
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

    const setError = (err: unknown) => {
      showErrorModal(wrap, err instanceof ApiError ? err.message : "Something went wrong.");
    };

    // Tutorial - listed first: the only option that needs no opponent AND explains the
    // rules, so it's where a brand-new player should land before anything else.
    const tutorialHeading = document.createElement("h2");
    tutorialHeading.textContent = "New here?";
    card.appendChild(tutorialHeading);

    const tutorialBtn = document.createElement("button");
    tutorialBtn.className = "primary";
    tutorialBtn.textContent = "Tutorial";
    tutorialBtn.addEventListener("click", () => this.callbacks.onOpenTutorial());
    card.appendChild(tutorialBtn);

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
      botBtn.disabled = true;
      try {
        const res = await api.createBotMatch(botLevelSelect.value as BotLevel);
        // The opponent is already seated, so this goes straight into the match rather
        // than through the lobby room the human create/join flows need.
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
      createBtn.disabled = true;
      try {
        // Every match starts private; visibility can be flipped afterward in the lobby room.
        const res = await api.createMatch(false);
        this.callbacks.onLobbyCreated(res.matchId, res.joinCode, res.isPublic);
      } catch (err) {
        setError(err);
        createBtn.disabled = false;
      }
    });
    card.appendChild(createBtn);

    // Join match - a dedicated screen with a public lobby browser and a join-by-code box.
    const joinHeading = document.createElement("h2");
    joinHeading.textContent = "Join a match";
    card.appendChild(joinHeading);

    const joinBtn = document.createElement("button");
    joinBtn.textContent = "Join match";
    joinBtn.addEventListener("click", () => this.callbacks.onOpenJoinMatch());
    card.appendChild(joinBtn);

    this.root.appendChild(wrap);
    this.el = wrap;
    // Consumed after one render - each LobbyScreen instance is only ever mounted once.
    this.playIntro = false;
  }
}
