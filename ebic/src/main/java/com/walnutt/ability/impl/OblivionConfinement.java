package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.ImprisonmentEffect;
import com.walnutt.event.PostDamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.Stat;
import com.walnutt.status.StatModifier;
import com.walnutt.unit.Unit;

/**
 * Harbinger - imprisons (stun + invulnerable) an enemy, stealing intelligence on cast.
 *
 * Upgraded, Harbinger also permanently steals a slice of intelligence off EVERY blow he
 * lands, not only Oblivion Confinement's own cast - see {@link #onDamageTaken}.
 */
public class OblivionConfinement extends Ability {
    private int duration;
    private double intStealPercent;
    private double passiveIntStealPercent;

    public OblivionConfinement(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 3));
        setRange(definition.getInt("cast_range", 2));
        this.duration = definition.getInt("duration", 1);
        this.intStealPercent = definition.getDouble("int_steal", 0.2);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target)) {
            return false;
        }
        if (!(target instanceof UnitTarget unitTarget)) {
            return false;
        }
        Unit other = unitTarget.getUnit();
        return !other.isDead()
            && other.getTeam() != owner.getTeam()
            && isInRange(state, other.getPosition());
    }

    @Override
    public void onUse(GameState state, Target target) {
        Unit other = ((UnitTarget) target).getUnit();

        ImprisonmentEffect effect = new ImprisonmentEffect(owner, duration, intStealPercent);
        other.addEffect(effect);
        effect.stealIntelligence();

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }

    /**
     * Upgraded: every instance of damage this unit deals - a swing, Sanity's Eclipse, anything
     * traced back to it - also tears away a slice of the victim's intelligence, minimum 1.
     * Hooked on the POST event so a blow that landed for 0 (absorbed, cancelled) steals nothing.
     */
    @Override
    public void onDamageTaken(GameState state, PostDamageEvent event) {
        if (passiveIntStealPercent <= 0) {
            return;
        }
        Unit harbinger = getOwner();
        if (harbinger == null || harbinger.isDead()) {
            return;
        }
        if (event.damageEvent().getSource() != harbinger || event.damageEvent().getDamage() <= 0) {
            return;
        }
        Unit victim = event.damageEvent().getTarget();
        if (victim == null || victim == harbinger || victim.isDead()) {
            return;
        }
        int amount = Math.max(1, (int) Math.round(victim.getEffective(Stat.INTELLIGENCE) * passiveIntStealPercent));
        victim.addPermanentModifier(StatModifier.flat(Stat.INTELLIGENCE, -amount, this));
        harbinger.addPermanentModifier(StatModifier.flat(Stat.INTELLIGENCE, amount, this));
    }

    @Override
    protected void onUpgraded() {
        this.duration = statInt("duration", duration);
        this.intStealPercent = stat("int_steal", intStealPercent);
        this.passiveIntStealPercent = stat("passive_int_steal", 0.1);
    }
}
