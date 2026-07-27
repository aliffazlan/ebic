import type { GameStateStore, MatchUiState } from "../state/GameStateStore";
import type {
  AbilitySnapshot,
  Attribute,
  PlacementUnitSnapshot,
  Team,
  UnitDefinitionSnapshot,
  UnitSnapshot,
} from "../types/contract";
import type { MatchActions } from "./MatchActions";
import { renderUnitPortrait } from "../units/UnitPortrait";

const ATTRIBUTES: Attribute[] = ["STRENGTH", "AGILITY", "INTELLIGENCE"];

export class Hud {
  private unsubscribe: () => void;
  private hudHost: HTMLElement;
  private matchId: string;
  private store: GameStateStore;
  private actions: MatchActions;

  constructor(hudHost: HTMLElement, matchId: string, store: GameStateStore, actions: MatchActions) {
    this.hudHost = hudHost;
    this.matchId = matchId;
    this.store = store;
    this.actions = actions;
    this.unsubscribe = this.store.subscribe((state) => this.render(state));
  }

  destroy(): void {
    this.unsubscribe();
    this.hudHost.innerHTML = "";
  }

  private render(state: MatchUiState): void {
    this.hudHost.innerHTML = "";

    this.hudHost.appendChild(this.renderTopBar(state));

    const isYourTurn = state.snapshot?.currentTeam === state.yourTeam;
    if (state.snapshot && !isYourTurn && !state.gameOver) {
      const banner = document.createElement("div");
      banner.className = "hud-section";
      const inner = document.createElement("div");
      inner.className = "waiting-banner";
      inner.textContent = "Waiting for opponent...";
      banner.appendChild(inner);
      this.hudHost.appendChild(banner);
    }

    if (state.placementState) {
      this.hudHost.appendChild(this.renderPlacementPanel(state));
    } else {
      this.hudHost.appendChild(this.renderUnitPanel(state, isYourTurn));
    }
    this.hudHost.appendChild(this.renderMessageLog(state));

    // Modal overlays, highest priority first.
    if (state.gameOver) {
      this.hudHost.appendChild(this.renderGameOverModal(state));
    } else if (state.draftRound) {
      // Receiving a draft_round message doubles as the prompt to pick - see
      // API_CONTRACT.md, there's no separate "pick" prompt kind anymore.
      this.hudHost.appendChild(this.renderDraftModal(state));
    } else if (state.prompt?.kind === "attribute") {
      this.hudHost.appendChild(this.renderAttributeModal(state));
    }
  }

  private renderTopBar(state: MatchUiState): HTMLElement {
    const section = document.createElement("div");
    section.className = "hud-section top-bar";

    const left = document.createElement("div");
    const idLabel = document.createElement("div");
    idLabel.className = "hint";
    idLabel.textContent = `Match ${this.matchId.slice(0, 8)}`;
    left.appendChild(idLabel);

    const badges = document.createElement("div");
    badges.style.marginTop = "4px";
    const teamBadge = document.createElement("span");
    teamBadge.className = `badge ${state.yourTeam === "PLAYER_ONE" ? "team-one" : "team-two"}`;
    teamBadge.textContent = state.yourTeam === "PLAYER_ONE" ? "Player One" : "Player Two";
    badges.appendChild(teamBadge);

    if (state.snapshot) {
      const isYourTurn = state.snapshot.currentTeam === state.yourTeam;
      const turnBadge = document.createElement("span");
      turnBadge.className = `badge ${isYourTurn ? "turn-yours" : "turn-theirs"}`;
      turnBadge.style.marginLeft = "6px";
      turnBadge.textContent = isYourTurn ? "Your turn" : "Opponent's turn";
      badges.appendChild(turnBadge);
    }
    left.appendChild(badges);
    section.appendChild(left);

    const leaveBtn = document.createElement("button");
    leaveBtn.textContent = "Leave";
    leaveBtn.addEventListener("click", () => this.actions.exitToLobby());
    section.appendChild(leaveBtn);

    return section;
  }

