// Combat-log line building: turns a batch of VfxEvents into coloured text
// segments. Pure - no DOM, no PixiJS - so it's unit-testable and so the Hud
// only has to know how to paint segments, not how to phrase a hit.
//
// Colours come from VfxIndicators, the same module the floating damage numbers
// use, so a number in the log and the number that floated off the unit can
// never disagree about what colour "poison" is.

import type { Team, UnitSnapshot, VfxEvent } from "../types/contract";
import { colorForKind, damageKind, isMissEvent } from "../vfx/VfxIndicators";
import { cssHex, teamCssColor } from "../ui/Colors";

export interface CombatLogSegment {
  text: string;
  /** CSS colour; omitted means "inherit the line's default". */
  color?: string;
  bold?: boolean;
}

export interface CombatLogEntry {
  segments: CombatLogSegment[];
  /**
   * Whose turn produced this line, which is what splits the log into halves.
   * Assigned when the line is committed rather than when it's built - see
   * GameStateStore.commitCombatLog for why it can't be known any earlier.
   */
  team: Team | null;
}

/** Resolves a unit id against the snapshot the events were built from. */
export type FindUnit = (unitId: string | null) => UnitSnapshot | null;

const UNKNOWN_NAME = "Unknown";
/** Grey, matching the MISS indicator - nothing happened. */
const MISS_TEXT_COLOR = cssHex(colorForKind("miss"));

function name(unit: UnitSnapshot | null): CombatLogSegment {
  if (!unit) return { text: UNKNOWN_NAME };
  return { text: unit.name, color: teamCssColor(unit.team) };
}

/** A bold, coloured number - the thing you actually want to find when skimming. */
function amountSegment(amount: number, color: string): CombatLogSegment {
  return { text: String(amount), color, bold: true };
}

/**
 * One line's worth of segments per loggable event. Events that aren't worth a
 * line (moves, plain attacks already covered by their damage event, deaths,
 * status applications) produce nothing.
 */
export function buildCombatLogLines(events: VfxEvent[], findUnit: FindUnit): CombatLogSegment[][] {
  const lines: CombatLogSegment[][] = [];

  for (const event of events) {
    if (event.type === "damage") {
      const target = findUnit(event.targetUnitId);
      const source = findUnit(event.sourceUnitId);

      if (isMissEvent(event)) {
        lines.push([
          name(source),
          { text: " missed their attack on ", color: MISS_TEXT_COLOR },
          name(target),
        ]);
        continue;
      }

      const amount = event.amount ?? 0;
      const kind = damageKind(event.causeLabel);
      const color = cssHex(colorForKind(kind));

      if (event.causeLabel === "Attack") {
        lines.push([
          name(source),
          { text: " attacks " },
          name(target),
          { text: " for " },
          amountSegment(amount, color),
          { text: " damage" },
        ]);
      } else if (event.causeLabel) {
        // The cause takes the same colour as its number, so "Poison" and the
        // number it dealt read as one thing.
        lines.push([
          name(target),
          { text: " takes " },
          amountSegment(amount, color),
          { text: " damage from " },
          { text: event.causeLabel, color },
        ]);
      } else {
        lines.push([name(target), { text: " takes " }, amountSegment(amount, color), { text: " damage" }]);
      }
    } else if (event.type === "heal") {
      // Heals carry no source and no cause label from the server, so the target
      // is the whole line.
      const amount = event.amount ?? 0;
      if (amount <= 0) continue;
      lines.push([
        name(findUnit(event.targetUnitId)),
        { text: " heals for " },
        amountSegment(amount, cssHex(colorForKind("heal"))),
      ]);
    } else if (event.type === "ability_used") {
      // "move" isn't interesting for a combat log; "attack" already gets its own
      // damage line above - logging both would be redundant.
      if (event.abilityId === "move" || event.abilityId === "attack") continue;
      const source = findUnit(event.sourceUnitId);
      const abilityName =
        source?.abilities.find((a) => a.id === event.abilityId)?.name ?? event.abilityId ?? "an ability";
      const abilityColor = cssHex(colorForKind("ability"));

      if (event.targetUnitId) {
        lines.push([
          name(source),
          { text: " cast " },
          { text: abilityName, color: abilityColor },
          { text: " on " },
          name(findUnit(event.targetUnitId)),
        ]);
      } else {
        lines.push([name(source), { text: " cast " }, { text: abilityName, color: abilityColor }]);
      }
    }
  }

  return lines;
}
