import type { GameStateStore, MatchUiState } from "../state/GameStateStore";
import type {
  AbilitySnapshot,
  Attribute,
  ChoiceOption,
  EffectSnapshot,
  PlacementUnitSnapshot,
  Team,
  UnitDefinitionSnapshot,
  UnitSnapshot,
} from "../types/contract";
import type { MatchActions } from "./MatchActions";
import { renderUnitFullBody } from "../units/UnitPortrait";
import { hpSeparatorThresholds } from "../units/UnitHp";
import { abilityTooltip, Tooltip } from "./Tooltip";
import { renderUnitCard } from "./UnitCard";
import { teamCssColor } from "./Colors";
import type { CombatLogEntry, CombatLogSegment } from "../state/CombatLog";
import { audioManager } from "../audio/AudioManager";
import { actionForHotkeyCode, getHotkey, setHotkey, hotkeyLabel, type HotkeyAction } from "../app/AppSettings";
import { isTextEntry } from "../board/CameraController";
import { volumeRow, hotkeyRow } from "./SettingsRows";

const ATTRIBUTES: Attribute[] = ["STRENGTH", "AGILITY", "INTELLIGENCE"];

const HOTKEY_ROWS: ReadonlyArray<{ action: HotkeyAction; label: string }> = [
  { action: "move", label: "Move" },
  { action: "attack", label: "Attack" },
  { action: "ability1", label: "Ability 1" },
  { action: "ability2", label: "Ability 2" },
  { action: "ability3", label: "Ability 3" },
  { action: "cancel", label: "Deselect / cancel" },
];

/** Which log the left column shows; null means collapsed to just the tab rail. */
type LogTab = "combat" | "system" | "settings" | null;

/** Combat-log halves, top to bottom. */
const TEAMS: Team[] = ["PLAYER_ONE", "PLAYER_TWO"];

/**
 * One combat-log line. Segments carry their own colour and weight so a unit
 * name reads in its team colour and a damage number stands out bold - see
 * CombatLog.buildCombatLogLines, which decides both.
 */
function renderLogLine(segments: CombatLogSegment[]): HTMLElement {
  const line = document.createElement("div");
  for (const segment of segments) {
    const span = document.createElement("span");
    span.textContent = segment.text;
    if (segment.color) span.style.color = segment.color;
    if (segment.bold) span.style.fontWeight = "700";
    line.appendChild(span);
  }
  return line;
}

/**
 * "Range 4", or "Range 7 (min 3)" while something like Steady Focus forbids close shots.
 * Range 1 is the default for most units, so it's still worth showing explicitly - it's
 * the difference between a melee unit and a ranged one at a glance.
 */
function formatRange(unit: UnitSnapshot): string {
  const min = unit.minAttackRange ?? 0;
  return min > 0 ? `Range ${unit.attackRange} (min ${min})` : `Range ${unit.attackRange}`;
}

/** "420 / 500 HP", or "420 (+10) / 500 HP" when the unit currently has any barrier. */
function formatHpText(unit: UnitSnapshot): string {
  const barrier = unit.currentBarrierHp > 0 ? ` (+${unit.currentBarrierHp})` : "";
  return `${unit.currentHp}${barrier} / ${unit.maxHp} HP`;
}

/**
 * Builds the .hp-bar-outer element (fill + darker-green separator ticks at
 * every 100 maxHp) shared by the sidebar unit panel and the encounter card.
 */
function renderHpBar(unit: UnitSnapshot): HTMLElement {
  const hpOuter = document.createElement("div");
  hpOuter.className = "hp-bar-outer";
  const hpInner = document.createElement("div");
  hpInner.className = "hp-bar-inner";
  hpInner.style.width = `${unit.maxHp > 0 ? Math.max(0, (unit.currentHp / unit.maxHp) * 100) : 0}%`;
  hpOuter.appendChild(hpInner);
  for (const threshold of hpSeparatorThresholds(unit.maxHp)) {
    if (unit.currentHp <= threshold) continue;
    const tick = document.createElement("div");
    tick.className = "hp-bar-separator";
    tick.style.left = `${(threshold / unit.maxHp) * 100}%`;
    hpOuter.appendChild(tick);
  }
  return hpOuter;
}

