import { api } from "../net/api";
import { audioManager } from "../audio/AudioManager";
import { getFastTransitions } from "./AppSettings";
import { AuthScreen } from "../ui/AuthScreen";
import { ChangelogScreen } from "../ui/ChangelogScreen";
import { ClickToContinueScreen } from "../ui/ClickToContinueScreen";
import { CodexScreen } from "../ui/CodexScreen";
import { FixtureScreen } from "../ui/FixtureScreen";
import { HotkeysScreen } from "../ui/HotkeysScreen";
import { JoinMatchScreen } from "../ui/JoinMatchScreen";
import { LoadingScreen } from "../ui/LoadingScreen";
import { LobbyRoomScreen } from "../ui/LobbyRoomScreen";
import { LobbyScreen } from "../ui/LobbyScreen";
import { MatchScreen } from "../ui/MatchScreen";
import { SettingsScreen } from "../ui/SettingsScreen";
import { TutorialScreen } from "../ui/TutorialScreen";
import {
  createFadeOverlay,
  delay,
  nextFrames,
  runOverlayFade,
  slideScreens,
  FADE_MS,
  HOLD_MS,
  REVEAL_MS,
  type SlideDirection,
} from "../ui/ScreenTransition";
import type { Screen } from "../ui/Screen";
import type { AuthUser, Team } from "../types/contract";

/** Top-level screen router: loading -> click-to-continue -> auth -> lobby -> match. */
export class App {
  private currentUser: AuthUser | null = null;
  private screen: Screen | null = null;
  private root: HTMLElement;
  // Guards the fade sequences (startMatch/endMatch, each ~5s). MatchScreen's
  // unmount() is not idempotent, so a re-entrant call must be refused outright
  // rather than queued.
  private navBusy = false;

  constructor(root: HTMLElement) {
    this.root = root;
  }

  async start(): Promise<void> {
    // Dev-only escape hatch: `?fixture=1` renders the board/HUD against a
    // hand-crafted local match snapshot, `?fixture=placement` against a
    // local placement-editor snapshot instead - no backend required either
    // way. See FixtureScreen.
    const fixtureParam = new URLSearchParams(location.search).get("fixture");
    if (fixtureParam === "1") {
      this.setScreen(new FixtureScreen(this.root, "match"));
      return;
    }
    if (fixtureParam === "placement") {
      this.setScreen(new FixtureScreen(this.root, "placement"));
      return;
    }

    this.setScreen(new LoadingScreen(this.root, () => this.showClickToContinue()));
  }

  private showClickToContinue(): void {
    this.setScreen(
      new ClickToContinueScreen(this.root, () => {
        // A real user gesture - the earliest point audio is allowed to
        // autoplay, and exactly where the design wants the music to start.
        audioManager.playMusic();
        void this.enterApp(true);
      }),
    );
  }

  // playIntro is true on two paths: the very first menu shown after
  // click-to-continue, and returning to the lobby after a match (endMatch)
  // - both play the logo/menu reveal. Every other entry to Auth or Lobby
  // (login, logout, returning from the codex or settings) is instant or slid.
  private async enterApp(playIntro: boolean): Promise<void> {
    try {
      this.currentUser = await api.me();
      this.showLobby(playIntro);
    } catch {
      this.showAuth(playIntro);
    }
  }

  private setScreen(screen: Screen, direction: SlideDirection | null = null): void {
    const previous = this.screen;
    this.screen = screen;

    const previousEl = previous?.getElement?.() ?? null;
    if (!direction || !previous || !previousEl || getFastTransitions()) {
      previous?.unmount();
      screen.mount();
      return;
    }

    // Animated hand-off: mount the new screen while the old one is still
    // showing, then slide them past each other. The outgoing screen isn't
    // unmounted until its own exit animation finishes - slideScreens
    // guarantees that callback runs exactly once.
    screen.mount();
    const enteringEl = screen.getElement?.() ?? null;
    if (!enteringEl) {
      previous.unmount();
      return;
    }

    slideScreens(previousEl, enteringEl, direction, () => previous.unmount());
  }

  private showAuth(playIntro = false, direction: SlideDirection | null = null): void {
    this.setScreen(
      new AuthScreen(
        this.root,
        (user) => {
          this.currentUser = user;
          this.showLobby(false, "up"); // slide from the login card into the lobby
        },
        playIntro,
      ),
      direction,
    );
  }

