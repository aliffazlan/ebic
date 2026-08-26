// DOM-only portrait badge for HUD modals (the encounter/attribute modal in
// Hud.ts). Mirrors UnitIconFactory's board art in spirit - same /icons/
// naming convention (an <img> pointed at /icons/<definitionId>.png, falling
// back to a team-colored circle + unitType-colored ring + glyph on a load
// error) - but is a lightweight parallel DOM implementation rather than a
// reuse of the Pixi RenderTexture pipeline, since UnitIconFactory is built
// around a Pixi Renderer and Hud.ts is plain DOM. Doesn't need to be pixel-
// identical to the board version.

import type { Team, UnitType } from "../types/contract";

const TEAM_COLORS: Record<Team, string> = {
  PLAYER_ONE: "#3b82f6", // blue
  PLAYER_TWO: "#ef4444", // red
};

const TYPE_RING_COLORS: Record<UnitType, string> = {
  CHAMPION: "#d4af37", // gold
  ELITE: "#c0c0c0", // silver
  BASIC: "#9ca3af", // gray
};

export interface PortraitInfo {
  definitionId: string;
  // Omitted for a not-yet-drafted candidate (draft cards) - there's no owning
  // team yet, so the badge falls back to a neutral background instead of
  // guessing one.
  team?: Team;
  unitType: UnitType;
  name: string;
}

const NEUTRAL_BG = "#0f172a";

export function renderUnitPortrait(
  info: PortraitInfo,
  size = 72,
  shape: "circle" | "square" = "circle",
): HTMLElement {
  const wrap = document.createElement("div");
  wrap.className = "unit-portrait";
  wrap.style.width = `${size}px`;
  wrap.style.height = `${size}px`;
  wrap.style.borderRadius = shape === "circle" ? "50%" : "12px";
  wrap.style.background = info.team ? (TEAM_COLORS[info.team] ?? "#999999") : NEUTRAL_BG;
  wrap.style.border = `3px solid ${TYPE_RING_COLORS[info.unitType] ?? "#ffffff"}`;
  wrap.style.position = "relative";
  wrap.style.flexShrink = "0";
  wrap.style.overflow = "hidden";

  const glyph = document.createElement("span");
  glyph.className = "unit-portrait-glyph";
  glyph.textContent = info.name.slice(0, 2).toUpperCase();
  glyph.style.position = "absolute";
  glyph.style.inset = "0";
  glyph.style.display = "flex";
  glyph.style.alignItems = "center";
  glyph.style.justifyContent = "center";
  glyph.style.fontWeight = "bold";
  glyph.style.fontSize = `${Math.round(size * 0.36)}px`;
  glyph.style.color = "#ffffff";
  wrap.appendChild(glyph);

  // If the real icon loads, it sits on top of (and visually replaces) the
  // procedural glyph fallback; if it 404s/errors, it removes itself and the
  // glyph badge underneath just shows through - same fallback story as
  // UnitIconFactory.tryLoadPng, minus the HEAD-request pre-check (a plain
  // <img> error handler is simpler and sufficient here).
  const img = new Image();
  img.src = `/icons/${info.definitionId}.png`;
  img.alt = info.name;
  img.style.position = "absolute";
  img.style.inset = "0";
  img.style.width = "100%";
  img.style.height = "100%";
  img.style.objectFit = "cover";
  img.onerror = () => img.remove();
  wrap.appendChild(img);

  return wrap;
}
