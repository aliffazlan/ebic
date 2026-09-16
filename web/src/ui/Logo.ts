// The app wordmark, shown above the card on the auth and lobby screens instead
// of a plain <h1>EBIC</h1> - it sits above .card rather than inside it because
// it's the screen's title, not the panel's. A free function rather than a
// class, same idiom as UnitCard.ts's renderUnitCard: no lifecycle to manage.

export const LOGO_URL = "/assets/ui/logo.webp";

/**
 * `playIntro` adds the CSS class that drives the one-time boot fade-in
 * (see .app-logo.intro-reveal in style.css) - callers pass true only for the
 * very first render right after the click-to-continue screen, and must go
 * back to false afterward so a later re-render never replays it.
 */
export function renderLogo(playIntro = false): HTMLElement {
  const wrap = document.createElement("div");
  wrap.className = playIntro ? "app-logo intro-reveal" : "app-logo";

  const img = document.createElement("img");
  img.src = LOGO_URL;
  img.alt = "EBIC";
  wrap.appendChild(img);

  return wrap;
}