/**
 * Builds the .barrier-bar-outer element (translucent white fill + solid white separator
 * ticks at every 100 maxBarrierHp), or null if the unit currently has no barrier.
 */
function renderBarrierBar(unit: UnitSnapshot): HTMLElement | null {
  if (unit.maxBarrierHp <= 0) return null;
  const barrierOuter = document.createElement("div");
  barrierOuter.className = "barrier-bar-outer";
  const barrierInner = document.createElement("div");
  barrierInner.className = "barrier-bar-inner";
  barrierInner.style.width = `${Math.max(0, (unit.currentBarrierHp / unit.maxBarrierHp) * 100)}%`;
  barrierOuter.appendChild(barrierInner);
  for (const threshold of hpSeparatorThresholds(unit.maxBarrierHp)) {
    if (unit.currentBarrierHp <= threshold) continue;
    const tick = document.createElement("div");
    tick.className = "barrier-bar-separator";
    tick.style.left = `${(threshold / unit.maxBarrierHp) * 100}%`;
    barrierOuter.appendChild(tick);
  }
  return barrierOuter;
}

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

export class Hud {
  private unsubscribe: () => void;
  private hudHost: HTMLElement;
  // The logs live in their own column on the far side of the board, so the right
  // sidebar is only ever "the unit you have selected, and what it can do".
  private logHost: HTMLElement;
  private matchId: string;
  private store: GameStateStore;
  private actions: MatchActions;
  // Which log the left column shows, or null for collapsed. Lives here rather
  // than in the store because it's view state, not game state - and render()
  // rebuilds logHost from scratch on every store update, so it has to survive
  // outside the DOM either way.
  private logTab: LogTab = "combat";
  // How many system messages had been seen last time the System tab was open,
  // so a message arriving while it's hidden can raise an unread dot.
  private lastSeenMessageCount = 0;
  // One tooltip for the whole HUD - see Tooltip, which explains why it cannot live
  // inside the subtree render() wipes on every store update.
  private tooltip: Tooltip;

  constructor(
    hudHost: HTMLElement,
    logHost: HTMLElement,
    matchId: string,
    store: GameStateStore,
    actions: MatchActions,
  ) {
    this.hudHost = hudHost;
    this.logHost = logHost;
    this.matchId = matchId;
    this.store = store;
    this.actions = actions;
    this.tooltip = new Tooltip();
    this.unsubscribe = this.store.subscribe((state) => this.render(state));
    window.addEventListener("keydown", this.onKeyDown);
  }

  destroy(): void {
    window.removeEventListener("keydown", this.onKeyDown);
    this.unsubscribe();
    this.hudHost.innerHTML = "";
    this.logHost.innerHTML = "";
    this.tooltip.destroy();
  }

  /**
   * Gameplay hotkeys: forwards to the same button a click would hit, so there is exactly
   * one place (renderAbilityButton) that decides whether an ability is actually usable right
   * now. Move/Attack/Ability1-3 naturally no-op whenever the corresponding button isn't
   * rendered at all (no unit selected, mid-placement/draft/game-over) - no extra phase
   * checks needed, the DOM is already the source of truth.
   */
  private onKeyDown = (e: KeyboardEvent): void => {
    if (e.ctrlKey || e.metaKey || e.altKey) return;
    if (isTextEntry(document.activeElement)) return;
    const action = actionForHotkeyCode(e.code);
    if (!action) return;

    if (action === "cancel") {
      const state = this.store.getState();
      if (state.selectedAbilityId) {
        this.actions.selectAbility(null);
      } else if (state.selectedUnitId) {
        this.actions.selectUnit(null);
      } else {
        return;
      }
      e.preventDefault();
      return;
    }

    const btn = this.hudHost.querySelector<HTMLButtonElement>(`[data-hotkey-slot="${action}"]`);
    if (btn && !btn.disabled) {
      btn.click();
      e.preventDefault();
    }
  };

