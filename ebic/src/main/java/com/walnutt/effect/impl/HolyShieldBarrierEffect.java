package com.walnutt.effect.impl;

import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Thaddeus's Holy Shield: on break, clears the host's debuffs and blasts nearby enemies. */
public class HolyShieldBarrierEffect extends BarrierEffect {
    private final int breakDamage;
    private final int radius;

    public HolyShieldBarrierEffect(int duration, int barrierHp, int breakDamage, int radius) {
        super("Holy Shield",
            "Absorbs up to " + barrierHp + " damage; if the barrier breaks before it expires, "
                + "clears this unit's debuffs and blasts nearby enemies for " + breakDamage + " damage.",
            duration, barrierHp);
        this.breakDamage = breakDamage;
        this.radius = radius;
    }

    @Override
    protected void onBarrierBroken(GameState state) {
        Unit owner = getOwner();
        if (owner == null) {
            return;
        }
        owner.dispelDebuffs(state);
        for (Unit enemy : state.getMap().getUnitsInRadius(owner.getPosition(), radius)) {
            if (enemy.getTeam() != owner.getTeam() && !enemy.isDead()) {
                DamageEvent event = new DamageEvent(owner, enemy, breakDamage);
                event.setCauseLabel("Holy Shield");
                enemy.takeDamage(state, event);
            }
        }
    }
}
