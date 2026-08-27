package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.game.GameState;
import com.walnutt.game.RemovalReason;
import com.walnutt.map.Tile;
import com.walnutt.status.EffectCategory;
import com.walnutt.unit.Unit;

/**
 * Branch's pending Sprout teleport. Delayed payloads run in onExpire (the same shape
 * Sanity's Eclipse uses), here gated on the planted Branchling still being alive.
 *
 * The Branchling is removed with DESPAWN rather than killed: consuming your own summon
 * isn't a kill, and publishing a KillEvent for it would feed anything watching for kills
 * - a Doomed Branch could otherwise clear its own curse by cashing in a Branchling.
 */
public class SproutEffect extends Effect {
    private final Unit caster;
    private final Unit branchling;
    private final int barrierHp;
    private final int barrierDuration;

    public SproutEffect(Unit caster, Unit branchling, int delay, int barrierHp, int barrierDuration) {
        super("Sprout",
            "Teleporting to a planted Branchling. On arrival, this unit and its neighbours gain a "
                + barrierHp + " health barrier. Destroying the Branchling first cancels the teleport.",
            delay);
        this.caster = caster;
        this.branchling = branchling;
        this.barrierHp = barrierHp;
        this.barrierDuration = barrierDuration;
        this.category = EffectCategory.BUFF;
    }

    @Override
    public String getExtraInfo() {
        return branchling.isDead() ? "Branchling destroyed - teleport cancelled" : "Teleport pending";
    }

    @Override
    public void onExpire(GameState state) {
        if (branchling.isDead()) {
            return; // destroyed before the delay elapsed - teleport cancelled
        }
        // The Branchling is consumed either way. Clearing it up front matters when the
        // caster died mid-delay: a registered summon never ticks, so nothing else would
        // ever remove it and it would sit on the board for the rest of the match.
        Tile destination = state.getMap().getTile(branchling.getPosition());
        state.removeUnit(branchling, RemovalReason.DESPAWN);

        if (caster == null || caster.isDead() || destination == null) {
            return;
        }
        state.getMap().moveUnit(caster, destination);

        grantBarrier(caster);
        for (Unit ally : state.getMap().getAdjacentUnits(destination.getPosition(),
            u -> u.getTeam() == caster.getTeam() && u != caster && !u.isDead())) {
            grantBarrier(ally);
        }
    }

    private void grantBarrier(Unit unit) {
        unit.addEffect(new BarrierEffect("Sprout Barrier",
            "A shell of bark absorbing the next " + barrierHp + " damage.", barrierDuration, barrierHp));
    }
}