  private renderUnitPanel(state: MatchUiState, isYourTurn: boolean): HTMLElement {
    const section = document.createElement("div");
    section.className = "hud-section";
    const h3 = document.createElement("h3");
    h3.textContent = "Selected unit";
    section.appendChild(h3);

    const unit = this.store.findUnit(state.selectedUnitId);
    if (!unit) {
      const empty = document.createElement("div");
      empty.className = "hint";
      empty.textContent = "Click a unit to inspect it.";
      section.appendChild(empty);
      return section;
    }

    const name = document.createElement("div");
    name.className = "unit-panel-name";
    name.textContent = unit.name;
    section.appendChild(name);

    const sub = document.createElement("div");
    sub.className = "unit-panel-sub";
    sub.textContent = `${unit.unitType} · ${unit.team === state.yourTeam ? "Yours" : "Enemy"}${unit.dead ? " · Dead" : ""}`;
    section.appendChild(sub);

    const hpOuter = document.createElement("div");
    hpOuter.className = "hp-bar-outer";
    const hpInner = document.createElement("div");
    hpInner.className = "hp-bar-inner";
    hpInner.style.width = `${unit.maxHp > 0 ? Math.max(0, (unit.currentHp / unit.maxHp) * 100) : 0}%`;
    hpOuter.appendChild(hpInner);
    section.appendChild(hpOuter);

    const hpText = document.createElement("div");
    hpText.className = "hint";
    hpText.style.marginBottom = "8px";
    hpText.textContent = `${unit.currentHp} / ${unit.maxHp} HP`;
    section.appendChild(hpText);

    const attrText = document.createElement("div");
    attrText.className = "hint";
    attrText.style.marginBottom = "8px";
    attrText.textContent = `STR ${unit.strength} · AGI ${unit.agility} · INT ${unit.intelligence}`;
    section.appendChild(attrText);

    if (unit.statusFlags.length > 0) {
      const statusRow = document.createElement("div");
      for (const flag of unit.statusFlags) {
        const chip = document.createElement("span");
        chip.className = "status-chip";
        chip.textContent = flag;
        statusRow.appendChild(chip);
      }
      section.appendChild(statusRow);
    }

    const canAct = unit.team === state.yourTeam && isYourTurn && state.prompt?.kind !== "attribute";
    const abilityList = document.createElement("div");
    abilityList.className = "ability-list";
    for (const ability of unit.abilities) {
      abilityList.appendChild(this.renderAbilityButton(unit.id, ability, state, canAct));
    }
    section.appendChild(abilityList);

    if (state.selectedAbilityId && unit.id === state.selectedUnitId) {
      const targetHint = document.createElement("div");
      targetHint.className = "hint";
      targetHint.style.marginTop = "8px";
      targetHint.textContent = "Click a unit or tile on the board to target this ability.";
      section.appendChild(targetHint);

      const row = document.createElement("div");
      row.className = "row";
      row.style.marginTop = "6px";

      const noTargetBtn = document.createElement("button");
      noTargetBtn.textContent = "Cast (no target)";
      noTargetBtn.addEventListener("click", () => this.actions.castAbility("none"));
      row.appendChild(noTargetBtn);

      const cancelBtn = document.createElement("button");
      cancelBtn.textContent = "Cancel";
      cancelBtn.addEventListener("click", () => this.actions.selectAbility(null));
      row.appendChild(cancelBtn);

      section.appendChild(row);
    }

    if (canAct) {
      const endTurnBtn = document.createElement("button");
      endTurnBtn.className = "primary";
      endTurnBtn.style.marginTop = "10px";
      endTurnBtn.style.width = "100%";
      endTurnBtn.textContent = "End Turn";
      endTurnBtn.addEventListener("click", () => this.actions.endTurn());
      section.appendChild(endTurnBtn);
    }

    return section;
  }

  private renderAbilityButton(
    unitId: string,
    ability: AbilitySnapshot,
    state: MatchUiState,
    canAct: boolean,
  ): HTMLElement {
    const btn = document.createElement("button");
    btn.className = "ability-btn";
    if (state.selectedAbilityId === ability.id) btn.classList.add("selected");

    const label = document.createElement("span");
    label.textContent = `${ability.name}${ability.passive ? " (passive)" : ""}`;
    btn.appendChild(label);

    if (!ability.ready) {
      const cd = document.createElement("span");
      cd.className = "ability-cd";
      cd.textContent = `CD ${ability.currentCooldown}/${ability.maxCooldown}`;
      btn.appendChild(cd);
    }

    const disabled = !canAct || ability.passive || !ability.ready;
    btn.disabled = disabled;
    btn.addEventListener("click", () => {
      this.actions.selectUnit(unitId);
      this.actions.selectAbility(ability.id);
    });
    return btn;
  }

  private renderMessageLog(state: MatchUiState): HTMLElement {
    const log = document.createElement("div");
    log.className = "message-log";
    for (const msg of state.messages) {
      const line = document.createElement("div");
      line.textContent = msg;
      log.appendChild(line);
    }
    log.scrollTop = log.scrollHeight;
    return log;
  }