  private render(state: MatchUiState): void {
    this.hudHost.innerHTML = "";
    this.logHost.innerHTML = "";
    // The chip that was being hovered (if any) just got destroyed by the
    // innerHTML wipe above, so its mouseleave will never fire - hide
    // explicitly rather than leaving a stale tooltip stuck on screen.
    this.tooltip.hide();

    // Everything above End Turn scrolls as one block; End Turn itself is a
    // sibling of the scroller, not part of it, which is what keeps it in the
    // same place no matter how many abilities the selected unit happens to have.
    const body = document.createElement("div");
    body.className = "hud-scroll";
    this.hudHost.appendChild(body);

    body.appendChild(this.renderTopBar(state));

    const isYourTurn = state.snapshot?.currentTeam === state.yourTeam;
    if (state.snapshot && !isYourTurn && !state.gameOver) {
      const banner = document.createElement("div");
      banner.className = "hud-section";
      const inner = document.createElement("div");
      inner.className = "waiting-banner";
      inner.textContent = "Waiting for opponent...";
      banner.appendChild(inner);
      body.appendChild(banner);
    }

    if (state.placementState) {
      body.appendChild(this.renderPlacementPanel(state));
    } else {
      body.appendChild(this.renderUnitPanel(state, isYourTurn));
    }

    // Not during placement: that phase has no turn to end, and its panel carries
    // its own "Confirm placement" action.
    if (!state.placementState) {
      const footer = document.createElement("div");
      footer.className = "hud-footer";
      footer.appendChild(this.renderEndTurnButton(state, isYourTurn));
      this.hudHost.appendChild(footer);
    }

    this.renderLogColumn(state);

    // Modal overlays, highest priority first.
    if (state.gameOver) {
      this.hudHost.appendChild(this.renderGameOverModal(state));
    } else if (state.draftRound) {
      // Receiving a draft_round message doubles as the prompt to pick - see
      // API_CONTRACT.md, there's no separate "pick" prompt kind anymore.
      this.hudHost.appendChild(this.renderDraftModal(state));
    } else if (state.prompt?.kind === "choice") {
      this.hudHost.appendChild(this.renderChoiceModal(state.prompt));
    } else if (state.prompt?.kind === "attribute") {
      this.hudHost.appendChild(this.renderAttributeModal(state));
    }
  }

  /**
   * The generic option dialogue (Maxwell's Eureka picking a gadget). Everything shown
   * comes from the prompt itself - name, description, detail - so this stays a single
   * renderer no matter which ability raised it, and a future ability needs no new UI.
   */
  private renderChoiceModal(prompt: { title: string; unitId: string; options: ChoiceOption[] }): HTMLElement {
    const backdrop = document.createElement("div");
    backdrop.className = "modal-backdrop";

    const panel = document.createElement("div");
    panel.className = "modal-panel choice-modal-panel";
    backdrop.appendChild(panel);

    const title = document.createElement("h2");
    title.textContent = prompt.title;
    panel.appendChild(title);

    const caster = this.store.findUnit(prompt.unitId);
    if (caster) {
      const sub = document.createElement("div");
      sub.className = "hint";
      sub.textContent = caster.name;
      panel.appendChild(sub);
    }

    const cards = document.createElement("div");
    cards.className = "choice-cards";
    for (const option of prompt.options) {
      cards.appendChild(this.renderChoiceCard(option));
    }
    panel.appendChild(cards);

    // Always offered, whatever is on the table - a dialogue with nothing takeable in it
    // (an ally with no unlockable abilities) would otherwise be a dead end with the match
    // waiting on this player. Cancelling costs nothing: the server spends neither the
    // resource nor the cooldown until a real choice comes back.
    const cancel = document.createElement("button");
    cancel.className = "choice-cancel";
    cancel.textContent = "Cancel";
    cancel.addEventListener("click", () => this.actions.cancelChoice());
    panel.appendChild(cancel);

    return backdrop;
  }

