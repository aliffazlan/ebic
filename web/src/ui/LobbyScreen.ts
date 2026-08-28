import { api, ApiError } from "../net/api";
import type { AuthUser, BotLevel, Team, UnitDefinitionSnapshot } from "../types/contract";
import type { Screen } from "./Screen";

const POLL_INTERVAL_MS = 2000;

/** Difficulties offered in the lobby. Adding one here and server-side is the whole change. */
const BOT_LEVELS: ReadonlyArray<{ value: BotLevel; label: string }> = [
  { value: "STANDARD", label: "Standard" },
];

export interface LobbyCallbacks {
  onMatchReady(matchId: string, yourTeam: Team): void;
  onOpenCodex(): void;
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
    const codexBtn = document.createElement("button");
    codexBtn.textContent = "Unit info";
    codexBtn.addEventListener("click", () => this.callbacks.onOpenCodex());
    const logoutBtn = document.createElement("button");
    logoutBtn.textContent = "Log out";
    logoutBtn.addEventListener("click", () => {
      void api.logout().finally(() => this.callbacks.onLogout());
    });
    header.append(title, codexBtn, logoutBtn);
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

    card.appendChild(this.renderFavouritePicker(setError));

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
  }

  /**
   * The hero this account always wants offered. Saved on change rather than behind a
   * button - there is one setting and no way to get it half-right, so a Save step would
   * only be something to forget.
   *
   * The list is the same GET /api/units the codex uses, so what can be favourited and what
   * can be drafted are one question with one answer.
   */
  private renderFavouritePicker(setError: (err: unknown) => void): HTMLElement {
    const section = document.createElement("div");

    const heading = document.createElement("h2");
    heading.textContent = "Favourite unit";
    section.appendChild(heading);

    const select = document.createElement("select");
    select.style.width = "100%";
    select.disabled = true;
    const none = document.createElement("option");
    none.value = "";
    none.textContent = "None";
    select.appendChild(none);
    section.appendChild(select);

    const hint = document.createElement("div");
    hint.className = "hint";
    hint.textContent = "Always offered as one of your two options in the matching draft round. "
      + "If your opponent has picked the same favourite, neither of you is offered them.";
    section.appendChild(hint);

    void api.getUnits()
      .then(({ units }) => {
        select.appendChild(optgroupFor("Champions", units.filter((u) => u.type === "CHAMPION")));
        select.appendChild(optgroupFor("Elites", units.filter((u) => u.type === "ELITE")));
        select.value = this.user.favouriteUnit ?? "";
        select.disabled = false;
      })
      .catch(setError);

    select.addEventListener("change", () => {
      const chosen = select.value === "" ? null : select.value;
      select.disabled = true;
      void api.setFavouriteUnit(chosen)
        .then((res) => {
          this.user = { ...this.user, favouriteUnit: res.favouriteUnit };
        })
        .catch((err) => {
          setError(err);
          select.value = this.user.favouriteUnit ?? "";
        })
        .finally(() => {
          select.disabled = false;
        });
    });

    return section;
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

function optgroupFor(label: string, units: UnitDefinitionSnapshot[]): HTMLOptGroupElement {
  const group = document.createElement("optgroup");
  group.label = label;
  for (const unit of units) {
    const option = document.createElement("option");
    option.value = unit.definitionId;
    option.textContent = unit.name;
    group.appendChild(option);
  }
  return group;
}
