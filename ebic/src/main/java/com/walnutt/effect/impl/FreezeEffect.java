package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.EffectCategory;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.Unit;

/** Auroth's Cold Embrace: frozen + invulnerable, but takes/heals a fixed amount every turn regardless. */
public class FreezeEffect extends Effect {
    private final Unit caster;
    private final int amountPerTurn;
    private final boolean targetIsEnemy;

    public FreezeEffect(Unit caster, int duration, int amountPerTurn, boolean targetIsEnemy) {
        this(caster, duration, amountPerTurn, targetIsEnemy, false);
    }

    /**
     * {@code mayMove} is upgraded Cold Embrace on an ALLY: silenced and disarmed rather than
     * shut down, so they can still walk out of trouble while the ice mends them. FROZEN is what
     * blocks all three action kinds, so the upgraded form simply does not apply it.
     */
    public FreezeEffect(Unit caster, int duration, int amountPerTurn, boolean targetIsEnemy,
                         boolean mayMove) {
        super("Cold Embrace",
            "Freezes the target - invulnerable, and "
                + (mayMove ? "silenced and disarmed but still able to walk" : "unable to act")
                + " - for the duration. Deals " + amountPerTurn + " damage each turn if the target "
                + "is an enemy, or heals that much each turn if it's an ally.",
            duration);
        this.caster = caster;
        this.amountPerTurn = amountPerTurn;
        this.targetIsEnemy = targetIsEnemy;
        if (mayMove) {
            this.flags.add(StatusFlag.SILENCED);
            this.flags.add(StatusFlag.DISARMED);
        } else {
            this.flags.add(StatusFlag.FROZEN);
        }
        this.flags.add(StatusFlag.INVULNERABLE);
        this.category = targetIsEnemy ? EffectCategory.DEBUFF : EffectCategory.BUFF;
    }

    @Override
    public void onTurnStart(GameState state, TurnStartEvent event) {
        if (getOwner() == null || isExpired() || event.team() != getOwner().getTeam()) {
            return;
        }
        if (targetIsEnemy) {
            DamageEvent damageEvent = new DamageEvent(caster, getOwner(), amountPerTurn);
            damageEvent.setBypassInvulnerability(true);
            damageEvent.setCauseLabel("Cold Embrace");
            getOwner().takeDamage(state, damageEvent);
        } else {
            getOwner().heal(state, amountPerTurn);
        }
    }
}
