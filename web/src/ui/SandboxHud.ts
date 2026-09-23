import type { MatchUiState, SandboxToolMode } from "../state/GameStateStore";
import type { Team, UnitDefinitionSnapshot } from "../types/contract";
import type { MatchActions } from "./MatchActions";
import { teamCssColor } from "./Colors";

/**
 * The SANDBOX tab and the unit picker it opens. Only ever rendered for a sandbox match -
 * Hud never builds either for a real one - so nothing here has to guard against being
 * shown to a player who can't use it.
 */

/** A sandbox player is both sides, so "Yours"/"Enemy" means nothing - sides go by colour. */
export const SIDE_NAMES: Record<Team, string> = { PLAYER_ONE: "Blue", PLAYER_TWO: "Red" };

/** Server-side id for the generic basic (SandboxController.BASIC_ID) - it has no JSON of its own. */
const BASIC_DEFINITION_ID = "basic";

/**
 * Tools only work while the server is waiting for an action - never mid-encounter or
 * mid-dialogue, and not in the moment between sending one command and the fresh prompt
 * that follows it.
 */
export function sandboxToolsAvailable(state: MatchUiState): boolean {
  return state.prompt?.kind === "action" && !state.gameOver;
}

/** What the board is waiting for while a tool is armed - shown in the tab and the sidebar. */
export function sandboxToolHint(tool: SandboxToolMode): string {
  switch (tool.kind) {
    case "spawn":
      return `Click a highlighted tile to spawn ${tool.name} (${SIDE_NAMES[tool.team]}).`;
    case "remove":
      return "Click a unit to remove it from the game.";
    case "heal":
      return "Click a unit to restore it to full health.";
  }
}

export function renderSandboxPanel(state: MatchUiState, actions: MatchActions): HTMLElement {
  const section = document.createElement("div");
  section.className = "hud-section sandbox-panel";

  const h3 = document.createElement("h3");
  h3.textContent = "Sandbox";
  section.appendChild(h3);

  const available = sandboxToolsAvailable(state);

  const button = (label: string, onClick: () => void, team?: Team): HTMLButtonElement => {
    const btn = document.createElement("button");
    btn.textContent = label;
    btn.disabled = !available;
    if (team) btn.style.borderLeft = `4px solid ${teamCssColor(team)}`;
    btn.addEventListener("click", onClick);
    return btn;
  };

  const group = (...buttons: HTMLButtonElement[]): HTMLElement => {
    const row = document.createElement("div");
    row.className = "sandbox-button-row";
    row.append(...buttons);
    return row;
  };

  section.appendChild(group(
    button("Add P1 unit", () => actions.openSandboxPicker("PLAYER_ONE"), "PLAYER_ONE"),
    button("Add P2 unit", () => actions.openSandboxPicker("PLAYER_TWO"), "PLAYER_TWO"),
  ));
  section.appendChild(group(
    button("Remove unit", () => actions.startSandboxTool("remove")),
    button("Clear board", () => actions.sendSandbox({ tool: "clear" })),
  ));
  section.appendChild(group(
    button("Full heal unit", () => actions.startSandboxTool("heal")),
    button("Reset cooldowns", () => actions.sendSandbox({ tool: "reset_cooldowns" })),
  ));
  section.appendChild(group(
    button("Refill moves", () => actions.sendSandbox({ tool: "refill_moves" })),
    button("Switch team", () => actions.sendSandbox({ tool: "switch_team" })),
  ));

  const hint = document.createElement("div");
  hint.className = "hint sandbox-hint";
  if (state.sandboxTool) {
    hint.textContent = `${sandboxToolHint(state.sandboxTool)} Clicking anywhere else cancels.`;
  } else if (!available) {
    hint.textContent = "Tools come back once the current action has resolved.";
  } else {
    hint.textContent =
      "Switch team hands control over without ending the turn - no end- or start-of-turn effects fire.";
  }
  section.appendChild(hint);

  if (state.sandboxTool) {
    const cancel = document.createElement("button");
    cancel.textContent = "Cancel";
    cancel.addEventListener("click", () => actions.cancelSandboxTool());
    section.appendChild(cancel);
  }

  return section;
}

/**
 * The ADD P1/P2 UNIT picker. `units` is null while the roster is still loading; `loadError`
 * replaces the list if fetching it failed. Summons never appear: the roster endpoint only
 * serves the draftable pool, and the generic basic is added here by hand.
 */
export function renderSandboxPicker(
  team: Team,
  units: UnitDefinitionSnapshot[] | null,
  loadError: string | null,
  actions: MatchActions,
): HTMLElement {
  const backdrop = document.createElement("div");
  backdrop.className = "modal-backdrop";

  const panel = document.createElement("div");
  panel.className = "modal-panel sandbox-picker-panel";
  panel.style.borderColor = teamCssColor(team);
  backdrop.appendChild(panel);

  const title = document.createElement("h2");
  title.textContent = `Add ${team === "PLAYER_ONE" ? "P1" : "P2"} unit (${SIDE_NAMES[team]})`;
  panel.appendChild(title);

  if (loadError) {
    const error = document.createElement("div");
    error.className = "hint";
    error.textContent = loadError;
    panel.appendChild(error);
  } else if (!units) {
    const loading = document.createElement("div");
    loading.className = "hint";
    loading.textContent = "Loading units…";
    panel.appendChild(loading);
  } else {
    const byName = (a: UnitDefinitionSnapshot, b: UnitDefinitionSnapshot) => a.name.localeCompare(b.name);
    const groups: { label: string; entries: { id: string; name: string }[] }[] = [
      { label: "Champions", entries: units.filter((u) => u.type === "CHAMPION").sort(byName).map(toEntry) },
      { label: "Elites", entries: units.filter((u) => u.type === "ELITE").sort(byName).map(toEntry) },
      { label: "Basic", entries: [{ id: BASIC_DEFINITION_ID, name: "Basic" }] },
    ];
    for (const { label, entries } of groups) {
      const heading = document.createElement("h3");
      heading.textContent = label;
      panel.appendChild(heading);

      const list = document.createElement("div");
      list.className = "sandbox-picker-list";
      for (const entry of entries) {
        const btn = document.createElement("button");
        btn.textContent = entry.name;
        btn.addEventListener("click", () => actions.chooseSandboxSpawn(team, entry.id, entry.name));
        list.appendChild(btn);
      }
      panel.appendChild(list);
    }
  }

  const cancel = document.createElement("button");
  cancel.className = "choice-cancel";
  cancel.textContent = "Cancel";
  cancel.addEventListener("click", () => actions.openSandboxPicker(null));
  panel.appendChild(cancel);

  return backdrop;
}

function toEntry(unit: UnitDefinitionSnapshot): { id: string; name: string } {
  return { id: unit.definitionId, name: unit.name };
}
