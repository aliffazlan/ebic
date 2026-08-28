package com.walnutt.ability.impl;

import java.util.ArrayList;
import java.util.List;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.MultiTarget;
import com.walnutt.ability.target.NoTarget;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.game.GameState;
import com.walnutt.map.Tile;
import com.walnutt.unit.Unit;

/**
 * Maxwell gadget - picks a unit up and sets it down elsewhere.
 *
 * The first ability to use {@link MultiTarget}: the cast names both a unit and a
 * destination tile. cast_range bounds how far the unit may be from Maxwell; the
 * self/ally/enemy ranges bound how far it may be displaced from where it stands, so
 * repositioning yourself reaches further than shoving an enemy.
 */
public class Translocation extends Ability {
    private final int selfRange;
    private final int allyRange;
    private final int enemyRange;

    public Translocation(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 5));
        setRange(definition.getInt("cast_range", 3));
        this.selfRange = definition.getInt("self_range", 4);
        this.allyRange = definition.getInt("ally_range", 3);
        this.enemyRange = definition.getInt("enemy_range", 2);
    }

    /** How far this particular unit may be displaced - the ability's whole point. */
    public int displacementRangeFor(Unit unit) {
        if (unit == owner) {
            return selfRange;
        }
        return unit.getTeam() == owner.getTeam() ? allyRange : enemyRange;
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target)) {
            return false;
        }
        if (!(target instanceof MultiTarget multi)
            || !(multi.primary() instanceof UnitTarget unitTarget)
            || !(multi.secondary() instanceof TileTarget tileTarget)) {
            return false;
        }
        return isLegalPair(state, unitTarget.getUnit(), tileTarget.getTile());
    }

    private boolean isLegalPair(GameState state, Unit subject, Tile destination) {
        if (subject.isDead() || subject.getPosition() == null || destination == null) {
            return false;
        }
        if (!isInRange(state, subject.getPosition())) {
            return false;
        }
        if (!destination.isWalkable()) {
            return false;
        }
        int displacement = state.getMap().getDistance(subject.getPosition(), destination.getPosition());
        // Displacement 0 is the unit's own tile: a legal-looking cast that does nothing.
        return displacement > 0 && displacement <= displacementRangeFor(subject);
    }

    /**
     * Ability's default enumeration only ever builds single-shape candidates, so it would
     * report this ability as having no legal targets at all. Enumerating the pairs
     * directly is also far cheaper than a blind cross-product: only tiles within the
     * subject's own displacement range are ever considered.
     */
    @Override
    public List<Target> getLegalTargets(GameState state) {
        List<Target> legal = new ArrayList<>();
        // Ability.canUse's baseline checks (readiness, silence, move points) ignore the
        // target entirely, so a NoTarget stands in for "can this be cast at all right now".
        if (owner == null || owner.getPosition() == null || !super.canUse(state, new NoTarget())) {
            return legal;
        }
        for (Unit subject : state.getAllActiveUnits()) {
            if (subject.isDead() || subject.getPosition() == null || !isInRange(state, subject.getPosition())) {
                continue;
            }
            for (Tile destination : state.getMap().getTilesInRadius(subject.getPosition(),
                    displacementRangeFor(subject))) {
                if (isLegalPair(state, subject, destination)) {
                    legal.add(new MultiTarget(new UnitTarget(subject), new TileTarget(destination)));
                }
            }
        }
        return legal;
    }

    @Override
    public void onUse(GameState state, Target target) {
        MultiTarget multi = (MultiTarget) target;
        Unit subject = ((UnitTarget) multi.primary()).getUnit();
        Tile destination = ((TileTarget) multi.secondary()).getTile();

        // A forced relocation, not the subject's own move: no markMoved, and no
        // Pre/PostMoveEvent - matching Manifestation, Dislocation and Cloak and Dagger.
        state.getMap().moveUnit(subject, destination);

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
