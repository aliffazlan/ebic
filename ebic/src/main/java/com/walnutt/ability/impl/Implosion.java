package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.combat.CombatEngine;
import com.walnutt.combat.WeightedEncounter;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Wei - damages the target (and adjacent enemies) proportional to their summed active-ability cooldowns. */
public class Implosion extends Ability {
    private int radius;
    private double damagePerCooldown;
    private int freeAttacks;

    public Implosion(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 5));
        setRange(definition.getInt("cast_range", 2));
        this.radius = definition.getInt("radius", 1);
        this.damagePerCooldown = definition.getDouble("dmg_per_cooldown", 12);
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
    protected void onUpgraded() {
        this.radius = statInt("radius", radius);
        this.damagePerCooldown = stat("dmg_per_cooldown", damagePerCooldown);
        this.freeAttacks = statInt("free_attacks", 0);
    }

    @Override
    public void onUse(GameState state, Target target) {
        Unit primary = ((UnitTarget) target).getUnit();

        // Upgrade: the free attacks come FIRST, so whatever cooldowns they drive up - Energy
        // Break's above all - are counted by the implosion that follows.
        for (int i = 0; i < freeAttacks && !primary.isDead() && !owner.isDead(); i++) {
            CombatEngine.performAttack(state, new WeightedEncounter(owner, primary));
        }
        if (primary.isDead()) {
            state.spendMoves(getMoveCost(state));
            resetToMax();
            return;
        }

        strike(state, primary);
        for (Unit splash : state.getMap().getUnitsInRadius(primary.getPosition(), radius)) {
            if (splash != primary && splash.getTeam() != owner.getTeam() && !splash.isDead()) {
                strike(state, splash);
            }
        }

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }

    private void strike(GameState state, Unit target) {
        int totalCooldown = target.getAbilities().stream()
            .filter(a -> !a.isPassive())
            .mapToInt(Ability::getCurrentCooldown)
            .sum();
        int damage = (int) Math.round(totalCooldown * damagePerCooldown);
        if (damage > 0) {
            DamageEvent event = new DamageEvent(owner, target, damage);
            event.setCauseLabel("Implosion");
            target.takeDamage(state, event);
        }
    }
}