  private showLobby(playIntro = false, direction: SlideDirection | null = null): void {
    this.setScreen(
      new LobbyScreen(
        this.root,
        this.currentUser!,
        {
          onMatchReady: (matchId, yourTeam) => void this.startMatch(matchId, yourTeam),
          onLobbyCreated: (matchId, joinCode, isPublic) =>
            this.showLobbyRoom(matchId, "PLAYER_ONE", joinCode, this.currentUser!.username, isPublic),
          onOpenJoinMatch: () => this.showJoinMatch(),
          onOpenTutorial: () => void this.startTutorial(),
          onOpenCodex: () => this.showCodex(),
          onOpenSettings: () => this.showSettings(),
          onLogout: () => {
            this.currentUser = null;
            this.showAuth(false, "down");
          },
        },
        playIntro,
      ),
      direction,
    );
  }

  /**
   * Join Match: same upward slide as creating a match, since both lead into
   * the same kind of "about to play" moment.
   */
  private showJoinMatch(): void {
    this.setScreen(
      new JoinMatchScreen(this.root, {
        onBack: () => this.showLobby(false, "down"),
        onJoined: (matchId, yourTeam, playerOneName, isPublic) =>
          this.showLobbyRoom(matchId, yourTeam, null, playerOneName, isPublic),
        onSpectate: (matchId, playerOneName, playerTwoName, joinCode) =>
          void this.startSpectate(matchId, playerOneName, playerTwoName, joinCode),
        // Already DRAFTING/IN_PROGRESS - skip the lobby room and go straight back into the
        // match, same entry point a fresh join/create eventually lands on.
        onRejoin: (matchId, yourTeam) => void this.startMatch(matchId, yourTeam),
      }),
      "up",
    );
  }

  /**
   * The shared waiting room for a human match, reached either by creating one
   * (owner, has a join code to show) or by joining one (no code to show, and
   * no START GAME button - only the owner has one). Neither path drops the
   * player into the game until the owner explicitly starts it.
   */
  private showLobbyRoom(
    matchId: string,
    yourTeam: Team,
    joinCode: string | null,
    playerOneName: string,
    isPublic: boolean,
  ): void {
    this.setScreen(
      new LobbyRoomScreen(this.root, matchId, yourTeam, joinCode, playerOneName, isPublic, {
        onBack: () => this.showLobby(false, "down"),
        onMatchReady: (id, team) => void this.startMatch(id, team),
      }),
      "up",
    );
  }

  private showCodex(): void {
    this.setScreen(new CodexScreen(this.root, () => this.showLobby(false, "left")), "right");
  }

  /**
   * Settings is a screen of its own rather than a popup, so it can be
   * navigated to and from like any other menu. The favourite-unit change has
   * to land on App's copy of the account: going back builds a fresh
   * LobbyScreen from it.
   */
  private showSettings(direction: SlideDirection | null = "left"): void {
    this.setScreen(
      new SettingsScreen(this.root, this.currentUser?.favouriteUnit ?? null, {
        onBack: () => this.showLobby(false, "right"),
        onFavouriteUnitChange: (favouriteUnit) => {
          if (this.currentUser) this.currentUser = { ...this.currentUser, favouriteUnit };
        },
        onOpenHotkeys: () => this.showHotkeys(),
        onOpenChangelog: () => this.showChangelog(),
      }),
      direction,
    );
  }

  private showHotkeys(): void {
    this.setScreen(new HotkeysScreen(this.root, () => this.showSettings("down")), "up");
  }

  private showChangelog(): void {
    this.setScreen(new ChangelogScreen(this.root, () => this.showSettings("right")), "left");
  }

  private showTutorial(): void {
    this.setScreen(new TutorialScreen(this.root, this.currentUser?.username ?? "You", () => void this.endMatch()));
  }

  /**
   * Same fade-to-black choreography as startMatch, since this is the same kind of
   * "about to play" moment - just with no matchId/team to fetch first, and no backend
   * involved at all (see TutorialScreen).
   */
  private async startTutorial(): Promise<void> {
    if (this.navBusy) return;
    if (getFastTransitions()) {
      this.showTutorial();
      return;
    }
    this.navBusy = true;
    const overlay = createFadeOverlay();
    try {
      await runOverlayFade(overlay, "fade-overlay-in", FADE_MS);
      overlay.style.opacity = "1";
      overlay.classList.remove("fade-overlay-in");
      await delay(HOLD_MS);
      this.showTutorial(); // instant swap, hidden behind the black
      await nextFrames(2); // let Pixi/board paint before the overlay clears
      await runOverlayFade(overlay, "fade-overlay-out", FADE_MS);
    } finally {
      overlay.remove();
      this.navBusy = false;
    }
  }

