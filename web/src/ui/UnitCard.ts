import type { AbilityPreviewSnapshot, UnitDefinitionSnapshot } from "../types/contract";
import { renderUnitPortrait } from "../units/UnitPortrait";
import { abilityPreviewTooltip, type Tooltip } from "./Tooltip";

// Large square portrait shown on each unit card (see API_CONTRACT.md /
// web/public/icons/README.md) - big enough to read clearly in a modal, still
// comfortably under the recommended 256x256 source art so nothing upscales.
const CARD_ICON_SIZE = 140;

/**
 * One unit's card: portrait, name, type, statline, ability chips. Shared by the draft
 * modal and the codex so a hero reads identically wherever you meet them - the codex
 * exists precisely so you can study the card you will later be asked to pick from.
 *
 * No real art is dropped in for any unit yet (see web/public/icons/README.md) - the large
 * initial-letter badge is the deliberate placeholder, the same fallback the board's
 * sprites use, just bigger and square. There is no `team` here either: these are
 * undrafted definitions, owned by nobody, so the badge stays neutral rather than guessing.
 */
export function renderUnitCard(
  def: UnitDefinitionSnapshot,
  tooltip: Tooltip,
  onClick?: (def: UnitDefinitionSnapshot) => void,
): HTMLElement {
  const card = document.createElement("div");
  card.className = onClick ? "unit-card clickable" : "unit-card";

  card.appendChild(
    renderUnitPortrait({ definitionId: def.definitionId, unitType: def.type, name: def.name }, CARD_ICON_SIZE, "square"),
  );

  const name = document.createElement("h5");
  name.textContent = def.name;
  card.appendChild(name);

  const type = document.createElement("div");
  type.className = "hint";
  type.textContent = def.type;
  card.appendChild(type);

  const stats = document.createElement("div");
  stats.className = "stats";
  stats.textContent =
    `HP ${def.maxHp} · STR ${def.strength} · AGI ${def.agility} · INT ${def.intelligence} · Range ${def.attackRange}`;
  card.appendChild(stats);

  const abilities = document.createElement("div");
  abilities.className = "abilities";
  for (const ability of def.abilities) {
    abilities.appendChild(renderAbilityPreviewChip(ability, tooltip));
  }
  card.appendChild(abilities);

  if (onClick) {
    card.addEventListener("click", () => onClick(def));
  }
  return card;
}

/** The static counterpart to an in-match ability button - same hover content, no live cooldown or click-to-select. */
export function renderAbilityPreviewChip(ability: AbilityPreviewSnapshot, tooltip: Tooltip): HTMLElement {
  const chip = document.createElement("span");
  chip.className = "ability-chip";
  chip.textContent = `${ability.name}${ability.passive ? " (passive)" : ""}`;
  tooltip.attachAbility(chip, abilityPreviewTooltip(ability));
  return chip;
}
