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

    public ImprisonmentEffect(Unit caster, int duration, double intStealPercent) {
        super("Oblivion Confinement",
            "Imprisons the target - stunned and invulnerable - for the duration, stealing "
                + Math.round(intStealPercent * 100) + "% of its intelligence on application. If the "
                + "target escapes when the effect expires naturally, another "
                + Math.round(intStealPercent * 100) + "% is stolen.",
            duration);
        this.caster = caster;
        this.intStealPercent = intStealPercent;
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

    @Override
    public void onExpire(GameState state) {
        // "If the unit escapes from this effect, an additional int_steal% intelligence is stolen."
        stealIntelligence();
    }
}
