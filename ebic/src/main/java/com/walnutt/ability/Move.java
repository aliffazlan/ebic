package com.walnutt.ability;

import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.event.PostMoveEvent;
import com.walnutt.event.PreMoveEvent;
import com.walnutt.game.GameState;
import com.walnutt.map.Position;
import com.walnutt.map.Tile;
import com.walnutt.unit.ActionKind;
import com.walnutt.unit.UnitType;

public class Move extends Ability {
    public Move() {
        super("Move", "Moves to an adjacent tile", false);
    }

    /**
     * Basics move for free, exactly as they attack for free - they are meant to swarm
     * without eating into the champion/elite action budget. They can still only move once
     * per turn (hasMovedThisTurn), so "free" means "costs no move point", not "unlimited".
     */
    @Override
    public int getMoveCost(GameState state) {
        return owner != null && owner.getUnitType() == UnitType.BASIC ? 0 : 1;
    }

    /**
     * Movement is always to an adjacent tile, so a cast-range bonus must not touch it -
     * Ability.getRange() would otherwise add Stat.CAST_RANGE (Maxwell's Gyroscope) and the
     * client would draw a band promising a two-tile step that canUse below still refuses.
     * Attack overrides this for the same reason, reading ATTACK_RANGE instead: between the
     * two overrides, "a basic Move or Attack ignores cast range" holds by construction.
     */
    @Override
    public int getRange() {
        return 1;
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (owner == null || owner.hasMovedThisTurn() || owner.isBlockedFrom(ActionKind.MOVE)) {
            return false;
        }
        if (!state.canSpendMoves(getMoveCost(state))) {
            return false;
        }
        if (!(target instanceof TileTarget tileTarget)) {
            return false;
        }
        Tile tile = tileTarget.getTile();
        return state.getMap().areAdjacent(owner.getPosition(), tile.getPosition()) && tile.isWalkable();
    }

    @Override
    public void onUse(GameState state, Target target) {
        Tile destination = ((TileTarget) target).getTile();
        Position from = owner.getPosition();

        PreMoveEvent preMove = new PreMoveEvent(owner, from, destination.getPosition());
        state.getEventBus().publish(state, preMove);
        if (preMove.isCancelled()) {
            return;
        }

        state.getMap().moveUnit(owner, destination);
        owner.markMoved();
        state.spendMoves(getMoveCost(state));

        state.getEventBus().publish(state, new PostMoveEvent(owner, from, destination.getPosition()));
    }
}
