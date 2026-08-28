package com.walnutt.ai;

import java.util.ArrayList;
import java.util.List;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.MultiTarget;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.game.GameState;
import com.walnutt.map.Position;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitType;

/**
 * Everything an {@link AbilityHint} needs, plus the queries hints kept re-deriving.
 * Keeping the helpers here is what lets each hint stay a couple of readable lines
 * instead of twenty lines of target unwrapping.
 */
public record HintContext(GameState state, Unit user, Ability ability, Target target,
                          BotConfig config, PositionEvaluator evaluator) {

    /** The targeted unit, or null when this ability aims at a tile or at nothing. */
    public Unit targetUnit() {
        return primary() instanceof UnitTarget unitTarget ? unitTarget.getUnit() : null;
    }

    /**
     * Where this cast lands: the target unit's tile, the target tile, or the caster's own
     * tile. For a MultiTarget it is the DESTINATION (Translocation's whole point is where
     * the unit ends up), while targetUnit() above reports who is being moved.
     */
    public Position aimPosition() {
        Target aim = target instanceof MultiTarget multi ? multi.secondary() : target;
        if (aim instanceof UnitTarget unitTarget) {
            return unitTarget.getUnit().getPosition();
        }
        if (aim instanceof TileTarget tileTarget) {
            return tileTarget.getTile().getPosition();
        }
        return user.getPosition();
    }

    /**
     * The subject of the cast, unwrapping a MultiTarget. Without this a two-part ability
     * would look target-less to every hint, including the generic fallback, which would
     * then score it as a tile cast and never notice it was aimed at a unit at all.
     */
    private Target primary() {
        return target instanceof MultiTarget multi ? multi.primary() : target;
    }

    public boolean isEnemy(Unit unit) {
        return unit != null && unit.getTeam() != user.getTeam();
    }

    public boolean targetsEnemy() {
        return isEnemy(targetUnit());
    }

    public boolean targetsAlly() {
        Unit unit = targetUnit();
        return unit != null && unit.getTeam() == user.getTeam();
    }

    /** Living units within {@code radius} of where this cast lands, on the given side. */
    public List<Unit> unitsNearAim(int radius, boolean enemies) {
        List<Unit> result = new ArrayList<>();
        Position aim = aimPosition();
        if (aim == null) {
            return result;
        }
        for (Unit unit : state.getMap().getUnitsInRadius(aim, radius)) {
            if (unit.isDead() || unit.getPosition() == null) {
                continue;
            }
            if (isEnemy(unit) == enemies) {
                result.add(unit);
            }
        }
        return result;
    }

    /** 0 at full health, 1 at death - how much of a target's health is already gone. */
    public double woundedFraction(Unit unit) {
        int max = unit.getMaxHealth();
        if (max <= 0) {
            return 0;
        }
        return 1.0 - ((double) unit.getHealth() / max);
    }

    /** How much the bot wants this particular enemy gone, independent of how it would do it. */
    public double targetPriority(Unit unit) {
        if (unit == null) {
            return 1.0;
        }
        double typeWeight = switch (unit.getUnitType()) {
            case CHAMPION -> config.championTargetWeight();
            case ELITE -> config.eliteTargetWeight();
            case BASIC -> config.basicTargetWeight();
        };
        return typeWeight + woundedFraction(unit) * (config.woundedTargetWeight() / 10.0);
    }

    public boolean isChampion(Unit unit) {
        return unit != null && unit.getUnitType() == UnitType.CHAMPION;
    }
}
