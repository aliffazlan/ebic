package com.walnutt.ability.impl;

import java.util.ArrayList;
import java.util.List;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.OvergrowthEffect;
import com.walnutt.game.GameState;
import com.walnutt.map.Tile;
import com.walnutt.unit.Unit;

/** Branch - rings a tile with Branchlings, walling it off and grinding down whoever is inside. */
public class Overgrowth extends Ability {
    private final int duration;

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
}
