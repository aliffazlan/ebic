package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.EffectCategory;
import com.walnutt.unit.Unit;

/**
 * Maxwell's Nanobots - repeated cleanse and heal.
 *
 * dispelDebuffs already honours Effect.dispellable and only touches DEBUFFs, so a
 * deliberately-permanent curse (Lucifer's Doom marks itself undispellable) survives this
 * while ordinary debuffs are stripped. Nothing extra is needed here.
 */
public class NanobotsEffect extends Effect {
    private final int healPerPulse;

    public NanobotsEffect(int duration, int healPerPulse) {
        super("Nanobots",
            "Medical nanobots strip away debuffs and repair " + healPerPulse
                + " health at the start of each of this unit's turns.",
            duration);
        this.healPerPulse = healPerPulse;
        this.category = EffectCategory.BUFF;
    }

    /**
     * One cleanse-and-heal. Called directly by the ability for the on-cast pulse, and by
     * this effect's own turn-start hook thereafter.
     *
     * Mutating the owner's effect list from inside an event dispatch is safe: EventBus
     * copies each unit's handler list before dispatching to it.
     */
    public void pulse(GameState state) {
        Unit target = getOwner();
        if (target == null || target.isDead()) {
            return;
        }
        target.dispelDebuffs(state);
        target.heal(state, healPerPulse);
    }

    @Override
    public void onTurnStart(GameState state, TurnStartEvent event) {
        if (getOwner() == null || isExpired() || event.team() != getOwner().getTeam()) {
            return;
        }
        pulse(state);
    }
}
