package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.event.PostDamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.EffectCategory;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitType;

/** Auroth's Frostbite debuff: no healing, and instantly shatters the host below the kill threshold. */
public class FrostbiteEffect extends Effect {
    private final Unit source;
    private double killThreshold;
    /** Upgrade only: a higher bar that applies to basic units alone. 0 leaves them on the normal one. */
    private double basicKillThreshold;

    public FrostbiteEffect(Unit source, int duration, double killThreshold) {
        this(source, duration, killThreshold, 0);
    }

    public FrostbiteEffect(Unit source, int duration, double killThreshold, double basicKillThreshold) {
        super("Frostbite",
            "Blocks all healing on the target for the duration; if its health ever drops below "
                + Math.round(killThreshold * 100) + "% of max, it instantly shatters and dies"
                + (basicKillThreshold > killThreshold
                    ? ", or below " + Math.round(basicKillThreshold * 100) + "% for a basic unit"
                    : "") + ".",
            duration);
        this.source = source;
        this.killThreshold = killThreshold;
        this.basicKillThreshold = basicKillThreshold;
        this.flags.add(StatusFlag.IMMUNE_TO_HEALING);
        this.category = EffectCategory.DEBUFF;
    }

    /**
     * Brings an existing frostbite up to Auroth's current one - see Effect.extendDuration. Without
     * it, unlocking Frostbite left everyone already bitten on the old thresholds, so the upgrade
     * appeared to do nothing to anyone already fighting.
     */
    public void refresh(double killThreshold, double basicKillThreshold) {
        this.killThreshold = Math.max(this.killThreshold, killThreshold);
        this.basicKillThreshold = Math.max(this.basicKillThreshold, basicKillThreshold);
    }

    @Override
    public void onDamageTaken(GameState state, PostDamageEvent event) {
        Unit owner = getOwner();
        if (owner == null || owner.isDead() || event.damageEvent().getTarget() != owner) {
            return;
        }
        if (owner.getHealth() < thresholdFor(owner) * owner.getMaxHealth()) {
            owner.instantKill(state, source);
        }
    }

    /**
     * The higher of the two for a basic unit, so the upgrade can only ever widen the margin -
     * a basic is never harder to shatter than anything else on the board.
     */
    private double thresholdFor(Unit owner) {
        return owner.getUnitType() == UnitType.BASIC
            ? Math.max(killThreshold, basicKillThreshold)
            : killThreshold;
    }

    @Override
    public Unit getSource() {
        return source;
    }
}