  private renderDraftModal(state: MatchUiState): HTMLElement {
    const backdrop = document.createElement("div");
    backdrop.className = "modal-backdrop";

    const panel = document.createElement("div");
    panel.className = "modal-panel";
    backdrop.appendChild(panel);

    const title = document.createElement("h2");
    title.textContent = state.draftRound!.roundLabel;
    panel.appendChild(title);

    // Each player drafts through their own rounds at their own pace (no
    // synchronized reveal moment anymore), but the opponent's same-round
    // options are still shown alongside for transparency - the whole pool is
    // already decided the moment both players join, so this needs no
    // synchronization with the opponent's actual progress. See
    // API_CONTRACT.md's draft_round section.
    const columns = document.createElement("div");
    columns.className = "draft-columns";
    panel.appendChild(columns);

    columns.appendChild(this.renderDraftColumn("Your options", state.draftRound!.options, true));
    columns.appendChild(
      this.renderDraftColumn("Opponent options", state.draftRound!.opponentOptions, false),
    );

    return backdrop;
  }

  private renderDraftColumn(
    heading: string,
    options: UnitDefinitionSnapshot[],
    clickable: boolean,
  ): HTMLElement {
    const col = document.createElement("div");
    col.className = "draft-column";
    const h4 = document.createElement("h4");
    h4.textContent = heading;
    col.appendChild(h4);

    const cards = document.createElement("div");
    cards.className = "draft-cards";
    for (const def of options) {
      cards.appendChild(this.renderUnitCard(def, clickable));
    }
    col.appendChild(cards);
    return col;
  }

  private renderUnitCard(def: UnitDefinitionSnapshot, clickable: boolean): HTMLElement {
    const card = document.createElement("div");
    card.className = clickable ? "unit-card clickable" : "unit-card";

    const name = document.createElement("h5");
    name.textContent = def.name;
    card.appendChild(name);

    const type = document.createElement("div");
    type.className = "hint";
    type.textContent = def.type;
    card.appendChild(type);

    const stats = document.createElement("div");
    stats.className = "stats";
    stats.textContent = `HP ${def.maxHp} · STR ${def.strength} · AGI ${def.agility} · INT ${def.intelligence}`;
    card.appendChild(stats);

    const abilities = document.createElement("div");
    abilities.className = "abilities";
    abilities.textContent = def.abilities.join(", ");
    card.appendChild(abilities);

    if (clickable) {
      card.addEventListener("click", () => this.actions.sendPick(def.definitionId));
    }
    return card;
  }

  private renderPlacementPanel(state: MatchUiState): HTMLElement {
    const section = document.createElement("div");
    section.className = "hud-section";

    const h3 = document.createElement("h3");
    h3.textContent = "Placement";
    section.appendChild(h3);

    const placement = state.placementState!;

    if (placement.confirmed) {
      const banner = document.createElement("div");
      banner.className = "waiting-banner";
      banner.textContent = "Placement confirmed - waiting for opponent...";
      section.appendChild(banner);
      return section;
    }

    const hint = document.createElement("div");
    hint.className = "hint";
    hint.style.marginBottom = "8px";
    hint.textContent = state.selectedUnitId
      ? "Click another unit to swap with it, an empty tile to move it there, or click it again to cancel."
      : "Click one of your units - on the board or in the list below - to select it.";
    section.appendChild(hint);

    const roster = document.createElement("div");
    roster.className = "placement-roster";
    for (const unit of placement.units) {
      roster.appendChild(this.renderPlacementRow(unit, state.selectedUnitId));
    }
    section.appendChild(roster);

    const confirmBtn = document.createElement("button");
    confirmBtn.className = "primary confirm-placement-btn";
    confirmBtn.textContent = "Confirm placement";
    confirmBtn.addEventListener("click", () => this.actions.confirmPlacement());
    section.appendChild(confirmBtn);

    return section;
  }

  private renderPlacementRow(unit: PlacementUnitSnapshot, selectedUnitId: string | null): HTMLElement {
    const row = document.createElement("div");
    row.className = "placement-unit-row";
    if (unit.unitId === selectedUnitId) row.classList.add("selected");

    const name = document.createElement("span");
    name.textContent = unit.name;
    row.appendChild(name);

    const type = document.createElement("span");
    type.className = "hint";
    type.textContent = unit.unitType;
    row.appendChild(type);

    row.addEventListener("click", () => {
      if (selectedUnitId === unit.unitId) {
        this.actions.selectUnit(null);
      } else if (selectedUnitId) {
        this.actions.sendPlacementSwap(selectedUnitId, unit.unitId);
      } else {
        this.actions.selectUnit(unit.unitId);
      }
    });

    return row;
  }

