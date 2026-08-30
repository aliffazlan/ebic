package com.walnutt.ability.impl;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.Stat;
import com.walnutt.status.StatModifier;
import com.walnutt.unit.Unit;

/**
 * Dirge - at the start of every one of Dirge's own turns, permanently steals max HP
 * and strength from all units in radius (ally and enemy alike), plus direct damage.
 */
public class Decay extends PassiveAbility {
    private int radius;
    private int healthSteal;
    private int strengthSteal;
    /** Upgrade only: a wider drain that touches enemies alone. 0 radius means it is not active. */
    private int auraRadius;
    private int auraHealthSteal;

    public Decay(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
        this.radius = definition.getInt("radius", 1);
        this.healthSteal = definition.getInt("health_steal", 5);
        this.strengthSteal = definition.getInt("str_steal", 2);
    }

    @Override
    public void onTurnStart(GameState state, TurnStartEvent event) {
        // Same trap as Eye of the Storm: a dead Dirge still has a position and still gets
        // turn hooks, so without isDead his corpse kept draining everything beside it.
        if (getOwner() == null || getOwner().isDead() || event.team() != getOwner().getTeam()) {
            return;
        }
        for (Unit victim : state.getMap().getUnitsInRadius(getOwner().getPosition(), radius)) {
            if (victim == getOwner() || victim.isDead()) {
                continue;
            }
            drain(state, victim, healthSteal, strengthSteal);
        }
        if (auraRadius <= 0) {
            return;
        }
        // The outer rot is a SECOND drain rather than a wider version of the first, so an
        // adjacent enemy sits in both and loses to each - which is what the upgrade promises.
        for (Unit victim : state.getMap().getUnitsInRadius(getOwner().getPosition(), auraRadius)) {
            if (victim == getOwner() || victim.isDead() || victim.getTeam() == getOwner().getTeam()) {
                continue;
            }
            drain(state, victim, auraHealthSteal, 0);
        }
    }

    @Override
    protected void onUpgraded() {
        this.radius = statInt("radius", radius);
        this.healthSteal = statInt("health_steal", healthSteal);
        this.strengthSteal = statInt("str_steal", strengthSteal);
        this.auraRadius = statInt("aura_radius", 0);
        this.auraHealthSteal = statInt("aura_health_steal", 0);
    }

    /**
     * Moves health and strength off a victim and onto Dirge. Deliberately identical in shape to
     * Cripple's transferHealth, including the ordering on each side, which is not cosmetic:
     *
     * - The victim takes the damage BEFORE its ceiling drops. Dropping the ceiling first clamps
     *   current health down to it, and the damage then lands on top - charging a full-health
     *   victim twice over, 10 current health for a 5 steal.
     * - Dirge's ceiling rises BEFORE he is healed into it. HealthPool.setMax never raises current
     *   health on its own, so without the heal he gains a bigger pool and none of the blood that
     *   was supposed to fill it.
     *
     * Net effect either way: the victim loses exactly `health` from both current and maximum, and
     * Dirge gains exactly that much of each.
     */
    private void drain(GameState state, Unit victim, int health, int strength) {
        if (strength > 0) {
            victim.addPermanentModifier(StatModifier.flat(Stat.STRENGTH, -strength, this));
            getOwner().addPermanentModifier(StatModifier.flat(Stat.STRENGTH, strength, this));
        }
        if (health <= 0) {
            return;
        }
        DamageEvent decayDamage = new DamageEvent(getOwner(), victim, health);
        decayDamage.setCauseLabel("Decay");
        victim.takeDamage(state, decayDamage);
        victim.addPermanentModifier(StatModifier.flat(Stat.MAX_HEALTH, -health, this));

        getOwner().addPermanentModifier(StatModifier.flat(Stat.MAX_HEALTH, health, this));
        getOwner().heal(state, health);
    }
}