  private renderChoiceCard(option: ChoiceOption): HTMLElement {
    const card = document.createElement("div");
    card.className = "choice-card";
    // Shown but not takeable - the card stays so the reason below is visible, which is the
    // entire point of listing an ally's already-unlocked abilities rather than omitting them.
    if (!option.enabled) card.classList.add("choice-card-disabled");

    const name = document.createElement("h5");
    name.textContent = option.name;
    card.appendChild(name);

    if (option.detail) {
      const detail = document.createElement("div");
      detail.className = "hint";
      detail.textContent = option.detail;
      card.appendChild(detail);
    }

    const description = document.createElement("div");
    description.className = "choice-card-description";
    description.textContent = option.description;
    card.appendChild(description);

    if (option.enabled) {
      card.addEventListener("click", () => this.actions.sendChoice(option.id));
    }
    return card;
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
    if (state.isSpectator) {
      teamBadge.className = "badge";
      teamBadge.textContent = "Spectating";
    } else {
      teamBadge.className = `badge ${state.yourTeam === "PLAYER_ONE" ? "team-one" : "team-two"}`;
      teamBadge.textContent = state.yourTeam === "PLAYER_ONE" ? "Player One" : "Player Two";
    }
    badges.appendChild(teamBadge);

    if (state.snapshot) {
      const turnBadge = document.createElement("span");
      turnBadge.style.marginLeft = "6px";
      if (state.isSpectator) {
        turnBadge.className = "badge turn-theirs";
        turnBadge.textContent = state.snapshot.currentTeam === "PLAYER_ONE" ? "Player One's turn" : "Player Two's turn";
      } else {
        const isYourTurn = state.snapshot.currentTeam === state.yourTeam;
        turnBadge.className = `badge ${isYourTurn ? "turn-yours" : "turn-theirs"}`;
        turnBadge.textContent = isYourTurn ? "Your turn" : "Opponent's turn";
      }
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
    leaveBtn.textContent = state.isSpectator ? "Stop spectating" : "Leave";
    leaveBtn.addEventListener("click", () => this.actions.exitToLobby());
    section.appendChild(leaveBtn);

    return section;
  }

  private renderUnitPanel(state: MatchUiState, isYourTurn: boolean): HTMLElement {
    const section = document.createElement("div");
    section.className = "hud-section unit-panel";
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
    const ownershipLabel = state.isSpectator
      ? (unit.team === "PLAYER_ONE" ? "Player One" : "Player Two")
      : (unit.team === state.yourTeam ? "Yours" : "Enemy");
    sub.textContent = `${unit.unitType} · ${ownershipLabel}${unit.dead ? " · Dead" : ""}`;
    section.appendChild(sub);

    const barrierBar = renderBarrierBar(unit);
    if (barrierBar) section.appendChild(barrierBar);
    section.appendChild(renderHpBar(unit));

    const hpText = document.createElement("div");
    hpText.className = "hint";
    hpText.style.marginBottom = "8px";
    hpText.textContent = formatHpText(unit);
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

    // Between what the unit *is* (name, statline, effects) and what it can *do*
    // below. Facing follows the board's rule rather than a slot's - there is no
    // opponent alongside it here, so a unit should simply look the way it looks
    // on the map.
    section.appendChild(
      renderUnitFullBody({
        definitionId: unit.definitionId,
        team: unit.team,
        unitType: unit.unitType,
        name: unit.name,
      }),
    );

    const canAct = unit.team === state.yourTeam && isYourTurn && state.prompt?.kind !== "attribute";
    const abilityList = document.createElement("div");
    abilityList.className = "ability-list";
    // Move/Attack are just two entries in this same array (see the id check
    // below); Ability 1/2/3 hotkeys bind to the first three *other* entries by
    // position, whether or not they're passive - a leading passive still
    // consumes a slot, matching the settings screen's numbering.
    let nextAbilitySlot = 0;
    for (const ability of unit.abilities) {
      let slot: HotkeyAction | null;
      if (ability.id === "move") slot = "move";
      else if (ability.id === "attack") slot = "attack";
      else if (nextAbilitySlot < 3) slot = (["ability1", "ability2", "ability3"] as const)[nextAbilitySlot++];
      else {
        nextAbilitySlot++;
        slot = null;
      }
      abilityList.appendChild(this.renderAbilityButton(unit, ability, state, canAct, slot));
    }
    section.appendChild(abilityList);

    if (state.selectedAbilityId && unit.id === state.selectedUnitId) {
      const targetHint = document.createElement("div");
      targetHint.className = "hint";
      targetHint.style.marginTop = "8px";
      const multi =
        state.prompt?.kind === "action"
          ? state.prompt.legalTargets?.[state.selectedUnitId]?.[state.selectedAbilityId]?.multi
          : undefined;
      // A two-part cast says which half is being asked for, and how to back out. Without it
      // the first of two clicks looks like a cast that did nothing.
      const picksTiles = !!multi && (multi.primaryTiles ?? []).length > 0;
      targetHint.textContent = !multi
        ? "Click a unit or tile on the board to target this ability."
        : picksTiles
          ? state.multiPrimaryTile
            ? "Now click the second tile, or click the first again to change it."
            : "Click the first of two tiles."
          : state.multiPrimaryUnitId
            ? "Now click where to put it, or click it again to pick someone else."
            : "Click the unit you want to move.";
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

    return section;
  }

  /**
   * Persistent regardless of selection (see CLAUDE.md's frontend fixes) -
   * greyed out via `disabled` rather than only appearing once a unit of
   * yours is selected, so it's always visible where a player expects it.
   *
   * It lives in the sidebar's pinned footer rather than at the end of the
   * selected-unit panel: hung off the ability list it slid up and down as units
   * with different numbers of abilities were selected, which is a bad thing for
   * the one button a player reaches for every single turn.
   */
  private renderEndTurnButton(state: MatchUiState, isYourTurn: boolean): HTMLElement {
    const btn = document.createElement("button");
    btn.className = "primary end-turn-btn";
    btn.style.width = "100%";
    btn.textContent = "End Turn";
    btn.disabled = !isYourTurn || state.prompt?.kind === "attribute" || !!state.gameOver;
    btn.addEventListener("click", () => this.actions.endTurn());
    return btn;
  }

  /**
   * Blocked-by-status-flag rules mirror the engine (see CLAUDE.md's Status flags
   * section). The action COST no longer does: it arrives as `ability.moveCost`,
   * computed by Ability.getMoveCost, because the rule stopped being derivable
   * from the snapshot alone once Maxwell's Capacitor Bank could pay for a cast.
   * A unit can still only move/attack once per turn regardless of move points
   * remaining (hasMovedThisTurn/hasAttackedThisTurn), independent of both
   * cooldown-based `ready` and cost, so "free" means "costs no move point",
   * not "unlimited".
   */
  private renderAbilityButton(
    unit: UnitSnapshot,
    ability: AbilitySnapshot,
    state: MatchUiState,
    canAct: boolean,
    slot: HotkeyAction | null,
  ): HTMLElement {
    const btn = document.createElement("button");
    btn.className = "ability-btn";
    if (state.selectedAbilityId === ability.id) btn.classList.add("selected");
    if (ability.upgraded) btn.classList.add("upgraded");
    // Stable selector for the tutorial's pointer-arrow overlay (see TutorialArrows.ts),
    // since slot-based data-hotkey-slot only covers move/attack/ability1-3 positionally.
    btn.dataset.abilityId = ability.id;
    // Set regardless of passive/disabled - onKeyDown forwards a click here, and a
    // disabled button already no-ops that click, which is exactly the desired
    // "hotkey does nothing on a passive ability" behaviour with no extra check.
    if (slot) btn.dataset.hotkeySlot = slot;

    const label = document.createElement("span");
    label.textContent = `${ability.name}${ability.passive ? " (passive)" : ""}`;
    btn.appendChild(label);

    const right = document.createElement("span");
    right.className = "ability-btn-right";
    if (!ability.ready) {
      const cd = document.createElement("span");
      cd.className = "ability-cd";
      cd.textContent = `CD ${ability.currentCooldown}/${ability.maxCooldown}`;
      right.appendChild(cd);
    }
    // No visible hotkey on a passive ability - it never does anything, even
    // though it still holds its numbered slot (see renderUnitPanel).
    if (slot && !ability.passive) {
      const hotkey = document.createElement("span");
      hotkey.className = "ability-hotkey";
      hotkey.textContent = hotkeyLabel(getHotkey(slot));
      right.appendChild(hotkey);
    }
    btn.appendChild(right);

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
      } else if (ability.usedThisTurn) {
        // Server-decided, not re-derived here: the engine already refused it via
        // Ability.canUse, so this only explains a button that would otherwise look
        // castable (its cooldown can genuinely read 0).
        disabled = true;
        reason = "Already used this turn";
      } else {
        // Server-computed (Ability.getMoveCost), not re-derived here: it already accounts
        // for a BASIC unit's free move/attack AND for a Maxwell charge paying the cost,
        // neither of which this side could know on its own.
        const remainingMoves = state.snapshot?.remainingMoves ?? 0;
        if (remainingMoves < ability.moveCost) {
          disabled = true;
          reason = "No moves remaining";
        }
      }
    }

    btn.disabled = disabled;
    this.tooltip.attachAbility(btn, { ...abilityTooltip(ability), disabledReason: reason });
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

    this.tooltip.attachEffect(chip, effect);

    return chip;
  }

