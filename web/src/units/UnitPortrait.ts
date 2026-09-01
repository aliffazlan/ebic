// DOM full-body portrait for the HUD's encounter modal and for draft/codex unit
// cards. The board's own tokens are the Pixi path in UnitIconFactory - this is a
// lightweight parallel implementation rather than a reuse of it, since that one
// is built around a Pixi Renderer and Hud.ts is plain DOM. The two don't need to
// be pixel-identical; they show different art (a face token vs a full body) at
// deliberately different sizes.

import type { Team, UnitType } from "../types/contract";
import { artId, portraitUrl } from "./UnitArt";

// As rgb triples so the backdrop below can build a translucent gradient from
// them; the flat hex is still what the fallback badge fills with.
const TEAM_COLORS: Record<Team, string> = {
  PLAYER_ONE: "59, 130, 246", // blue
  PLAYER_TWO: "239, 68, 68", // red
};

const TYPE_RING_COLORS: Record<UnitType, string> = {
  CHAMPION: "#d4af37", // gold
  ELITE: "#c0c0c0", // silver
  BASIC: "#9ca3af", // gray
};

export interface PortraitInfo {
  definitionId: string;
  // Omitted for a not-yet-drafted candidate (draft cards) - there's no owning
  // team yet, so the fallback badge stays neutral instead of guessing one.
  team?: Team;
  unitType: UnitType;
  name: string;
}

const NEUTRAL_BG = "#0f172a";

/**
 * A unit's full-body art, sized entirely by the caller's CSS. The element gets
 * no width of its own on purpose: `.encounter-card .unit-art` and
 * `.unit-card .unit-art` want different widths, and the <img> inside is
 * `width: 100%; height: auto`, so whatever width the parent picks, the height
 * follows the source image's own ratio. The portraits are portrait-oriented but
 * not all the same shape (ratios run 0.67 to 0.87), which is exactly why the
 * height is never pinned here.
 *
 * Every portrait is drawn facing right, so `mirrored` flips one side to make two
 * units confront each other. It defaults to the board's rule - PLAYER_TWO turns -
 * but callers whose layout is relative to the *viewer* rather than to the teams
 * override it, because "my unit" and "PLAYER_ONE" are not the same slot for both
 * players. See renderEncounterCard and renderDraftColumn.
 */
export function renderUnitFullBody(
  info: PortraitInfo,
  mirrored: boolean = info.team === "PLAYER_TWO",
): HTMLElement {
  const wrap = document.createElement("div");
  wrap.className = mirrored ? "unit-art unit-art-mirrored" : "unit-art";

  // The no-art fallback: the same first-letters badge the board falls back to,
  // filling the art slot. Built here but only attached if the image actually
  // fails - the sidebar panel re-renders on every HUD update, and a badge that
  // showed by default would flash over a cached portrait on each one.
  const glyph = document.createElement("span");
  glyph.className = "unit-art-glyph";
  glyph.textContent = info.name.slice(0, 2).toUpperCase();
  glyph.style.background = info.team ? `rgb(${TEAM_COLORS[info.team]})` : NEUTRAL_BG;
  glyph.style.border = `3px solid ${TYPE_RING_COLORS[info.unitType] ?? "#ffffff"}`;

  // The portraits are transparent cut-outs, so an owned unit gets a soft wash of
  // its team's colour behind it. That is the only thing carrying "whose is this"
  // on an encounter card now that the art has replaced the team-coloured disc
  // this used to be. Undrafted definitions (draft cards) have no owner and get
  // nothing, which is why they sit on the card's own background.
  if (info.team) {
    const backdrop = document.createElement("span");
    backdrop.className = "unit-art-backdrop";
    backdrop.style.background =
      `radial-gradient(ellipse at 50% 62%, rgba(${TEAM_COLORS[info.team]}, 0.30), transparent 72%)`;
    wrap.appendChild(backdrop);
  }

  const img = new Image();
  img.src = portraitUrl(artId(info.definitionId, info.name, info.team));
  img.alt = info.name;
  // A codex grid holds every draftable hero at once; no reason to pull all of
  // them before they scroll into view. The two encounter portraits are on screen
  // the moment the modal opens, so this still fetches them immediately.
  img.loading = "lazy";
  img.decoding = "async";
  // Same fallback story as UnitIconFactory's missing-art path: no art file, show
  // the badge instead. The swap is one-way - the badge is never up while a real
  // portrait is on its way, so it can't show through the transparent artwork.
  img.onerror = () => {
    img.remove();
    wrap.appendChild(glyph);
  };
  wrap.appendChild(img);

  return wrap;
}
