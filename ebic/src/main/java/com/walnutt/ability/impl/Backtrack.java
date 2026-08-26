package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.PostDamageEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.map.Tile;

/**
 * Chronos - dashes to an empty tile and heals for damage taken during the
 * previous of his own turns. Tracks damage via the normal onDamageTaken/
 * onTurnStart hooks - available to any Ability (active or passive) since both
 * extend TriggerHandler. backtrack_period is treated as always 1 (only value
 * ever specified) - "the previous turn", not a longer rolling window.
 */
public class Backtrack extends Ability {
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
        state.getMap().moveUnit(owner, destination);

        if (damageTakenLastTurn > 0) {
            owner.heal(state, damageTakenLastTurn);
        }

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
