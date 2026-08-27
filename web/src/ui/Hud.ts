import type { GameStateStore, MatchUiState } from "../state/GameStateStore";
import type {
  AbilityPreviewSnapshot,
  AbilitySnapshot,
  Attribute,
  EffectSnapshot,
  PlacementUnitSnapshot,
  Team,
  UnitDefinitionSnapshot,
  UnitSnapshot,
} from "../types/contract";
import type { MatchActions } from "./MatchActions";
import { renderUnitPortrait } from "../units/UnitPortrait";

const ATTRIBUTES: Attribute[] = ["STRENGTH", "AGILITY", "INTELLIGENCE"];

/**
 * "Range 4", or "Range 7 (min 3)" while something like Steady Focus forbids close shots.
 * Range 1 is the default for most units, so it's still worth showing explicitly - it's
 * the difference between a melee unit and a ranged one at a glance.
 */
function formatRange(unit: UnitSnapshot): string {
  const min = unit.minAttackRange ?? 0;
  return min > 0 ? `Range ${unit.attackRange} (min ${min})` : `Range ${unit.attackRange}`;
}

// Large square portrait shown on each draft card (see API_CONTRACT.md /
// web/public/icons/README.md) - big enough to read clearly in a modal, still
// comfortably under the recommended 256x256 source art so nothing upscales.
const DRAFT_ICON_SIZE = 140;

// Mirrors com.walnutt.status.StatusFlag's blocksMovement()/blocksAttack()/
// blocksAbility() (see CLAUDE.md's "Status flags & stat modifiers" section) -
// kept in lockstep with that enum by hand, same as this file already mirrors
// API_CONTRACT.md's wire shapes. The server is still the actual authority
// (an illegal action just gets rejected with a "message" push either way);
// this is purely so the button already looks disabled instead of the player
// discovering the block by having their click bounce.
const BLOCKS_MOVEMENT = new Set(["STUNNED", "ROOTED", "FROZEN", "DUELING"]);
const BLOCKS_ATTACK = new Set(["STUNNED", "DISARMED", "FROZEN", "DUELING"]);
const BLOCKS_ABILITY = new Set(["STUNNED", "SILENCED", "FROZEN", "DUELING"]);

// Move/Attack always report maxCooldown 0 (they have no real cooldown concept -
// see CLAUDE.md's Cooldowns section) - "Cooldown: 0 turns" would read as
// confusing on every single hover of either, so 0 gets its own wording.
function cooldownLabel(cooldown: number): string {
  if (cooldown <= 0) return "No cooldown";
  return `Cooldown: ${cooldown} turn${cooldown === 1 ? "" : "s"}`;
}

export class Hud {
  private unsubscribe: () => void;
  private hudHost: HTMLElement;
  private matchId: string;
  private store: GameStateStore;
  private actions: MatchActions;
  // A single persistent tooltip element, appended to <body> once rather than
  // recreated inside render()'s hudHost.innerHTML = "" churn - render() wipes
  // and rebuilds the whole sidebar on every store update, which would tear
  // down a tooltip element living inside it mid-hover. Shared by every
  // hoverable thing in the HUD (effect chips, ability buttons/chips both
  // in-match and at draft time) - only one can ever be visible at once, so
  // one element is all that's needed.
  private tooltipEl: HTMLDivElement;

  constructor(hudHost: HTMLElement, matchId: string, store: GameStateStore, actions: MatchActions) {
    this.hudHost = hudHost;
    this.matchId = matchId;
    this.store = store;
    this.actions = actions;
    this.tooltipEl = document.createElement("div");
    this.tooltipEl.className = "hover-tooltip";
    document.body.appendChild(this.tooltipEl);
    this.unsubscribe = this.store.subscribe((state) => this.render(state));
  }

  destroy(): void {
    this.unsubscribe();
    this.hudHost.innerHTML = "";
    this.tooltipEl.remove();
  }