  /**
   * The left column: the tab rail, then one log panel (or none, when collapsed).
   *
   * The rail comes first in the DOM so it pins to the screen's outer edge and
   * stays put as the panel opens and closes beside it. It is always rendered,
   * even when collapsed - it is the only way back.
   */
  private renderLogColumn(state: MatchUiState): void {
    // Looking at the System tab *is* seeing its messages.
    if (this.logTab === "system") this.lastSeenMessageCount = state.messages.length;

    this.logHost.appendChild(this.renderLogTabs(state));

    if (this.logTab !== null) {
      const panel = document.createElement("div");
      panel.className = "log-panel";
      panel.appendChild(
        this.logTab === "combat" ? this.renderCombatLog(state)
          : this.logTab === "system" ? this.renderMessageLog(state)
          : this.renderSettingsPanel(),
      );
      this.logHost.appendChild(panel);
    }
  }

  private renderLogTabs(state: MatchUiState): HTMLElement {
    const rail = document.createElement("div");
    rail.className = "log-tabs";
    rail.appendChild(this.renderLogTab("combat", "Combat", false));
    rail.appendChild(
      this.renderLogTab("system", "System", state.messages.length > this.lastSeenMessageCount),
    );
    rail.appendChild(this.renderLogTab("settings", "Settings", false));
    return rail;
  }

