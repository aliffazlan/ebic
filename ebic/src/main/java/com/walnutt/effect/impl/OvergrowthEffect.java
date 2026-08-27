package com.walnutt.effect.impl;

import java.util.List;

import com.walnutt.effect.Effect;
import com.walnutt.game.GameState;
import com.walnutt.game.RemovalReason;
import com.walnutt.status.EffectCategory;
import com.walnutt.unit.Unit;

/**
 * Holds the lifespan of one Overgrowth's Branchlings. It lives on Branch rather than on
 * the Branchlings because registered summons never get startTurn/endTurn, so an effect
 * sitting on a Branchling would never tick down.
 *
 * Branch dying doesn't orphan them: a dead unit stays in its player's roster and keeps
 * ticking, so this still expires on schedule and cleans up.
 */
public class OvergrowthEffect extends Effect {
    private final List<Unit> branchlings;

    public OvergrowthEffect(List<Unit> branchlings, int duration) {
        super("Overgrowth",
            "Sustaining " + branchlings.size() + " Branchling(s). They wither when this expires.",
            duration);
        this.branchlings = List.copyOf(branchlings);
        this.category = EffectCategory.BUFF;
    }

    public List<Unit> getBranchlings() {
        return branchlings;
    }

    @Override
    public String getExtraInfo() {
        long alive = branchlings.stream().filter(b -> !b.isDead()).count();
        return alive + " Branchling(s) still standing";
    }

    @Override
    public void onExpire(GameState state) {
        for (Unit branchling : branchlings) {
            if (!branchling.isDead()) {
                state.removeUnit(branchling, RemovalReason.DESPAWN);
            }
        }
    }
}
