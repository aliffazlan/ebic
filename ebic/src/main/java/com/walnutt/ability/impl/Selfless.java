package com.walnutt.ability.impl;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Thaddeus - adjacent allies have a portion of their incoming damage redirected to him instead. */
public class Selfless extends PassiveAbility {
    private int radius;
    private double redirectPercent;
    /** Upgrade: the fraction of redirected damage this unit actually takes. 1.0 until upgraded. */
    private double selfDamageTaken = 1.0;

    public Selfless(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
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
        // The ally is spared the whole redirected share either way; the upgrade only changes
        // how much of it survives the trip, so the pair together take strictly less.
        event.modifyDamage(-redirected);
        int borne = (int) Math.round(redirected * selfDamageTaken);
        if (borne <= 0) {
            return;
        }
        DamageEvent redirectedDamage = new DamageEvent(event.getSource(), owner, borne);
        redirectedDamage.setCauseLabel("Selfless");
        owner.takeDamage(state, redirectedDamage);
    }

    @Override
    protected void onUpgraded() {
        this.radius = statInt("radius", radius);
        this.redirectPercent = stat("redirect_dmg", redirectPercent);
        this.selfDamageTaken = stat("self_damage_taken", selfDamageTaken);
    }
}
