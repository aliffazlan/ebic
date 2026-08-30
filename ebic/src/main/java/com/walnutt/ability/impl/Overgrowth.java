package com.walnutt.ability.impl;

import java.util.ArrayList;
import java.util.List;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.Effect;
import com.walnutt.effect.impl.OvergrowthEffect;
import com.walnutt.event.DeathEvent;
import com.walnutt.game.GameState;
import com.walnutt.map.Tile;
import com.walnutt.unit.Unit;

/** Branch - rings a tile with Branchlings, walling it off and grinding down whoever is inside. */
public class Overgrowth extends Ability {
    private int duration;
    /** Upgrade: turns a Branchigga stands for. 0 until upgraded, when none are grown. */
    private int branchiggaDuration;

    public Overgrowth(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 4));
        setRange(definition.getInt("cast_range", 3));
        this.duration = definition.getInt("duration", 3);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target)) {
            return false;
        }
        if (!(target instanceof TileTarget tileTarget)) {
            return false;
        }
        // The centre tile itself is never planted on, so it may well be occupied - ringing
        // an enemy where they stand is exactly the intended use.
        return state.getMap().getTile(tileTarget.getTile().getPosition()) != null
            && isInRange(state, tileTarget.getTile().getPosition());
    }

    @Override
    public void onUse(GameState state, Target target) {
        Tile centre = ((TileTarget) target).getTile();

        List<Unit> spawned = new ArrayList<>();
        // getAdjacentTiles is exactly the six-neighbour ring and is already map-edge safe.
        for (Tile ring : state.getMap().getAdjacentTiles(centre.getPosition())) {
            if (BranchlingSpawner.canSpawnOn(ring)) {
                spawned.add(BranchlingSpawner.spawn(state, owner, ring));
            }
        }
        if (!spawned.isEmpty()) {
            owner.addEffect(new OvergrowthEffect(spawned, duration));
        }

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }

    @Override
    protected void onUpgraded() {
        this.duration = statInt("duration", duration);
        this.branchiggaDuration = statInt("branchigga_duration", 0);
    }

    /**
     * Upgrade: a Branchling KILLED by something grows a Branchigga where it fell.
     *
     * Both of the design's exclusions fall out of this rather than needing a check of their own.
     * Sprout's Branchling is not in any OvergrowthEffect's list, so it is never one of "its own".
     * And a Branchling that simply withered was removed by OvergrowthEffect.onExpire with
     * RemovalReason.DESPAWN, which publishes no DeathEvent at all - so only a real death gets
     * this far.
     *
     * A Branchigga's own death grows nothing: it is not a Branchling, and never joins the list.
     */
    @Override
    public void onDeath(GameState state, DeathEvent event) {
        if (branchiggaDuration <= 0 || owner == null) {
            return;
        }
        Unit fallen = event.unit();
        if (fallen == null || fallen.getPosition() == null || !isOwnBranchling(fallen)) {
            return;
        }
        Tile ground = state.getMap().getTile(fallen.getPosition());
        if (ground != null) {
            BranchlingSpawner.spawnBranchigga(state, owner, ground, branchiggaDuration);
        }
    }

    /**
     * Every grove Branch currently sustains, not just the first: getActiveEffect returns one
     * match, and a second Overgrowth can overlap the first if the cooldown ever drops below the
     * duration.
     */
    private boolean isOwnBranchling(Unit candidate) {
        for (Effect effect : owner.getEffects()) {
            if (effect instanceof OvergrowthEffect grove && grove.getBranchlings().contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
