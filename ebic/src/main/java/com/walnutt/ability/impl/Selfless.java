package com.walnutt.ability.impl;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Thaddeus - adjacent allies have a portion of their incoming damage redirected to him instead. */
public class Selfless extends PassiveAbility {
    private final int radius;
    private final double redirectPercent;

    public Selfless(AbilityDefinition definition) {
        super(definition.name(), definition.description());
        this.radius = definition.getInt("radius", 1);
        this.redirectPercent = definition.getDouble("redirect_dmg", 0.25);
    }

    @Override
    public void onIncomingDamage(GameState state, DamageEvent event) {
        Unit owner = getOwner();
        Unit target = event.getTarget();
        if (owner == null || owner.isDead() || target == null || target == owner) {
            return;
        }
        if (target.getTeam() != owner.getTeam() || event.getDamage() <= 0) {
            return;
        }
        if (state.getMap().getDistance(owner.getPosition(), target.getPosition()) > radius) {
            return;
        }

        int redirected = (int) Math.round(event.getDamage() * redirectPercent);
        if (redirected <= 0) {
            return;
        }
        event.modifyDamage(-redirected);
        DamageEvent redirectedDamage = new DamageEvent(event.getSource(), owner, redirected);
        redirectedDamage.setCauseLabel("Selfless");
        owner.takeDamage(state, redirectedDamage);
    }
}
