package com.walnutt.ability.impl;

import java.util.ArrayList;
import java.util.List;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.MultiTarget;
import com.walnutt.ability.target.NoTarget;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.BurnEffect;
import com.walnutt.effect.impl.BurningGroundEffect;
import com.walnutt.game.GameState;
import com.walnutt.map.Position;
import com.walnutt.map.Tile;
import com.walnutt.unit.Unit;

/** Ember - sets a tile alight, igniting whoever stands on it now and whoever lingers later. */
public class Eruption extends Ability {
    private int duration;
    private int initialBurnStacks;
    private int burnStacks;
    /** Tiles set alight per cast. 1 until upgraded, when it becomes a two-tile pick. */
    private int targets = 1;

    public Eruption(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 3));
        setRange(definition.getInt("cast_range", 3));
        this.duration = definition.getInt("duration", 7);
        this.initialBurnStacks = definition.getInt("burn_stacks_init", 2);
        this.burnStacks = definition.getInt("burn_stacks", 1);
    }

    @Override
    protected void onUpgraded() {
        this.duration = statInt("duration", duration);
        this.initialBurnStacks = statInt("burn_stacks_init", initialBurnStacks);
        this.burnStacks = statInt("burn_stacks", burnStacks);
        this.targets = statInt("targets", targets);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target)) {
            return false;
        }
        // Upgraded, the cast names two tiles at once. They must be DIFFERENT: the base rework
        // already lets a re-cast refresh burning ground, so aiming both halves at one tile
        // would be a legal-looking cast that spends a cooldown to do a single tile's work.
        if (targets > 1 && target instanceof MultiTarget multi) {
            if (!(multi.primary() instanceof TileTarget first)
                || !(multi.secondary() instanceof TileTarget second)) {
                return false;
            }
            return !first.getTile().getPosition().equals(second.getTile().getPosition())
                && isCastableTile(state, first.getTile().getPosition())
                && isCastableTile(state, second.getTile().getPosition());
        }
        return targets == 1 && target instanceof TileTarget tileTarget
            && isCastableTile(state, tileTarget.getTile().getPosition());
    }

    /**
     * Deliberately not requiring a walkable or empty tile - igniting the ground an enemy is
     * standing on is the point of the ability. Ground already alight is fair game too, since
     * re-lighting it refreshes how long it burns.
     */
    private boolean isCastableTile(GameState state, Position position) {
        return state.getMap().getTile(position) != null && isInRange(state, position);
    }

    /**
     * Every pair of distinct castable tiles, once upgraded. The default enumeration only ever
     * builds single-shape candidates, so it would report a two-tile cast as having no legal
     * targets at all - the same reason Translocation overrides this.
     *
     * Cheap enough to enumerate outright: at cast_range 3 that is about 37 tiles, so ~1300
     * ordered pairs, which is the same order as Translocation already serialises.
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
        List<Tile> castable = new ArrayList<>();
        for (Tile tile : state.getMap().getTilesInRadius(owner.getPosition(), getRange())) {
            if (isCastableTile(state, tile.getPosition())) {
                castable.add(tile);
            }
        }
        for (Tile first : castable) {
            for (Tile second : castable) {
                if (!first.getPosition().equals(second.getPosition())) {
                    legal.add(new MultiTarget(new TileTarget(first), new TileTarget(second)));
                }
            }
        }
        return legal;
    }

    @Override
    public void onUse(GameState state, Target target) {
        if (target instanceof MultiTarget multi) {
            ignite(state, ((TileTarget) multi.primary()).getTile());
            ignite(state, ((TileTarget) multi.secondary()).getTile());
        } else {
            ignite(state, ((TileTarget) target).getTile());
        }

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }

    /**
     * Sets one tile alight. Ground that is already burning is REFRESHED rather than given a
     * second fire - two patches on one tile would burn whoever stands there twice a round,
     * which is not what "re-lighting refreshes how long it burns" means.
     *
     * Only Ember's own fires are refreshed; another Ember's patch on the same tile is left
     * alone and a fresh one is laid down beside it.
     */
    private void ignite(GameState state, Tile ground) {
        for (Unit occupant : List.copyOf(ground.getOccupants())) {
            if (occupant.isDead() || occupant.getTeam() == owner.getTeam()) {
                continue;
            }
            BurnEffect.apply(state, occupant, owner, initialBurnStacks);
        }
        for (BurningGroundEffect existing : BurningGroundEffect.activeGrounds(state)) {
            if (existing.getOwner() == owner && existing.getTile().equals(ground.getPosition())) {
                existing.setRemainingTurns(duration);
                return;
            }
        }
        owner.addEffect(new BurningGroundEffect(owner, ground.getPosition(), duration, burnStacks));
    }
}