  private async showMatch(matchId: string, yourTeam: Team): Promise<void> {
    // Fetched here (rather than threaded through every onMatchReady call site in
    // LobbyScreen/LobbyRoomScreen) so both player names reach the combat log for
    // every match, not just ones a caller happened to already have both names for.
    const info = await api.getMatch(matchId);
    this.setScreen(
      new MatchScreen(this.root, matchId, yourTeam, info.playerOneName, info.playerTwoName ?? "", () =>
        void this.endMatch(),
      ),
    );
  }

  /** A read-only viewer's entry point - no lobby room, no join code to show, names already
   * known from the spectate HTTP response so no extra fetch is needed. */
  private showSpectate(matchId: string, playerOneName: string, playerTwoName: string, joinCode?: string): void {
    this.setScreen(
      new MatchScreen(
        this.root,
        matchId,
        null,
        playerOneName,
        playerTwoName,
        () => void this.endMatch(),
        joinCode,
      ),
    );
  }

  /**
   * Game start: everything fades to black over 2s, holds for 1s, then fades
   * into the match over 2s. The overlay is inserted synchronously and eats
   * pointer events, so the lobby is locked from the first frame of the fade -
   * no second "Play vs Bot" click, no Back mid-sequence.
   */
  private async startMatch(matchId: string, yourTeam: Team): Promise<void> {
    if (this.navBusy) return;
    if (getFastTransitions()) {
      await this.showMatch(matchId, yourTeam);
      return;
    }
    this.navBusy = true;
    const overlay = createFadeOverlay();
    try {
      await runOverlayFade(overlay, "fade-overlay-in", FADE_MS);
      overlay.style.opacity = "1";
      overlay.classList.remove("fade-overlay-in");
      await delay(HOLD_MS);
      await this.showMatch(matchId, yourTeam); // instant swap, hidden behind the black
      await nextFrames(2); // let Pixi/board paint before the overlay clears
      await runOverlayFade(overlay, "fade-overlay-out", FADE_MS);
    } finally {
      overlay.remove();
      this.navBusy = false;
    }
  }

  /** Same fade choreography as startMatch, landing on the read-only spectate screen instead. */
  private async startSpectate(
    matchId: string,
    playerOneName: string,
    playerTwoName: string,
    joinCode?: string,
  ): Promise<void> {
    if (this.navBusy) return;
    if (getFastTransitions()) {
      this.showSpectate(matchId, playerOneName, playerTwoName, joinCode);
      return;
    }
    this.navBusy = true;
    const overlay = createFadeOverlay();
    try {
      await runOverlayFade(overlay, "fade-overlay-in", FADE_MS);
      overlay.style.opacity = "1";
      overlay.classList.remove("fade-overlay-in");
      await delay(HOLD_MS);
      this.showSpectate(matchId, playerOneName, playerTwoName, joinCode);
      await nextFrames(2);
      await runOverlayFade(overlay, "fade-overlay-out", FADE_MS);
    } finally {
      overlay.remove();
      this.navBusy = false;
    }
  }

  /**
   * Match end: fade to black over 2s, hold 1s, then hand over to the lobby's
   * own boot intro (logo reveal, then the menu) - the same reveal the very
   * first menu after click-to-continue plays.
   */
  private async endMatch(): Promise<void> {
    if (this.navBusy) return;
    if (getFastTransitions()) {
      this.showLobby();
      return;
    }
    this.navBusy = true;
    const overlay = createFadeOverlay();
    try {
      await runOverlayFade(overlay, "fade-overlay-in", FADE_MS);
      overlay.style.opacity = "1";
      overlay.classList.remove("fade-overlay-in");
      await delay(HOLD_MS);
      this.showLobby(true); // unmounts MatchScreen exactly once
      await nextFrames(2);
      await runOverlayFade(overlay, "fade-overlay-out-quick", REVEAL_MS);
    } finally {
      overlay.remove();
      this.navBusy = false;
    }
  }
}
