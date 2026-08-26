package com.walnutt.ability.impl;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.PostDamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Mercurial - reflects a portion of any damage taken back onto surrounding enemies. */
public class Dispersion extends PassiveAbility {
    private final int radius;
    private final double reflectPercent;

    public Dispersion(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
        this.radius = definition.getInt("radius", 1);
        this.reflectPercent = definition.getDouble("dmg_reflect", 0.25);
    }

    @Override
    public void onDamageTaken(GameState state, PostDamageEvent event) {
        Unit owner = getOwner();
        int damage = event.damageEvent().getDamage();
        if (owner == null || owner.isDead() || event.target() != owner || damage <= 0) {
            return;
        }

        int reflected = (int) Math.round(damage * reflectPercent);
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