  private render(state: MatchUiState): void {
    this.hudHost.innerHTML = "";
    // The chip that was being hovered (if any) just got destroyed by the
    // innerHTML wipe above, so its mouseleave will never fire - hide
    // explicitly rather than leaving a stale tooltip stuck on screen.
    this.hideTooltip();

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
    this.hudHost.appendChild(this.renderCombatLog(state));
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
    if (!state.connected) {
      // GameSocket auto-reconnects with backoff on any unexpected close (see
      // its own comment for why) - this is the ongoing visual indicator
      // while that's in flight; MatchScreen's message log gets a one-time
      // "attempting to reconnect" line for the initial heads-up.
      const reconnectBadge = document.createElement("span");
      reconnectBadge.className = "badge turn-theirs";
      reconnectBadge.style.marginLeft = "6px";
      reconnectBadge.textContent = "Reconnecting…";
      badges.appendChild(reconnectBadge);
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
      section.appendChild(this.renderEndTurnButton(state, isYourTurn));
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
    attrText.textContent = `STR ${unit.strength} · AGI ${unit.agility} · INT ${unit.intelligence}` + ` · ${formatRange(unit)}`;
    section.appendChild(attrText);

    if (unit.effects.length > 0) {
      const effectsRow = document.createElement("div");
      effectsRow.className = "effects-list";
      for (const effect of unit.effects) {
        effectsRow.appendChild(this.renderEffectChip(effect));
      }
      section.appendChild(effectsRow);
    }

    const canAct = unit.team === state.yourTeam && isYourTurn && state.prompt?.kind !== "attribute";
    const abilityList = document.createElement("div");
    abilityList.className = "ability-list";
    for (const ability of unit.abilities) {
      abilityList.appendChild(this.renderAbilityButton(unit, ability, state, canAct));
    }
    section.appendChild(abilityList);

    if (state.selectedAbilityId && unit.id === state.selectedUnitId) {
      const targetHint = document.createElement("div");
      targetHint.className = "hint";
      targetHint.style.marginTop = "8px";
      targetHint.textContent = "Click a unit or tile on the board to target this ability.";
      // Self-casts fire the moment they are selected (see MatchScreen's
      // castImmediatelyIfSelfTargeted), so anything still showing this panel is
      // genuinely waiting on a target.
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

    section.appendChild(this.renderEndTurnButton(state, isYourTurn));

    return section;
  }

  /**
   * Persistent regardless of selection (see CLAUDE.md's frontend fixes) -
   * greyed out via `disabled` rather than only appearing once a unit of
   * yours is selected, so it's always visible where a player expects it.
   */
  private renderEndTurnButton(state: MatchUiState, isYourTurn: boolean): HTMLElement {
    const btn = document.createElement("button");
    btn.className = "primary";
    btn.style.marginTop = "10px";
    btn.style.width = "100%";
    btn.textContent = "End Turn";
    btn.disabled = !isYourTurn || state.prompt?.kind === "attribute" || !!state.gameOver;
    btn.addEventListener("click", () => this.actions.endTurn());
    return btn;
  }

  /**
   * Move/Attack/ability cost and blocked-by-status-flag rules mirror the
   * engine exactly (see CLAUDE.md's Core game rules + Status flags sections):
   * BASIC units move AND attack for free, everything else pays 1 per move or
   * attack, and any other active ability costs 1 - no ability in the engine
   * overrides that default. A unit can also only move/attack once per turn
   * regardless of move points remaining (hasMovedThisTurn/hasAttackedThisTurn),
   * independent of cooldown-based `ready`, so "free" means "costs no move
   * point", not "unlimited".
   */
  private renderAbilityButton(
    unit: UnitSnapshot,
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

    let disabled = !canAct || ability.passive || !ability.ready;
    let reason: string | null = null;
    if (!disabled) {
      const kind = ability.id === "move" ? "move" : ability.id === "attack" ? "attack" : "ability";
      const blockedBy = (kind === "move" ? BLOCKS_MOVEMENT : kind === "attack" ? BLOCKS_ATTACK : BLOCKS_ABILITY);
      const blockingFlag = unit.statusFlags.find((flag) => blockedBy.has(flag));
      if (blockingFlag) {
        disabled = true;
        reason = blockingFlag.charAt(0) + blockingFlag.slice(1).toLowerCase();
      } else if (kind === "move" && unit.hasMovedThisTurn) {
        disabled = true;
        reason = "Already moved this turn";
      } else if (kind === "attack" && unit.hasAttackedThisTurn) {
        disabled = true;
        reason = "Already attacked this turn";
      } else {
        const isFreeBasicAction =
          unit.unitType === "BASIC" && (kind === "attack" || kind === "move");
        const cost = isFreeBasicAction ? 0 : 1;
        const remainingMoves = state.snapshot?.remainingMoves ?? 0;
        if (remainingMoves < cost) {
          disabled = true;
          reason = "No moves remaining";
        }
      }
    }

    btn.disabled = disabled;
    this.attachTooltip(btn, (e) =>
      this.showAbilityTooltip(ability.name, ability.description, ability.passive, cooldownLabel(ability.maxCooldown), e, reason),
    );
    btn.addEventListener("click", () => {
      this.actions.selectUnit(unit.id);
      this.actions.selectAbility(ability.id);
    });
    return btn;
  }

  /**
   * One effect row for the sidebar unit panel - real effect name (not the
   * raw StatusFlag names the board used to render), with a custom hover
   * tooltip (not a native `title` - inconsistent cross-browser newline
   * rendering, no styling, slow to appear) carrying the description, turns
   * remaining, dynamic per-instance state (extraInfo, e.g. "Next hit: 12
   * damage"), and this effect's own concrete StatusFlags - see
   * API_CONTRACT.md's "Effects sidebar" section for the wording guidance.
   */
  private renderEffectChip(effect: EffectSnapshot): HTMLElement {
    const chip = document.createElement("span");
    chip.className = `effect-chip effect-${effect.category.toLowerCase()}`;
    chip.textContent = effect.name;

    this.attachTooltip(chip, (e) => this.showEffectTooltip(effect, e));

    return chip;
  }

  /** Wires the shared hover tooltip onto any element - shows on enter, tracks the cursor, hides on leave. */
  private attachTooltip(el: HTMLElement, show: (event: MouseEvent) => void): void {
    el.addEventListener("mouseenter", (e) => show(e as MouseEvent));
    el.addEventListener("mousemove", (e) => this.positionTooltip(e as MouseEvent));
    el.addEventListener("mouseleave", () => this.hideTooltip());
  }

  private showEffectTooltip(effect: EffectSnapshot, event: MouseEvent): void {
    this.tooltipEl.innerHTML = "";

    const title = document.createElement("div");
    title.className = "hover-tooltip-title";
    title.textContent = effect.name;
    this.tooltipEl.appendChild(title);

    const desc = document.createElement("div");
    desc.textContent = effect.description;
    this.tooltipEl.appendChild(desc);

    const duration = document.createElement("div");
    duration.className = "hover-tooltip-meta";
    duration.textContent = effect.permanent
      ? "Permanent"
      : `${effect.remainingTurns} turn${effect.remainingTurns === 1 ? "" : "s"} remaining`;
    this.tooltipEl.appendChild(duration);

    if (effect.extraInfo) {
      const extra = document.createElement("div");
      extra.className = "hover-tooltip-meta";
      extra.textContent = effect.extraInfo;
      this.tooltipEl.appendChild(extra);
    }

    if (effect.statusFlags.length > 0) {
      const flags = document.createElement("div");
      flags.className = "hover-tooltip-meta";
      flags.textContent = `Flags: ${effect.statusFlags.join(", ")}`;
      this.tooltipEl.appendChild(flags);
    }

    this.tooltipEl.classList.add("visible");
    this.positionTooltip(event);
  }

  /**
   * Shared by both draft-card ability chips (AbilityPreviewSnapshot, no live
   * match state) and in-match ability buttons (AbilitySnapshot) - see
   * API_CONTRACT.md's "Ability tooltips" section. `cooldownLabel` is
   * precomputed by the caller since the two snapshot shapes name the cooldown
   * field differently (`cooldown` vs `maxCooldown`) and only one of them also
   * carries live current-cooldown state. `disabledReason`, when given (only
   * ever by an in-match button - draft chips are never "disabled"), replaces
   * the native `title` attribute this button would otherwise need, so there's
   * only ever one tooltip competing for the hover instead of two.
   */
  private showAbilityTooltip(
    name: string,
    description: string,
    passive: boolean,
    cooldownLabel: string,
    event: MouseEvent,
    disabledReason?: string | null,
  ): void {
    this.tooltipEl.innerHTML = "";

    const title = document.createElement("div");
    title.className = "hover-tooltip-title";
    title.textContent = name;
    this.tooltipEl.appendChild(title);

    const desc = document.createElement("div");
    desc.textContent = description;
    this.tooltipEl.appendChild(desc);

    const meta = document.createElement("div");
    meta.className = "hover-tooltip-meta";
    meta.textContent = passive ? "Passive" : cooldownLabel;
    this.tooltipEl.appendChild(meta);

    if (disabledReason) {
      const reasonEl = document.createElement("div");
      reasonEl.className = "hover-tooltip-meta hover-tooltip-reason";
      reasonEl.textContent = disabledReason;
      this.tooltipEl.appendChild(reasonEl);
    }

    this.tooltipEl.classList.add("visible");
    this.positionTooltip(event);
  }

  private positionTooltip(event: MouseEvent): void {
    const offset = 14;
    this.tooltipEl.style.left = `${event.clientX + offset}px`;
    this.tooltipEl.style.top = `${event.clientY + offset}px`;
  }

  private hideTooltip(): void {
    this.tooltipEl.classList.remove("visible");
  }

  private renderCombatLog(state: MatchUiState): HTMLElement {
    const section = document.createElement("div");
    section.className = "hud-section combat-log-section";

    const header = document.createElement("div");
    header.className = "combat-log-header";
    const h3 = document.createElement("h3");
    h3.textContent = "Combat log";
    header.appendChild(h3);

    // One page per full round (Player One's turn through the end of Player
    // Two's turn) - see GameStateStore.startNewCombatLogPageIfRoundJustCompleted.
    const pageCount = state.combatLogPages.length;
    const pager = document.createElement("div");
    pager.className = "combat-log-pager";

    const prevBtn = document.createElement("button");
    prevBtn.textContent = "‹";
    prevBtn.disabled = state.viewedLogPage <= 0;
    prevBtn.addEventListener("click", () => this.store.viewCombatLogPage(-1));
    pager.appendChild(prevBtn);

    const pageLabel = document.createElement("span");
    pageLabel.className = "hint";
    pageLabel.textContent = `Turn ${state.viewedLogPage + 1} / ${pageCount}`;
    pager.appendChild(pageLabel);

    const nextBtn = document.createElement("button");
    nextBtn.textContent = "›";
    nextBtn.disabled = state.viewedLogPage >= pageCount - 1;
    nextBtn.addEventListener("click", () => this.store.viewCombatLogPage(1));
    pager.appendChild(nextBtn);

    header.appendChild(pager);
    section.appendChild(header);

    const log = document.createElement("div");
    log.className = "combat-log";
    const pageLines = state.combatLogPages[state.viewedLogPage] ?? [];
    if (pageLines.length === 0) {
      const empty = document.createElement("div");
      empty.className = "hint";
      empty.textContent = "No damage dealt yet.";
      log.appendChild(empty);
    } else {
      for (const entry of pageLines) {
        const line = document.createElement("div");
        line.textContent = entry;
        log.appendChild(line);
      }
    }
    // Only auto-scroll to the bottom when looking at the latest page - an
    // older page the user deliberately navigated back to shouldn't jump.
    if (state.viewedLogPage === pageCount - 1) {
      log.scrollTop = log.scrollHeight;
    }
    section.appendChild(log);

    return section;
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

    // No real art dropped in yet for any unit (see web/public/icons/README.md) -
    // the large initial-letter badge is the deliberate placeholder here, same
    // fallback UnitIconFactory/renderUnitPortrait already use elsewhere, just
    // bigger and square instead of the board's small circular sprite. No
    // `team` yet either - these are still-undrafted candidates, not owned by
    // anyone, so the badge uses a neutral background instead of guessing one.
    card.appendChild(
      renderUnitPortrait({ definitionId: def.definitionId, unitType: def.type, name: def.name }, DRAFT_ICON_SIZE, "square"),
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
    stats.textContent = `HP ${def.maxHp} · STR ${def.strength} · AGI ${def.agility} · INT ${def.intelligence} · Range ${def.attackRange}`;
    card.appendChild(stats);

    const abilities = document.createElement("div");
    abilities.className = "abilities";
    for (const ability of def.abilities) {
      abilities.appendChild(this.renderAbilityPreviewChip(ability));
    }
    card.appendChild(abilities);

    if (clickable) {
      card.addEventListener("click", () => this.actions.sendPick(def.definitionId));
    }
    return card;
  }

  /** Draft-time counterpart to renderAbilityButton's tooltip wiring - same hover content, no live cooldown/click-to-select. */
  private renderAbilityPreviewChip(ability: AbilityPreviewSnapshot): HTMLElement {
    const chip = document.createElement("span");
    chip.className = "ability-chip";
    chip.textContent = `${ability.name}${ability.passive ? " (passive)" : ""}`;

    this.attachTooltip(chip, (e) =>
      this.showAbilityTooltip(ability.name, ability.description, ability.passive, cooldownLabel(ability.cooldown), e),
    );

    return chip;
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
    title.textContent = state.attributeSubmitted ? "Attribute chosen" : "Choose an attribute";
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

    // Once this client has sent its own attribute pick, keep the encounter
    // card up but swap the pick buttons for a waiting message - the other
    // side may not have answered yet, and there's no separate server signal
    // for that beyond the absence of a superseding message (see
    // API_CONTRACT.md's "Waiting for other player" state paragraph).
    if (state.attributeSubmitted) {
      const waiting = document.createElement("div");
      waiting.className = "waiting-banner attribute-waiting-banner";
      waiting.textContent = "Waiting for other player…";
      panel.appendChild(waiting);
      return backdrop;
    }

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
    attrText.textContent = `STR ${unit.strength} · AGI ${unit.agility} · INT ${unit.intelligence}` + ` · ${formatRange(unit)}`;
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
