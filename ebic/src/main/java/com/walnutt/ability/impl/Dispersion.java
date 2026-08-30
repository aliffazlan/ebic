package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.NoTarget;
import com.walnutt.ability.target.Target;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.Effect;
import com.walnutt.effect.StatusEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.PostDamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/**
 * Mercurial - reflects a portion of any damage taken back onto surrounding enemies.
 *
 * Extends Ability with the passive flag set rather than PassiveAbility, which hard-codes
 * "never castable" and "onUse throws". Its upgrade turns it into an active, and that flag is
 * flipped by the base class from the JSON's upgrade.type - so the refusal has to be the
 * ordinary one Ability.canUse already applies, not a subclass that cannot change its mind.
 */
public class Dispersion extends Ability {
    /** How long the raised dispersal lasts once activated, and what it raises the share to. */
    private static final String ACTIVE_EFFECT_NAME = "Dispersion (active)";

    private int radius;
    private double reflectPercent;
    private double activeReflect;
    private int activeDuration;

    public Dispersion(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), true);
        this.radius = definition.getInt("radius", 1);
        this.reflectPercent = definition.getDouble("dmg_reflect", 0.25);
    }

    @Override
    protected void onUpgraded() {
        this.radius = statInt("radius", radius);
        this.reflectPercent = stat("dmg_reflect", reflectPercent);
        this.activeReflect = stat("active_reflect", reflectPercent);
        this.activeDuration = statInt("duration", 3);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        return super.canUse(state, target) && target instanceof NoTarget;
    }

    @Override
    public void onUse(GameState state, Target target) {
        // A plain marker: the reflection below reads it, so there is no second copy of the
        // dispersal logic and the passive keeps running underneath it either way.
        owner.addEffect(new StatusEffect(ACTIVE_EFFECT_NAME, activeDuration));
        state.spendMoves(getMoveCost(state));
        resetToMax();
    }

    /** The share sent back right now: the raised one while activated, the passive one otherwise. */
    private double currentReflect() {
        if (owner == null || activeReflect <= 0) {
            return reflectPercent;
        }
        boolean raised = owner.getEffects().stream()
            .anyMatch(effect -> ACTIVE_EFFECT_NAME.equals(effect.getName()) && !effect.isExpired());
        return raised ? activeReflect : reflectPercent;
    }

    @Override
    public void onDamageTaken(GameState state, PostDamageEvent event) {
        Unit owner = getOwner();
        int damage = event.damageEvent().getDamage();
        if (owner == null || owner.isDead() || event.target() != owner || damage <= 0) {
            return;
        }

        int reflected = (int) Math.round(damage * currentReflect());
        if (reflected <= 0) {
            return;
        }

        for (Unit enemy : state.getMap().getUnitsInRadius(owner.getPosition(), radius)) {
            if (enemy.getTeam() != owner.getTeam() && !enemy.isDead()) {
                DamageEvent reflectDamage = new DamageEvent(owner, enemy, reflected);
                reflectDamage.setCauseLabel("Dispersion");
                enemy.takeDamage(state, reflectDamage);
            }
        }
    }
}
