package com.walnutt.ability.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/**
 * Joker - a bolt that hits its target, then ricochets to a random unit beside whatever it
 * last hit, growing stronger with every jump.
 *
 * Deliberately has NO team filter past the initial target: the bolt is as happy to land on
 * Joker's own army, or on Joker himself, as on the enemy. That is the whole design - it is
 * a large amount of damage you aim at an enemy cluster that nothing of yours is touching,
 * and a liability anywhere else.
 */
public class PerplexingShot extends Ability {
    private final int damage;
    private final int bonusDamage;
    private final int bounces;

    public PerplexingShot(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 3));
        setRange(definition.getInt("cast_range", 2));
        this.damage = definition.getInt("damage", 30);
        this.bonusDamage = definition.getInt("bonus_damage", 20);
        this.bounces = definition.getInt("bounces", 2);
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
        Unit current = ((UnitTarget) target).getUnit();
        // Insertion-ordered so the chain is readable when a test or the combat log walks it.
        Set<Unit> alreadyHit = new LinkedHashSet<>();
        int nextDamage = damage;

        // Charged up front so an early fizzle still costs the move and the cooldown -
        // a bolt that found nothing to bounce to was still fired.
        state.spendMoves(getMoveCost(state));
        resetToMax();

        for (int jump = 0; jump <= bounces; jump++) {
            alreadyHit.add(current);
            strike(state, current, nextDamage);
            nextDamage += bonusDamage;

            Unit next = pickBounceTarget(state, current, alreadyHit);
            if (next == null) {
                return; // nothing left beside it - the bolt fizzles out early
            }
            current = next;
        }
    }

    /**
     * A random unhit unit within one tile of {@code from}, or null if there is none.
     *
     * Radius 1 rather than the six neighbouring tiles, so a unit sharing a tile with the
     * last victim counts as beside it - tiles stack whenever an ability forces them to,
     * and Killer Drone already treats same-tile as adjacent for the same reason. The
     * dead-unit guard matters because a bounce can kill, and a corpse keeps its position
     * (GameState.removeUnit detaches from the tile but leaves the field set).
     */
    private Unit pickBounceTarget(GameState state, Unit from, Set<Unit> alreadyHit) {
        List<Unit> candidates = new ArrayList<>();
        for (Unit unit : state.getMap().getUnitsInRadius(from.getPosition(), 1)) {
            if (!unit.isDead() && !alreadyHit.contains(unit)) {
                candidates.add(unit);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(state.getRandom().nextInt(candidates.size()));
    }

    private void strike(GameState state, Unit target, int amount) {
        DamageEvent event = new DamageEvent(owner, target, amount);
        event.setCauseLabel(getName());
        target.takeDamage(state, event);
    }
}
