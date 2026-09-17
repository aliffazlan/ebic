import { api } from "../net/api";
import { audioManager } from "../audio/AudioManager";
import { getFastTransitions } from "./AppSettings";
import { AuthScreen } from "../ui/AuthScreen";
import { ChangelogScreen } from "../ui/ChangelogScreen";
import { ClickToContinueScreen } from "../ui/ClickToContinueScreen";
import { CodexScreen } from "../ui/CodexScreen";
import { FixtureScreen } from "../ui/FixtureScreen";
import { HotkeysScreen } from "../ui/HotkeysScreen";
import { LoadingScreen } from "../ui/LoadingScreen";
import { LobbyScreen } from "../ui/LobbyScreen";
import { MatchScreen } from "../ui/MatchScreen";
import { SettingsScreen } from "../ui/SettingsScreen";
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

  private showMatch(matchId: string, yourTeam: Team): void {
    this.setScreen(new MatchScreen(this.root, matchId, yourTeam, () => void this.endMatch()));
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
      this.showMatch(matchId, yourTeam);
      return;
    }
    this.navBusy = true;
    const overlay = createFadeOverlay();
    try {
      await runOverlayFade(overlay, "fade-overlay-in", FADE_MS);
      overlay.style.opacity = "1";
      overlay.classList.remove("fade-overlay-in");
      await delay(HOLD_MS);
      this.showMatch(matchId, yourTeam); // instant swap, hidden behind the black
      await nextFrames(2); // let Pixi/board paint before the overlay clears
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
