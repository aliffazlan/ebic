package com.walnutt.ability.impl;

import java.util.ArrayList;
import java.util.List;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.MultiTarget;
import com.walnutt.ability.target.NoTarget;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.UnitDefinition;
import com.walnutt.game.GameState;
import com.walnutt.map.Tile;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitFactory;

/**
 * Yuki - summons a player-controlled Snow Golem (independent HP, built straight from
 * yuki_golem.json via UnitFactory, same as a drafted unit, which is what gives it its own Move
 * and Attack rather than spending Yuki's).
 *
 * Only one summoning stands at a time: recasting instantly kills whatever is still up first.
 * Upgraded that is a PAIR, raised on two chosen tiles from the weaker yuki_golem_upgrade.json,
 * and resummoning clears both.
 */
public class SnowGolem extends Ability {
    /** The prototype a single golem is built from, and the weaker one the upgraded pair use. */
    private static final String SINGLE_GOLEM = "yuki_golem";
    private static final String PAIRED_GOLEM = "yuki_golem_upgrade";

    /** Every golem this ability currently has standing - one, or two once upgraded. */
    private final List<Unit> activeGolems = new ArrayList<>();
    /** Tiles the cast names. 1 until upgraded, when it becomes a two-tile pick. */
    private int targets = 1;

    public SnowGolem(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 16));
        setRange(definition.getInt("cast_range", 1));
    }

    /**
     * Unlocking this refreshes the cooldown outright. At 16 turns that is most of a match, and
     * the design says so explicitly - an upgrade the player cannot use for another fifteen turns
     * is barely an upgrade.
     */
    @Override
    protected void onUpgraded() {
        this.targets = statInt("targets", targets);
        decreaseCooldown(getMaxCooldown());
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target)) {
            return false;
        }
        if (targets > 1 && target instanceof MultiTarget multi) {
            if (!(multi.primary() instanceof TileTarget first)
                || !(multi.secondary() instanceof TileTarget second)) {
                return false;
            }
            // Distinct: two golems cannot be raised on one tile, they occupy the ground.
            return !first.getTile().getPosition().equals(second.getTile().getPosition())
                && isRaisable(state, first.getTile())
                && isRaisable(state, second.getTile());
        }
        return targets == 1 && target instanceof TileTarget tileTarget
            && isRaisable(state, tileTarget.getTile());
    }

    private boolean isRaisable(GameState state, Tile tile) {
        return tile.isWalkable() && isInRange(state, tile.getPosition());
    }

    /**
     * Every pair of distinct raisable tiles, once upgraded. The default enumeration only builds
     * single-shape candidates and would report this as having no legal targets at all - the same
     * reason Translocation and upgraded Eruption override it. At cast_range 1 that is seven
     * tiles at most, so the pairing is trivial.
     */
    @Override
    public List<Target> getLegalTargets(GameState state) {
        if (targets == 1) {
            return super.getLegalTargets(state);
        }
        List<Target> legal = new ArrayList<>();
        if (owner == null || owner.getPosition() == null || !super.canUse(state, new NoTarget())) {
            return legal;
        }
        List<Tile> raisable = new ArrayList<>();
        for (Tile tile : state.getMap().getTilesInRadius(owner.getPosition(), getRange())) {
            if (isRaisable(state, tile)) {
                raisable.add(tile);
            }
        }
        for (Tile first : raisable) {
            for (Tile second : raisable) {
                if (!first.getPosition().equals(second.getPosition())) {
                    legal.add(new MultiTarget(new TileTarget(first), new TileTarget(second)));
                }
            }
        }
        return legal;
    }

    @Override
    public void onUse(GameState state, Target target) {
        // Resummoning clears the board of every golem still standing, whether that is one or two.
        for (Unit golem : List.copyOf(activeGolems)) {
            if (!golem.isDead()) {
                golem.instantKill(state, owner);
            }
        }
        activeGolems.clear();

        if (target instanceof MultiTarget multi) {
            raise(state, ((TileTarget) multi.primary()).getTile());
            raise(state, ((TileTarget) multi.secondary()).getTile());
        } else {
            raise(state, ((TileTarget) target).getTile());
        }

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }

    /**
     * A golem is a basic unit built through UnitFactory exactly as a drafted one is, which is
     * what gives it its own Move and Attack - it acts on its own rather than spending Yuki's
     * actions. Upgraded, the pair come from the weaker prototype.
     */
    private void raise(GameState state, Tile destination) {
        String prototype = targets > 1 ? PAIRED_GOLEM : SINGLE_GOLEM;
        UnitDefinition golemDefinition = state.getUnitDefinitions().get(prototype);
        if (golemDefinition == null) {
            return;
        }
        Unit golem = UnitFactory.createFromDefinition(golemDefinition, owner.getTeam(),
            state.getAbilityDefinitions());
        state.getMap().moveUnit(golem, destination);
        state.getPlayer(owner.getTeam()).addUnit(golem);
        state.recordSummoner(golem, owner);
        activeGolems.add(golem);
    }
}
