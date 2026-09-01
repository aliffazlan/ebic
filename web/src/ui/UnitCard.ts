import type { AbilityPreviewSnapshot, UnitDefinitionSnapshot } from "../types/contract";
import { renderUnitFullBody } from "../units/UnitPortrait";
import { abilityPreviewTooltip, type Tooltip } from "./Tooltip";

/**
 * One unit's card: portrait, name, type, statline, ability chips. Shared by the draft
 * modal and the codex so a hero reads identically wherever you meet them - the codex
 * exists precisely so you can study the card you will later be asked to pick from.
 *
 * The portrait is the hero's full-body art (see web/public/icons/README.md), sized by
 * `.unit-card .unit-art` rather than here; a hero with no art file falls back to the same
 * initial-letter badge the board's sprites use. There is no `team` here either: these are
 * undrafted definitions, owned by nobody, so the badge stays neutral rather than guessing.
 *
 * `mirrored` flips the art, which the draft uses to turn the opponent's column to face
 * yours. It defaults off: the codex is one grid with nobody to face, and an undrafted
 * definition has no team to derive a direction from anyway.
 */
export function renderUnitCard(
  def: UnitDefinitionSnapshot,
  tooltip: Tooltip,
  onClick?: (def: UnitDefinitionSnapshot) => void,
  mirrored = false,
): HTMLElement {
  const card = document.createElement("div");
  card.className = onClick ? "unit-card clickable" : "unit-card";

  card.appendChild(
    renderUnitFullBody(
      { definitionId: def.definitionId, unitType: def.type, name: def.name },
      mirrored,
    ),
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
