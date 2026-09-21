// Centralizes "which bouncing pointer arrow(s) for the current step + selection
// state" as one pure, hand-authored function - matching TutorialScript.ts's own
// philosophy of explicit per-step content over a generic abstraction. TutorialRunner
// calls this every time selection/gate-progress state changes and forwards the
// result straight to TutorialHost.setArrows.

import type { ArrowTarget } from "./types";

export interface ArrowContext {
  selectedUnitId: string | null;
  selectedAbilityId: string | null;
  awaitingAttribute: boolean;
  actedUnits: ReadonlySet<string>;
  placementMovedUnits: ReadonlySet<string>;
}

function domDown(selector: string): ArrowTarget {
  return { kind: "dom", selector, direction: "down" };
}

function domSide(selector: string): ArrowTarget {
  return { kind: "dom", selector, direction: "side" };
}

export function computeStepArrows(stepId: string, ctx: ArrowContext): ArrowTarget[] {
  switch (stepId) {
    case "draft-champion":
      return [domDown('[data-definition-id="valor"]')];
    case "draft-elite-auroth":
      return [domDown('[data-definition-id="auroth"]')];
    case "draft-elite-evayne":
      return [domDown('[data-definition-id="evayne"]')];
    case "draft-elite-thaddeus":
      return [domDown('[data-definition-id="thaddeus"]')];

    // Points at Thaddeus for the whole step (no switch to a tile once he's selected -
    // "frontmost tile" isn't a single fixed tile, the whole FRONT_ROW_Q column is
    // legal, and guessing which one to point at has looked wrong every time; left to
    // the dialogue alone). Just identifies which unit is Thaddeus, since a player may
    // not recognize him from the portrait art alone.
    case "placement-intro":
      return [{ kind: "unit", unitId: "u-thaddeus" }];

    case "placement-free":
      return ctx.placementMovedUnits.size >= 2 ? [domSide(".confirm-placement-btn")] : [];

    case "battle-basics":
      return ctx.selectedUnitId?.startsWith("u-basic-") ? [domSide('[data-hotkey-slot="move"]')] : [];

    case "battle-elites": {
      const trio = ["u-valor", "u-thaddeus", "u-evayne"];
      if (ctx.selectedUnitId && trio.includes(ctx.selectedUnitId) && !ctx.actedUnits.has(ctx.selectedUnitId)) {
        return [domSide('[data-hotkey-slot="move"]')];
      }
      return trio.filter((id) => !ctx.actedUnits.has(id)).map((unitId) => ({ kind: "unit", unitId }) as ArrowTarget);
    }

    case "battle-endturn":
      return [domSide(".end-turn-btn")];

    case "midbattle-evayne-attacks": {
      if (ctx.awaitingAttribute) return [];
      if (ctx.selectedUnitId !== "u-evayne") return [{ kind: "unit", unitId: "u-evayne" }];
      const arrows: ArrowTarget[] = [domSide('[data-hotkey-slot="attack"]')];
      if (ctx.selectedAbilityId === "attack") arrows.push({ kind: "unit", unitId: "e-grivath" });
      return arrows;
    }

    case "evayne-healed": {
      if (ctx.awaitingAttribute) return [];
      if (ctx.selectedUnitId !== "u-valor") return [{ kind: "unit", unitId: "u-valor" }];
      const arrows: ArrowTarget[] = [domSide('[data-hotkey-slot="attack"]')];
      if (ctx.selectedAbilityId === "attack") arrows.push({ kind: "unit", unitId: "e-harbinger" });
      return arrows;
    }

    case "evayne-missed":
      if (ctx.selectedUnitId !== "u-auroth") return [{ kind: "unit", unitId: "u-auroth" }];
      if (ctx.selectedAbilityId !== "cold_embrace") return [domSide('[data-ability-id="cold_embrace"]')];
      return [{ kind: "unit", unitId: "u-evayne" }];

    default:
      return [];
  }
}
