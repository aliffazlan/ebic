package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.game.GameState;
import com.walnutt.status.EffectCategory;
import com.walnutt.status.Stat;
import com.walnutt.status.StatModifier;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.Unit;

/** Harbinger's Oblivion Confinement: stunned + invulnerable, with intelligence stolen on cast and on escape. */
public class ImprisonmentEffect extends Effect {
    private final Unit caster;
    private final double intStealPercent;
    /** Whether a second helping is taken when the target returns - Harbinger's upgrade. */
    private final boolean stealOnEscape;

    public ImprisonmentEffect(Unit caster, int duration, double intStealPercent) {
        this(caster, duration, intStealPercent, false);
    }

    public ImprisonmentEffect(Unit caster, int duration, double intStealPercent, boolean stealOnEscape) {
        super("Oblivion Confinement",
            "Imprisons the target - stunned and invulnerable - for the duration, stealing "
                + Math.round(intStealPercent * 100) + "% of its intelligence on application."
                + (stealOnEscape
                    ? " Another " + Math.round(intStealPercent * 100) + "% is stolen when it returns."
                    : ""),
            duration);
        this.caster = caster;
        this.intStealPercent = intStealPercent;
        this.stealOnEscape = stealOnEscape;
        this.flags.add(StatusFlag.STUNNED);
        this.flags.add(StatusFlag.INVULNERABLE);
        this.category = EffectCategory.DEBUFF;
    }

    public void stealIntelligence() {
        if (getOwner() == null || caster.isDead()) {
            return;
        }
        int amount = (int) Math.round(getOwner().getEffective(Stat.INTELLIGENCE) * intStealPercent);
        if (amount <= 0) {
            return;
        }
        getOwner().addPermanentModifier(StatModifier.flat(Stat.INTELLIGENCE, -amount, this));
        caster.addPermanentModifier(StatModifier.flat(Stat.INTELLIGENCE, amount, this));
    }

    /**
     * The second helping, on the way out. Baseline Oblivion Confinement takes its whole toll on
     * the way in (a heavier one than it used to, see oblivion_confinement.json); only the
     * upgraded form takes another when the target returns.
     */
    @Override
    public void onExpire(GameState state) {
        if (stealOnEscape) {
            stealIntelligence();
        }
    }
}
