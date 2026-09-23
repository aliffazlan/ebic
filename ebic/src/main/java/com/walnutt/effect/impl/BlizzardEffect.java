package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.EffectCategory;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.Unit;

/** Yuki's Blizzard: rooted + damage per turn. Reused (with 0 damage) by the golem's Blizzard Fist/Snow Blast. */
public class BlizzardEffect extends Effect {
    private final Unit source;
    private int damagePerTurn;

    public BlizzardEffect(Unit source, int duration, int damagePerTurn) {
        this(source, duration, damagePerTurn, false);
    }

    public BlizzardEffect(Unit source, int duration, int damagePerTurn, boolean disarms) {
        super("Blizzard",
            "Roots the target in place and deals " + damagePerTurn + " damage at the start of each of "
                + "its turns; casting Blizzard again while it's already active extends the duration "
                + "instead of applying a second stack.",
            duration);
        this.source = source;
        this.damagePerTurn = damagePerTurn;
        this.flags.add(StatusFlag.ROOTED);
        // Upgraded, they cannot swing their way out of it either.
        if (disarms) {
            this.flags.add(StatusFlag.DISARMED);
        }
        this.category = EffectCategory.DEBUFF;
    }

    @Override
    public Unit getSource() {
        return source;
    }

    @Override
    public void onTurnStart(GameState state, TurnStartEvent event) {
        if (getOwner() == null || isExpired() || event.team() != getOwner().getTeam()) {
            return;
        }
        if (damagePerTurn <= 0) {
            return;
        }
        DamageEvent damageEvent = new DamageEvent(source, getOwner(), damagePerTurn);
        damageEvent.setCauseLabel("Blizzard");
        getOwner().takeDamage(state, damageEvent);
    }

    /**
     * Brings an existing storm up to the caster's current one - see Effect.extendDuration for why
     * this matters. Yuki unlocking Blizzard mid-match must start disarming whoever is ALREADY
     * buried, not only the next victim - see Blizzard.onRetroactiveUpgrade, the other caller.
     *
     * Raised, never lowered: the golem's damage-less Blizzard Fist must not water down a storm
     * Yuki herself laid, and an un-upgraded fist must not strip a disarm.
     */
    public void refresh(int damagePerTurn, boolean disarms) {
        this.damagePerTurn = Math.max(this.damagePerTurn, damagePerTurn);
        if (disarms) {
            this.flags.add(StatusFlag.DISARMED);
        }
    }

    /** "If the target is already affected by blizzard, the duration is increased." */
    public static void applyOrExtend(Unit target, Unit source, int duration, int damagePerTurn) {
        applyOrExtend(target, source, duration, damagePerTurn, false);
    }

    public static void applyOrExtend(Unit target, Unit source, int duration, int damagePerTurn,
                                      boolean disarms) {
        target.getActiveEffect(BlizzardEffect.class).ifPresentOrElse(
            existing -> {
                existing.extendDuration(duration);
                existing.refresh(damagePerTurn, disarms);
            },
            () -> target.addEffect(new BlizzardEffect(source, duration, damagePerTurn, disarms)));
    }
}
