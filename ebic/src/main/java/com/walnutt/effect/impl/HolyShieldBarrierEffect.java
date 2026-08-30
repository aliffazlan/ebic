package com.walnutt.effect.impl;

import com.walnutt.event.DamageEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Thaddeus's Holy Shield: on break, clears the host's debuffs and blasts nearby enemies. */
public class HolyShieldBarrierEffect extends BarrierEffect {
    private final int breakDamage;
    private final int radius;
    /** Upgrade: HP mended each turn, and whether a barrier that merely faded still erupts. */
    private final int regenPerTurn;
    private final int fullBarrierHp;
    private final boolean alwaysErupt;

    public HolyShieldBarrierEffect(int duration, int barrierHp, int breakDamage, int radius) {
        this(duration, barrierHp, breakDamage, radius, 0, false);
    }

    public HolyShieldBarrierEffect(int duration, int barrierHp, int breakDamage, int radius,
                                    int regenPerTurn, boolean alwaysErupt) {
        super("Holy Shield",
            "Absorbs up to " + barrierHp + " damage"
                + (regenPerTurn > 0 ? ", mending " + regenPerTurn + " each turn" : "")
                + "; " + (alwaysErupt ? "when it breaks or fades it" : "if the barrier breaks before it expires, it")
                + " clears this unit's debuffs and blasts nearby enemies for " + breakDamage + " damage.",
            duration, barrierHp);
        this.breakDamage = breakDamage;
        this.radius = radius;
        this.regenPerTurn = regenPerTurn;
        this.fullBarrierHp = barrierHp;
        this.alwaysErupt = alwaysErupt;
    }

    /** Mends toward the pool it started with, never past it. */
    @Override
    public void onTurnStart(GameState state, TurnStartEvent event) {
        if (isExpired() || getOwner() == null || event.team() != getOwner().getTeam()) {
            return;
        }
        restore(regenPerTurn, fullBarrierHp);
    }

    /**
     * Upgraded, a barrier that simply ran out of time erupts exactly as a shattered one does.
     * onBarrierBroken already fired for the shattered case and set the duration to 0, so this
     * would double up - hence the check that there is anything left to have faded.
     */
    @Override
    public void onExpire(GameState state) {
        if (alwaysErupt && getRemainingBarrierHp() > 0) {
            erupt(state);
        }
    }

    @Override
    protected void onBarrierBroken(GameState state) {
        erupt(state);
    }

    private void erupt(GameState state) {
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
