package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.map.Position;
import com.walnutt.unit.SummonedUnit;
import com.walnutt.unit.Unit;

import java.util.ArrayList;
import java.util.List;

/** Zenith - flat-damage ranged beam; can target a unit or an empty tile (a "miss" cast, e.g. to trigger Pylons). */
public class OrbitalBeam extends Ability {
    private int damage;
    /** Upgrade: one extra global beam per pylon standing. */
    private boolean beamsPerPylon;

    public OrbitalBeam(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 2));
        setRange(definition.getInt("cast_range", 5));
        this.damage = definition.getInt("damage", 40);
    }

    public int getDamage() {
        return damage;
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target)) {
            return false;
        }
        Position targetPosition = resolvePosition(target);
        return targetPosition != null && isInRange(state, targetPosition);
    }

    @Override
    protected void onUpgraded() {
        this.damage = statInt("damage", damage);
        this.beamsPerPylon = true;
    }

    @Override
    public void onUse(GameState state, Target target) {
        if (target instanceof UnitTarget unitTarget) {
            Unit victim = unitTarget.getUnit();
            if (!victim.isDead()) {
                DamageEvent event = new DamageEvent(owner, victim, damage);
                event.setCauseLabel("Orbital Beam");
                victim.takeDamage(state, event);
            }
        }
        if (beamsPerPylon) {
            fireGlobalVolley(state);
        }

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }

    private Position resolvePosition(Target target) {
        if (target instanceof UnitTarget unitTarget) {
            return unitTarget.getUnit().getPosition();
        }
        if (target instanceof TileTarget tileTarget) {
            return tileTarget.getTile().getPosition();
        }
        return null;
    }

    /**
     * Upgrade: one extra beam per pylon, each aimed at a random enemy ANYWHERE on the map
     * rather than beside its pylon. These are on top of the volley the pylons already fire
     * for themselves (PylonOrbitalBeam), which is unchanged.
     *
     * Targets are rolled independently, so one unlucky enemy really can be struck several
     * times - the JSON says so, and re-rolling for distinctness would quietly weaken it.
     *
     * The enemy list is snapshotted first: a beam can kill, and the pool must not shrink
     * halfway through a volley that was priced on how many pylons are standing.
     */
    private void fireGlobalVolley(GameState state) {
        int pylons = 0;
        for (Unit unit : state.getAllActiveUnits()) {
            if (unit instanceof SummonedUnit summon && summon.getSummoner() == owner && !unit.isDead()
                && unit.getAbilities().stream().anyMatch(PylonOrbitalBeam.class::isInstance)) {
                pylons++;
            }
        }
        if (pylons <= 0) {
            return;
        }
        List<Unit> enemies = new ArrayList<>();
        for (Unit unit : state.getAllActiveUnits()) {
            if (unit.getTeam() != owner.getTeam() && !unit.isDead()) {
                enemies.add(unit);
            }
        }
        if (enemies.isEmpty()) {
            return;
        }
        for (int i = 0; i < pylons; i++) {
            Unit victim = enemies.get(state.getRandom().nextInt(enemies.size()));
            if (victim.isDead()) {
                continue;
            }
            DamageEvent beam = new DamageEvent(owner, victim, damage);
            beam.setCauseLabel("Orbital Beam");
            victim.takeDamage(state, beam);
        }
    }
}
