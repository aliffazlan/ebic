// Which art file does a given unit use?
//
// Art lives in two parallel folders, both keyed by the same id (see
// web/public/icons/README.md):
//   /icons/<artId>.webp      256x256 face close-up, the board's unit tokens
//   /portraits/<artId>.webp  512px-wide full body, the encounter modal and cards
//
// Usually that id is just the server's `definitionId`. The exception is BASIC:
// GameStateSnapshotMapper.definitionId() maps the whole type to the single
// string "basic", so all ten generic basics per side AND every summon collapse
// onto one id. Their display names are still distinct, so the id is recovered
// from `name` here rather than by widening the server contract.

import type { Team } from "../types/contract";

/**
 * Mirrors the server's Identifiers.normalize (see
 * ebic/src/main/java/com/walnutt/web/Identifiers.java) so a display name maps to
 * the same lowercase_underscore id the JSON filenames and `definitionId` use -
 * "Lanaya (Clone)" -> "lanaya_clone".
 */
export function normalizeId(name: string): string {
  let out = "";
  let lastWasUnderscore = false;
  for (const char of name.toLowerCase().trim()) {
    if (/[a-z0-9]/.test(char)) {
      out += char;
      lastWasUnderscore = false;
    } else if (!lastWasUnderscore && out.length > 0) {
      out += "_";
      lastWasUnderscore = true;
    }
  }
  return out.replace(/_+$/, "");
}

/**
 * Summons, by normalized display name. Most are just their own art; the
 * interesting entries are the ones whose summon name doesn't match the art id
 * ("Drone" is Maxwell's, "Snow Golem" is Yuki's) and Mercurial's Shadow, which
 * has no art of its own and borrows his - it is literally his shadow.
 *
 * "Pylon" maps to an id that has no file shipped yet, on purpose: a Pylon is a
 * structure, and showing it as a team knight would read as a bug. Missing art
 * falls back to the procedural badge, which is honest, and dropping
 * zenith_pylon.webp into both folders lights it up with no code change.
 */
const SUMMON_ART: Record<string, string> = {
  drone: "maxwell_drone",
  snow_golem: "yuki_golem",
  lanaya_clone: "lanaya_clone",
  mercurial_shadow: "mercurial",
  branchling: "branchling",
  branchigga: "branchigga",
  pylon: "zenith_pylon", // no art shipped - deliberately falls back to the badge
};

/**
 * Generic basics come in per-team colours: basic1 is the blue knight, basic2 the
 * red one, basic0 a neutral used where there is no owning team yet (a draft card).
 * basic3/basic4 (green/yellow) ship too but are unused until there are more than
 * two players.
 */
const BASIC_ART: Record<Team, string> = {
  PLAYER_ONE: "basic1",
  PLAYER_TWO: "basic2",
};
const NEUTRAL_BASIC_ART = "basic0";

export function artId(definitionId: string, name: string, team?: Team): string {
  if (definitionId !== "basic") return definitionId;
  // A generic basic is named "<player> Basic 3", which matches nothing here and
  // correctly falls through to the team-coloured knight.
  return SUMMON_ART[normalizeId(name)]
    ?? (team ? BASIC_ART[team] : NEUTRAL_BASIC_ART)
    ?? NEUTRAL_BASIC_ART;
}

export function faceUrl(id: string): string {
  return `/icons/${id}.webp`;
}

export function portraitUrl(id: string): string {
  return `/portraits/${id}.webp`;
}

// Already requested this session - the browser cache handles the rest, this just
// stops us building throwaway Image objects for the same id on every message.
const warmedPortraits = new Set<string>();

/**
 * Pulls full-body portraits into the browser cache ahead of the modal or card
 * that will show them. Deliberately low priority: the board's own art matters
 * more, and a portrait is never needed in the same frame it is warmed. A missing
 * file just errors quietly, exactly as the <img> fallback in UnitPortrait does.
 */
export function warmPortraits(ids: Iterable<string>): void {
  for (const id of ids) {
    if (warmedPortraits.has(id)) continue;
    warmedPortraits.add(id);
    const img = new Image();
    img.fetchPriority = "low";
    img.decoding = "async";
    img.src = portraitUrl(id);
  }
}
