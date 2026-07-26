package com.walnutt.ability.impl;

import java.util.List;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.NoTarget;
import com.walnutt.ability.target.Target;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Valor - self-centered AOE: damages enemies if outnumbering them, heals allies otherwise. */
public class OverwhelmingOdds extends Ability {
    private final int radius;
    private final double diffDamage;
    private final double diffHeal;

    public OverwhelmingOdds(AbilityDefinition definition) {
        super(definition.name(), definition.description(), false);
        setMaxCooldown(definition.getInt("cooldown", 5));
        this.radius = definition.getInt("radius", 2);
        this.diffDamage = definition.getDouble("diff_dmg", 20);
        this.diffHeal = definition.getDouble("diff_heal", 20);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        return super.canUse(state, target)
            && state.canSpendMoves(getMoveCost(state))
            && target instanceof NoTarget;
    }

    @Override
    public void onUse(GameState state, Target target) {
        List<Unit> unitsInArea = state.getMap().getUnitsInRadius(owner.getPosition(), radius);
        long allies = unitsInArea.stream().filter(u -> u.getTeam() == owner.getTeam()).count();
        long enemies = unitsInArea.size() - allies;
        long diff = allies - enemies;

        if (diff > 0) {
            int damage = (int) Math.round(diff * diffDamage);
            for (Unit unit : unitsInArea) {
                if (unit.getTeam() != owner.getTeam()) {
                    unit.takeDamage(state, new DamageEvent(owner, unit, damage));
                }
            }
        } else if (diff < 0) {
            int healAmount = (int) Math.round(-diff * diffHeal);
            for (Unit unit : unitsInArea) {
                if (unit.getTeam() == owner.getTeam()) {
                    unit.heal(state, healAmount);
                }
            }
        }

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
