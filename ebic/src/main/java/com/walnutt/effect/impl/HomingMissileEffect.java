package com.walnutt.effect.impl;

import java.util.List;

import com.walnutt.effect.Effect;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.EffectCategory;
import com.walnutt.unit.Unit;

/**
 * Maxwell's Homing Missile, in flight.
 *
 * Living on the VICTIM rather than on Maxwell is what makes the missile home: the effect
 * travels with whoever it is attached to, so the impact point is wherever they happen to
 * be when it lands - no per-turn position bookkeeping, and running away does not help.
 *
 * The timing is deliberately NOT the caster-turn countdown OrbEffect and SproutEffect use:
 * because this sits on the victim, the ordinary tick already runs at the end of the
 * VICTIM's own turn, so they get a full turn to react or cleanse before impact. Moving it
 * to a turn hook would delay the missile by an extra round, not fix anything.
 *
 * Detonation is in onExpire, which fires for a dispel as well as for a natural finish, so
 * wasDispelled() is checked to deny the payload when the lock is cleansed instead
 * (Poison Bloom's spread uses the same guard). Maxwell dying does not recall a missile
 * already in the air.
 */
public class HomingMissileEffect extends Effect {
    private final Unit source;
    private final int impactDamage;
    private final int splashDamage;

    public HomingMissileEffect(Unit source, int delay, int impactDamage, int splashDamage) {
        super("Missile Lock",
            "A homing missile is tracking this unit. On impact it deals " + impactDamage
                + " damage to it and " + splashDamage + " to enemies beside it. Cleansing the lock "
                + "shoots the missile down.",
            delay);
        this.source = source;
        this.impactDamage = impactDamage;
        this.splashDamage = splashDamage;
        this.category = EffectCategory.DEBUFF;
    }

    @Override
    public String getExtraInfo() {
        int turns = getRemainingTurns();
        return "Impact in " + turns + " turn" + (turns == 1 ? "" : "s") + ": " + impactDamage + " damage";
    }

    @Override
    public void onExpire(GameState state) {
        Unit victim = getOwner();
        if (victim == null || wasDispelled() || victim.isDead() || victim.getPosition() == null) {
            return;
        }

        DamageEvent impact = new DamageEvent(source, victim, impactDamage);
        impact.setCauseLabel("Homing Missile");
        victim.takeDamage(state, impact);

        if (splashDamage <= 0) {
            return;
        }
        // Splash hits enemies of whoever fired, not simply "everyone beside the victim" -
        // a missile landing next to Maxwell's own troops must not hit them.
        for (Unit nearby : List.copyOf(state.getMap().getAdjacentUnits(victim.getPosition(),
                u -> !u.isDead() && u.getTeam() != source.getTeam()))) {
            DamageEvent splash = new DamageEvent(source, nearby, splashDamage);
            splash.setCauseLabel("Homing Missile");
            nearby.takeDamage(state, splash);
        }
    }
}
