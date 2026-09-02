// Shared colour constants and formatters.
//
// Team colour used to be declared separately in UnitIconFactory (as Pixi
// numbers) and UnitPortrait (as "r, g, b" strings for CSS gradients), with the
// same values written out twice. One definition here, formatted per consumer.

import type { Team } from "../types/contract";

/**
 * The colour a unit is identified by: its board token's ring, its portrait
 * backdrop, and its name in the combat log all use this, so a name in the log
 * matches the token you're looking at.
 *
 * Note the `.badge.team-one` / `.team-two` CSS rules deliberately use a darker
 * pair - those are solid pills with white text on them, a different job.
 */
export const TEAM_COLOR: Record<Team, number> = {
  PLAYER_ONE: 0x3b82f6, // blue-500
  PLAYER_TWO: 0xef4444, // red-500
};

/** 0x3b82f6 -> "#3b82f6", for CSS and for anything that parses SVG colours. */
export function cssHex(color: number): string {
  return `#${color.toString(16).padStart(6, "0")}`;
}

/** 0x3b82f6 -> "59, 130, 246", for building rgba() from a base colour. */
export function cssRgbTriple(color: number): string {
  return `${(color >> 16) & 0xff}, ${(color >> 8) & 0xff}, ${color & 0xff}`;
}

export function teamCssColor(team: Team): string {
  return cssHex(TEAM_COLOR[team]);
}
