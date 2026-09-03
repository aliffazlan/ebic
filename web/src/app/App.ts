import { api } from "../net/api";
import { audioManager } from "../audio/AudioManager";
import { AuthScreen } from "../ui/AuthScreen";
import { ClickToContinueScreen } from "../ui/ClickToContinueScreen";
import { CodexScreen } from "../ui/CodexScreen";
import { FixtureScreen } from "../ui/FixtureScreen";
import { LoadingScreen } from "../ui/LoadingScreen";
import { LobbyScreen } from "../ui/LobbyScreen";
import { MatchScreen } from "../ui/MatchScreen";
import type { Screen } from "../ui/Screen";
import type { AuthUser, Team } from "../types/contract";

/** Top-level screen router: loading -> click-to-continue -> auth -> lobby -> match. */
export class App {
  private currentUser: AuthUser | null = null;
  private screen: Screen | null = null;
  private root: HTMLElement;

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
        void this.enterApp();
      }),
    );
  }

  private async enterApp(): Promise<void> {
    try {
      this.currentUser = await api.me();
      this.showLobby();
    } catch {
      this.showAuth();
    }
  }

  private setScreen(screen: Screen): void {
    this.screen?.unmount();
    this.screen = screen;
    screen.mount();
  }

  private showAuth(): void {
    this.setScreen(
      new AuthScreen(this.root, (user) => {
        this.currentUser = user;
        this.showLobby();
      }),
    );
  }

  private showLobby(): void {
    this.setScreen(
      new LobbyScreen(this.root, this.currentUser!, {
        onMatchReady: (matchId, yourTeam) => this.showMatch(matchId, yourTeam),
        onOpenCodex: () => this.showCodex(),
        onLogout: () => {
          this.currentUser = null;
          this.showAuth();
        },
      }),
    );
  }

  /**
   * A screen of its own rather than a lobby sub-view: the lobby is a fixed-width card and
   * the roster wants the whole window. Coming back rebuilds the lobby, which also re-reads
   * the account - so a favourite changed here would be reflected there.
   */
  private showCodex(): void {
    this.setScreen(new CodexScreen(this.root, () => this.showLobby()));
  }

  private showMatch(matchId: string, yourTeam: Team): void {
    this.setScreen(new MatchScreen(this.root, matchId, yourTeam, () => this.showLobby()));
  }
}
