// Turns raw VfxEvents into the floating numbers the board draws over units.
// Pure - no PixiJS, no DOM - so it's unit-testable under vitest's node
// environment, and so the combat log and the board can share one definition of
// what counts as a miss.

import type { VfxEvent } from "../types/contract";
// Bundled as source text rather than fetched from public/: these six are
// code-coupled (exactly one per IndicatorKind, all required), so importing
// them makes the set synchronous - an indicator can never render before its
// icon arrived - and lets the Record type below enforce completeness.
import abilityIcon from "./icons/ability.svg?raw";
import attackIcon from "./icons/attack.svg?raw";
import burnIcon from "./icons/burn.svg?raw";
import healIcon from "./icons/heal.svg?raw";
import missIcon from "./icons/miss.svg?raw";
import poisonIcon from "./icons/poison.svg?raw";

export type IndicatorKind = "attack" | "ability" | "burn" | "poison" | "heal" | "miss";

export interface IndicatorSpec {
  /** Unit to float the text above. */
  unitId: string;
  text: string;
  color: number;
  kind: IndicatorKind;
}

// Colours are drawn from hues already on the board rather than invented, so a
// number reads as part of the same visual language as the tile it sits over.
/** Basic weapon attacks only - the engine's default causeLabel. Matches the low-HP bar. */
const ATTACK_COLOR = 0xef4444;
/** Everything else that deals damage: abilities, and every DoT but burn and poison. */
const ABILITY_COLOR = 0x60a5fa;
/** Burn. Same orange as the pre-encounter strobe ring and the burning-ground overlay. */
const BURN_COLOR = 0xf97316;
/** Poison. Deliberately darker than HEAL_COLOR so "losing HP" never reads as "gaining it". */
const POISON_COLOR = 0x15803d;
const HEAL_COLOR = 0x22c55e;
/** A turned-aside attack. The same grey the death burst uses - nothing happened. */
const MISS_COLOR = 0x9ca3af;

// causeLabel values that come from the engine's rock-paper-scissors attribute
// resolution - a 0-damage event with one of these labels is a real RPS "miss"
// (attacker's attribute lost, or tied unfavorably), not just an ability tick
// that happened to roll 0.
//
// "Duel" never actually reaches the wire today (DuelEffect goes through
// CombatEngine's "Attack" back-fill and sets no label of its own), but it costs
// nothing to keep and would start working the moment that changes.
export const ENCOUNTER_CAUSE_LABELS: ReadonlySet<string> = new Set([
  "Attack",
  "Counterstrike",
  "Duel",
  "Cloak and Dagger",
]);

/** The engine's default causeLabel, back-filled by CombatEngine for a plain attack. */
const ATTACK_CAUSE_LABEL = "Attack";
const BURN_CAUSE_LABEL = "Burn";
const POISON_CAUSE_LABEL = "Poison";

/**
 * True when a damage event is a turned-aside attack rather than a hit for zero.
 * The server has no miss flag: both are `{type: "damage", amount: 0}`, and the
 * cause label is the only thing separating them.
 */
export function isMissEvent(event: VfxEvent): boolean {
  return (
    event.type === "damage" &&
    (event.amount ?? 0) === 0 &&
    event.causeLabel !== null &&
    ENCOUNTER_CAUSE_LABELS.has(event.causeLabel)
  );
}

/**
 * The floating indicator for one event, or null for events that shouldn't get a
 * number: casts, deaths, status applications, anything with no target to float
 * over, and a non-miss that lands for zero (an ability tick that rolled 0 - a
 * floating "0" is noise, not information).
 */
export function indicatorFor(event: VfxEvent): IndicatorSpec | null {
  if (event.type !== "damage" && event.type !== "heal") return null;
  const unitId = event.targetUnitId;
  if (!unitId) return null;

  if (isMissEvent(event)) {
    return { unitId, text: "MISS", color: MISS_COLOR, kind: "miss" };
  }

  const amount = event.amount ?? 0;
  if (amount <= 0) return null;

  if (event.type === "heal") {
    return { unitId, text: `+${amount}`, color: HEAL_COLOR, kind: "heal" };
  }

  const kind = damageKind(event.causeLabel);
  return { unitId, text: `-${amount}`, color: colorForKind(kind), kind };
}

/** The indicator kind a damage event's cause label maps to. Shared with the combat log. */
export function damageKind(causeLabel: string | null): IndicatorKind {
  switch (causeLabel) {
    case BURN_CAUSE_LABEL:
      return "burn";
    case POISON_CAUSE_LABEL:
      return "poison";
    case ATTACK_CAUSE_LABEL:
      return "attack";
    // Every other label is an ability - and so is a null label, which only
    // happens when damage is applied without going through CombatEngine.
    default:
      return "ability";
  }
}

/**
 * The icon drawn to the left of each number, as raw SVG text.
 *
 * Every icon paints with `fill="currentColor"` - a placeholder, since nothing
 * here resolves CSS. The renderer substitutes the kind's own colour into the
 * string before parsing it, so the icon and the number are tinted from this
 * file's one set of colour constants rather than having the colour baked into
 * the art. Typing this as a full Record means a new IndicatorKind won't compile
 * until it has an icon.
 */
export const ICON_SVG: Record<IndicatorKind, string> = {
  attack: attackIcon,
  ability: abilityIcon,
  burn: burnIcon,
  poison: poisonIcon,
  heal: healIcon,
  miss: missIcon,
};

/** The placeholder every icon's fill uses, swapped for a real colour at parse time. */
export const ICON_COLOR_PLACEHOLDER = "currentColor";

export function colorForKind(kind: IndicatorKind): number {
  switch (kind) {
    case "attack": return ATTACK_COLOR;
    case "burn": return BURN_COLOR;
    case "poison": return POISON_COLOR;
    case "heal": return HEAL_COLOR;
    case "miss": return MISS_COLOR;
    case "ability": return ABILITY_COLOR;
  }
}

/**
 * Which stagger slot an indicator belongs to, lowest first.
 *
 * At the start of a turn every damage-over-time effect ticks at once, so the
 * whole batch arrives together and used to be unreadable. Splitting it into
 * poison, then burn, then everything else lets each source be read on its own.
 * Ordinary combat only ever produces slot 2, so a normal attack still shows its
 * number immediately.
 */
export function indicatorGroup(kind: IndicatorKind): number {
  if (kind === "poison") return 0;
  if (kind === "burn") return 1;
  return 2;
}

/** Splits a batch into stagger slots, dropping empty ones, lowest slot first. */
export function groupIndicators(events: VfxEvent[]): IndicatorSpec[][] {
  const groups = new Map<number, IndicatorSpec[]>();
  for (const event of events) {
    const spec = indicatorFor(event);
    if (!spec) continue;
    const group = indicatorGroup(spec.kind);
    const existing = groups.get(group);
    if (existing) existing.push(spec);
    else groups.set(group, [spec]);
  }
  return [...groups.entries()].sort((a, b) => a[0] - b[0]).map(([, specs]) => specs);
}
