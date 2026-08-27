package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.SproutEffect;
import com.walnutt.game.GameState;
import com.walnutt.map.Tile;
import com.walnutt.unit.Unit;

/**
 * Branch - plants a Branchling anywhere on the map and teleports to it a turn later.
 *
 * The cooldown is spent at cast time and is never refunded, so killing the Branchling
 * denies the teleport but doesn't hand the ability back.
 */
public class Sprout extends Ability {
    private final int delay;
    private final int barrierHp;
    private final int barrierDuration;

    public Sprout(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 6));
        setRange(UNLIMITED_RANGE); // castable anywhere on the map - no distance check in canUse
        this.delay = definition.getInt("delay", 1);
        this.barrierHp = definition.getInt("barrier", 20);
        this.barrierDuration = definition.getInt("barrier_duration", 2);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target)) {
            return false;
        }
        if (!(target instanceof TileTarget tileTarget)) {
            return false;
        }
        // "Anywhere on the map" - no range check at all, same as Zenith's Pylon.
        return BranchlingSpawner.canSpawnOn(tileTarget.getTile());
    }

    @Override
    public void onUse(GameState state, Target target) {
        Tile destination = ((TileTarget) target).getTile();
        Unit branchling = BranchlingSpawner.spawn(state, owner, destination);
        owner.addEffect(new SproutEffect(owner, branchling, delay, barrierHp, barrierDuration));

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