  /**
   * Lets a player adjust audio and hotkeys without leaving the match. Deliberately a
   * subset of the lobby SettingsScreen: favourite unit is an account-level draft
   * preference (meaningless mid-match) and Fast Transitions only affects menu
   * navigation, which doesn't exist once a match has started.
   */
  private renderSettingsPanel(): HTMLElement {
    const section = document.createElement("div");
    section.className = "hud-section";

    const h3 = document.createElement("h3");
    h3.textContent = "Settings";
    section.appendChild(h3);

    section.appendChild(
      volumeRow("Music volume", audioManager.getMusicVolume(), (v) => audioManager.setMusicVolume(v)),
    );
    section.appendChild(
      volumeRow("SFX volume", audioManager.getSfxVolume(), (v) => audioManager.setSfxVolume(v)),
    );

    const hotkeysHeading = document.createElement("h3");
    hotkeysHeading.style.marginTop = "8px";
    hotkeysHeading.textContent = "Hotkeys";
    section.appendChild(hotkeysHeading);
    for (const { action, label } of HOTKEY_ROWS) {
      section.appendChild(
        hotkeyRow(label, getHotkey(action), (code) => {
          setHotkey(action, code);
          this.render(this.store.getState());
        }),
      );
    }

    return section;
  }

  private renderLogTab(tab: LogTab, label: string, unread: boolean): HTMLElement {
    const btn = document.createElement("button");
    btn.className = "log-tab";
    // Same "active tab carries .primary" idiom the codex filters use.
    if (this.logTab === tab) btn.classList.add("primary");
    btn.textContent = label;
    if (unread) {
      const dot = document.createElement("span");
      dot.className = "log-tab-dot";
      btn.appendChild(dot);
    }
    btn.addEventListener("click", () => {
      // Clicking the tab you're already on closes the panel entirely.
      this.logTab = this.logTab === tab ? null : tab;
      this.render(this.store.getState());
    });
    return btn;
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
    // Two's turn) - see GameStateStore.commitCombatLog.
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
    // "Round", not "Turn": a page has always spanned both players' turns, and
    // now that the body splits them apart the distinction is visible.
    pageLabel.textContent = `Round ${state.viewedLogPage + 1} / ${pageCount}`;
    pager.appendChild(pageLabel);

    const nextBtn = document.createElement("button");
    nextBtn.textContent = "›";
    nextBtn.disabled = state.viewedLogPage >= pageCount - 1;
    nextBtn.addEventListener("click", () => this.store.viewCombatLogPage(1));
    pager.appendChild(nextBtn);

    header.appendChild(pager);
    section.appendChild(header);

    // Split into halves by whose turn produced each line. Both halves are always
    // rendered, empty or not, so the layout doesn't jump around as a round fills.
    const body = document.createElement("div");
    body.className = "combat-log-body";
    const entries = state.combatLogPages[state.viewedLogPage] ?? [];
    // Only auto-scroll the newest round - an older one the user deliberately
    // paged back to shouldn't jump.
    const isLatestPage = state.viewedLogPage === pageCount - 1;
    for (const team of TEAMS) {
      body.appendChild(
        this.renderCombatLogHalf(team, entries.filter((e) => e.team === team), isLatestPage),
      );
    }
    section.appendChild(body);

    return section;
  }

