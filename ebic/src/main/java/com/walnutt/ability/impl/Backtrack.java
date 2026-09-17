package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import java.util.List;

import com.walnutt.combat.CombatEngine;
import com.walnutt.combat.WeightedEncounter;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.PostDamageEvent;
import com.walnutt.event.PostMoveEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.map.Position;
import com.walnutt.map.Tile;
import com.walnutt.unit.Unit;

/**
 * Chronos - dashes to an empty tile and heals for damage taken during the
 * previous of his own turns. Tracks damage via the normal onDamageTaken/
 * onTurnStart hooks - available to any Ability (active or passive) since both
 * extend TriggerHandler. backtrack_period is treated as always 1 (only value
 * ever specified) - "the previous turn", not a longer rolling window.
 */
public class Backtrack extends Ability {
    /** Upgrade: whether arriving swings at every adjacent enemy. */
    private boolean strikesOnArrival;
    private int damageTakenLastTurn;
    private int damageTakenThisTurn;

    public Backtrack(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 3));
        setRange(definition.getInt("range", 3));
    }

    @Override
    public void onDamageTaken(GameState state, PostDamageEvent event) {
        if (owner != null && event.damageEvent().getTarget() == owner) {
            damageTakenThisTurn += Math.max(0, event.damageEvent().getDamage());
        }
    }

    @Override
    public void onTurnStart(GameState state, TurnStartEvent event) {
        if (owner != null && event.team() == owner.getTeam()) {
            damageTakenLastTurn = damageTakenThisTurn;
            damageTakenThisTurn = 0;
        }
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target)) {
            return false;
        }
        if (!(target instanceof TileTarget tileTarget)) {
            return false;
        }
        Tile tile = tileTarget.getTile();
        return tile.isWalkable() && isInRange(state, tile.getPosition());
    }

    @Override
    public void onUse(GameState state, Target target) {
        Tile destination = ((TileTarget) target).getTile();
        Position from = owner.getPosition();
        state.getMap().moveUnit(owner, destination);
        // A forced relocation, not a chosen Move: no markMoved, no PreMoveEvent (nothing should
        // be able to cancel it) - but PostMoveEvent still fires so anything tracking this unit's
        // position (a Feast latch, Cloak's own onMove ambush) sees it, same as an ordinary step.
        state.getEventBus().publish(state, new PostMoveEvent(owner, from, destination.getPosition()));

        if (damageTakenLastTurn > 0) {
            owner.heal(state, damageTakenLastTurn);
        }
        // Upgrade: the landing itself is an attack, on everything beside the tile arrived at.
        // Snapshotted first because a strike can kill, which would otherwise mutate the tile's
        // neighbours mid-iteration.
        if (strikesOnArrival) {
            for (Unit enemy : List.copyOf(
                    state.getMap().getUnitsInRadius(destination.getPosition(), 1))) {
                if (enemy != owner && enemy.getTeam() != owner.getTeam() && !enemy.isDead()
                    && !owner.isDead()) {
                    CombatEngine.performAttack(state, new WeightedEncounter(owner, enemy));
                }
            }
        }

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }

    @Override
    protected void onUpgraded() {
        this.strikesOnArrival = true;
    }
}
