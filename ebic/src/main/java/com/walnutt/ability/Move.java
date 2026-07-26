package com.walnutt.ability;

import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.event.PostMoveEvent;
import com.walnutt.event.PreMoveEvent;
import com.walnutt.game.GameState;
import com.walnutt.map.Position;
import com.walnutt.map.Tile;
import com.walnutt.unit.ActionKind;

public class Move extends Ability {
    public Move() {
        super("Move", "Moves to an adjacent tile", false);
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
