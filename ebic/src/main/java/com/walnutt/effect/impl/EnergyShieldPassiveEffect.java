package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;

/**
 * The barrier upgraded Energy Shield wears permanently, mending itself every turn.
 *
 * Deliberately NOT an EnergyShieldEffect: the ability looks that class up to decide whether
 * to refresh a cast shield rather than stack a second one, and this must never be mistaken
 * for one - the two are meant to sit on Maxwell at the same time and absorb in turn.
 *
 * Shattering it does not remove it. BarrierEffect ends a barrier the moment its pool empties,
 * which is right for a shield someone paid a cooldown for and wrong for one that is simply
 * part of the unit, so this re-arms itself and mends back up from zero.
 */
public class EnergyShieldPassiveEffect extends BarrierEffect {
    private final int regenPerTurn;
    private final int maxBarrierHp;

    public EnergyShieldPassiveEffect(int regenPerTurn, int maxBarrierHp) {
        super("Energy Shield",
            "A self-repairing barrier: regains " + regenPerTurn + " each turn, up to "
                + maxBarrierHp + ". Separate from the barrier Energy Shield casts.",
            Effect.PERMANENT, 0);
        this.regenPerTurn = regenPerTurn;
        this.maxBarrierHp = maxBarrierHp;
    }

    /**
     * Starts empty and fills on the first turn, rather than arriving full: the upgrade is
     * meant to be a growing shield, not a free 80 the instant it lands.
     */
    @Override
    public void onTurnStart(GameState state, TurnStartEvent event) {
        if (getOwner() == null || event.team() != getOwner().getTeam()) {
            return;
        }
        setRemainingTurns(Effect.PERMANENT);
        restore(regenPerTurn, maxBarrierHp);
    }

    @Override
    protected void onBarrierBroken(GameState state) {
        // BarrierEffect zeroed the duration on the way in; put it back so the plating mends
        // next turn instead of being gone for good.
        setRemainingTurns(Effect.PERMANENT);
    }
}
