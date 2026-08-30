package com.walnutt.ability.impl;

import java.util.List;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.NoTarget;
import com.walnutt.ability.target.Target;
import com.walnutt.combat.Attribute;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Valor - self-centered AOE: damages enemies if outnumbering them, heals allies otherwise. */
public class OverwhelmingOdds extends Ability {
    private int radius;
    private double diffDamage;
    private double diffHeal;
    /** Upgrade: a board-wide passive on Valor's own attacks. Both 0 until upgraded. */
    private int passiveDamage;
    private int passiveHeal;

    public OverwhelmingOdds(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 5));
        this.radius = definition.getInt("radius", 2);
        this.diffDamage = definition.getDouble("diff_dmg", 20);
        this.diffHeal = definition.getDouble("diff_heal", 20);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        return super.canUse(state, target) && target instanceof NoTarget;
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
                    DamageEvent event = new DamageEvent(owner, unit, damage);
                    event.setCauseLabel("Overwhelming Odds");
                    unit.takeDamage(state, event);
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

    @Override
    protected void onUpgraded() {
        this.radius = statInt("radius", radius);
        this.diffDamage = stat("diff_dmg", diffDamage);
        this.diffHeal = stat("diff_heal", diffHeal);
        this.passiveDamage = statInt("passive_damage", 0);
        this.passiveHeal = statInt("passive_heal", 0);
    }

    /**
     * The upgrade's passive half: every attack Valor makes is worth more when his side has the
     * numbers, and worth some health back when it does not. Counts the whole board rather than
     * this ability's radius, and applies to Valor alone.
     *
     * Hooked on the pre-application damage event rather than onPostAttack for two reasons: a
     * Counterstrike counter deliberately never publishes PostAttackEvent (it would counter the
     * counter), and adding the bonus here lands it AFTER Counterstrike's own multiplier, so the
     * counter's damage reduction never eats into it - both exactly as designed.
     *
     * Restricted to attacks by requiring the attribute matchup, which only an encounter sets;
     * Valor's ability damage is not an attack and is left alone.
     */
    @Override
    public void onIncomingDamage(GameState state, DamageEvent event) {
        Unit valor = getOwner();
        if (valor == null || valor.isDead() || event.getSource() != valor || event.getDamage() <= 0) {
            return;
        }
        if (passiveDamage <= 0 && passiveHeal <= 0) {
            return;
        }
        if (event.getAttackerAttribute() == null || event.getDefenderAttribute() == null) {
            return;
        }
        long allies = 0;
        long enemies = 0;
        for (Unit unit : state.getAllActiveUnits()) {
            if (unit.isDead()) {
                continue;
            }
            if (unit.getTeam() == valor.getTeam()) {
                allies++;
            } else {
                enemies++;
            }
        }
        long advantage = allies - enemies;
        if (advantage > 0) {
            event.modifyDamage((int) (passiveDamage * advantage));
        } else if (advantage < 0) {
            valor.heal(state, (int) (passiveHeal * -advantage));
        }
    }
}