  private renderAttributeModal(state: MatchUiState): HTMLElement {
    const backdrop = document.createElement("div");
    backdrop.className = "modal-backdrop";

    const panel = document.createElement("div");
    panel.className = "modal-panel attribute-modal-panel";
    backdrop.appendChild(panel);

    const title = document.createElement("h2");
    title.textContent = "Choose an attribute";
    panel.appendChild(title);

    // prompt.kind is narrowed to "attribute" by the caller (Hud.render); both
    // ids resolve against the last "state" message's units - no fog of war
    // once combat has started, see API_CONTRACT.md.
    const prompt = state.prompt as { kind: "attribute"; unitId: string; opponentUnitId: string };
    const self = this.store.findUnit(prompt.unitId);
    const opponent = this.store.findUnit(prompt.opponentUnitId);

    const encounter = document.createElement("div");
    encounter.className = "encounter-row";
    encounter.appendChild(this.renderEncounterCard(self, state.yourTeam));

    const vs = document.createElement("div");
    vs.className = "encounter-vs";
    vs.textContent = "VS";
    encounter.appendChild(vs);

    encounter.appendChild(this.renderEncounterCard(opponent, state.yourTeam));
    panel.appendChild(encounter);

    const row = document.createElement("div");
    row.className = "attribute-buttons";
    for (const attr of ATTRIBUTES) {
      const btn = document.createElement("button");
      btn.className = "primary";
      btn.textContent = attr;
      btn.addEventListener("click", () => this.actions.sendAttribute(attr));
      row.appendChild(btn);
    }
    panel.appendChild(row);

    return backdrop;
  }

  private renderEncounterCard(unit: UnitSnapshot | null, yourTeam: Team): HTMLElement {
    const card = document.createElement("div");
    card.className = "encounter-card";

    if (!unit) {
      // Shouldn't happen in practice (both ids come straight from the last
      // real state snapshot), but degrade gracefully rather than throw.
      card.textContent = "Unknown unit";
      return card;
    }

    card.appendChild(
      renderUnitPortrait(
        { definitionId: unit.definitionId, team: unit.team, unitType: unit.unitType, name: unit.name },
        72,
      ),
    );

    const name = document.createElement("div");
    name.className = "encounter-card-name";
    name.textContent = unit.name;
    card.appendChild(name);

    const sub = document.createElement("div");
    sub.className = "hint";
    sub.textContent = `${unit.unitType} · ${unit.team === yourTeam ? "Yours" : "Enemy"}`;
    card.appendChild(sub);

    const hpOuter = document.createElement("div");
    hpOuter.className = "hp-bar-outer";
    const hpInner = document.createElement("div");
    hpInner.className = "hp-bar-inner";
    hpInner.style.width = `${unit.maxHp > 0 ? Math.max(0, (unit.currentHp / unit.maxHp) * 100) : 0}%`;
    hpOuter.appendChild(hpInner);
    card.appendChild(hpOuter);

    const hpText = document.createElement("div");
    hpText.className = "hint";
    hpText.textContent = `${unit.currentHp} / ${unit.maxHp} HP`;
    card.appendChild(hpText);

    const attrText = document.createElement("div");
    attrText.className = "hint";
    attrText.textContent = `STR ${unit.strength} · AGI ${unit.agility} · INT ${unit.intelligence}`;
    card.appendChild(attrText);

    return card;
  }

  private renderGameOverModal(state: MatchUiState): HTMLElement {
    const backdrop = document.createElement("div");
    backdrop.className = "modal-backdrop";

    const panel = document.createElement("div");
    panel.className = "modal-panel game-over-panel";
    backdrop.appendChild(panel);

    const title = document.createElement("h2");
    const youWon = state.gameOver!.winnerTeam === state.yourTeam;
    title.textContent = youWon ? "Victory!" : "Defeat";
    panel.appendChild(title);

    const winnerText = document.createElement("div");
    winnerText.textContent = `Winner: ${state.gameOver!.winnerName}`;
    panel.appendChild(winnerText);

    const backBtn = document.createElement("button");
    backBtn.className = "primary";
    backBtn.style.marginTop = "16px";
    backBtn.textContent = "Return to lobby";
    backBtn.addEventListener("click", () => this.actions.exitToLobby());
    panel.appendChild(backBtn);

    return backdrop;
  }
}