  private renderCombatLogHalf(team: Team, entries: CombatLogEntry[], autoScroll: boolean): HTMLElement {
    const half = document.createElement("div");
    half.className = "combat-log-half";

    const { playerOneName, playerTwoName } = this.store.getState();
    const heading = document.createElement("h4");
    heading.className = "combat-log-half-heading";
    // Named for every viewer, not just spectators - a player wants to know their
    // opponent's actual username here too, not just which side of the board they're on.
    heading.textContent = team === "PLAYER_ONE" ? `Player One (${playerOneName})` : `Player Two (${playerTwoName})`;
    heading.style.color = teamCssColor(team);
    half.appendChild(heading);

    const log = document.createElement("div");
    log.className = "combat-log";
    if (entries.length === 0) {
      const empty = document.createElement("div");
      empty.className = "hint";
      empty.textContent = "Nothing yet.";
      log.appendChild(empty);
    } else {
      for (const entry of entries) log.appendChild(renderLogLine(entry.segments));
    }
    if (autoScroll) log.scrollTop = log.scrollHeight;
    half.appendChild(log);

    return half;
  }

  private renderMessageLog(state: MatchUiState): HTMLElement {
    const section = document.createElement("div");
    section.className = "hud-section message-log-section";

    const h3 = document.createElement("h3");
    h3.textContent = "System";
    section.appendChild(h3);

    const log = document.createElement("div");
    log.className = "message-log";
    for (const msg of state.messages) {
      const line = document.createElement("div");
      line.textContent = msg;
      log.appendChild(line);
    }
    log.scrollTop = log.scrollHeight;
    section.appendChild(log);

    return section;
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

    // The opponent's column is on the right, so its art is turned to face yours.
    // Keyed on the column, not on a team: these heroes are undrafted and belong
    // to nobody yet, and the layout is relative to the viewer either way.
    columns.appendChild(this.renderDraftColumn("Your options", state.draftRound!.options, true));
    columns.appendChild(
      this.renderDraftColumn("Opponent options", state.draftRound!.opponentOptions, false, true),
    );

    return backdrop;
  }

  private renderDraftColumn(
    heading: string,
    options: UnitDefinitionSnapshot[],
    clickable: boolean,
    mirrored = false,
  ): HTMLElement {
    const col = document.createElement("div");
    col.className = "draft-column";
    const h4 = document.createElement("h4");
    h4.textContent = heading;
    col.appendChild(h4);

    const cards = document.createElement("div");
    cards.className = "draft-cards";
    for (const def of options) {
      cards.appendChild(renderUnitCard(def, this.tooltip,
        clickable ? (picked) => this.actions.sendPick(picked.definitionId) : undefined,
        mirrored));
    }
    col.appendChild(cards);
    return col;
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
    const prompt = state.prompt as {
      kind: "attribute";
      unitId: string;
      opponentUnitId: string;
      selectableAttributes?: Attribute[];
    };
    const self = this.store.findUnit(prompt.unitId);
    const opponent = this.store.findUnit(prompt.opponentUnitId);
    // An "attribute" prompt is only ever sent to the one participant it's for (sendTo, never
    // broadcast) - a spectator never receives one, so yourTeam is never actually null here;
    // the fallback exists purely to satisfy the type after widening it for spectator support.
    const yourTeam = state.yourTeam ?? "PLAYER_ONE";

    const encounter = document.createElement("div");
    encounter.className = "encounter-row";
    // Your unit is always the left card, so it faces right and the opponent's
    // faces left. That is deliberately the *slot's* rule rather than the board's
    // "PLAYER_TWO turns" one: which team sits on the left here depends on who is
    // looking, so keying the flip on team would leave a PLAYER_TWO viewer
    // watching their own hero and the enemy face away from each other.
    encounter.appendChild(this.renderEncounterCard(self, yourTeam, false));

    const vs = document.createElement("div");
    vs.className = "encounter-vs";
    vs.textContent = "VS";
    encounter.appendChild(vs);

    encounter.appendChild(this.renderEncounterCard(opponent, yourTeam, true));
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

    // The server decides which attributes are legal (a unit cannot fight with one it has
    // none of) and ships the set; the client never re-derives the rule from the stat block.
    // An older server that doesn't send the field leaves all three enabled.
    const selectable = prompt.selectableAttributes;
    const row = document.createElement("div");
    row.className = "attribute-buttons";
    for (const attr of ATTRIBUTES) {
      const usable = !selectable || selectable.includes(attr);
      const btn = document.createElement("button");
      btn.className = "primary";
      btn.textContent = attr;
      btn.disabled = !usable;
      if (!usable) {
        // Disabled alone reads as "broken"; say which stat is missing, using the same
        // hover tooltip everything else in the HUD uses rather than a second mechanism.
        this.tooltip.attachAbility(btn, {
          name: attr,
          description: `${self?.name ?? "This unit"} has no ${attr.toLowerCase()} left, so it cannot fight with it.`,
          details: [],
          stats: {},
          passive: false,
          cooldownLabel: "",
        });
      } else {
        btn.addEventListener("click", () => this.actions.sendAttribute(attr));
      }
      row.appendChild(btn);
    }
    panel.appendChild(row);

    return backdrop;
  }

  private renderEncounterCard(
    unit: UnitSnapshot | null,
    yourTeam: Team,
    mirrored: boolean,
  ): HTMLElement {
    const card = document.createElement("div");
    card.className = "encounter-card";

    if (!unit) {
      // Shouldn't happen in practice (both ids come straight from the last
      // real state snapshot), but degrade gracefully rather than throw.
      card.textContent = "Unknown unit";
      return card;
    }

    card.appendChild(
      renderUnitFullBody(
        { definitionId: unit.definitionId, team: unit.team, unitType: unit.unitType, name: unit.name },
        mirrored,
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

    const cardBarrierBar = renderBarrierBar(unit);
    if (cardBarrierBar) card.appendChild(cardBarrierBar);
    card.appendChild(renderHpBar(unit));

    const hpText = document.createElement("div");
    hpText.className = "hint";
    hpText.textContent = formatHpText(unit);
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
    if (state.isSpectator) {
      title.textContent = "Game Over";
    } else {
      const youWon = state.gameOver!.winnerTeam === state.yourTeam;
      title.textContent = youWon ? "Victory!" : "Defeat";
    }
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
