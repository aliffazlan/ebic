package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.FeastEffect;
import com.walnutt.game.GameState;
import com.walnutt.map.Tile;

/** Grivath - leaps to a tile and roots himself there, gaining several automatic lifesteal attacks per turn. */
public class Feast extends Ability {
    private final int duration;
    private final int attacks;
    private final double lifesteal;
    private final int rootDuration;

    public Feast(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 7));
        setRange(definition.getInt("cast_range", 3));
        this.duration = definition.getInt("duration", 3);
        this.attacks = definition.getInt("attacks", 2);
        this.lifesteal = definition.getDouble("lifesteal", 0.5);
        this.rootDuration = definition.getInt("root_duration", 1);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target)) {
            return false;
        }
        if (!(target instanceof TileTarget tileTarget)) {
            return false;
        }
        Tile tile = tileTarget.getTile();
        return tile.isWalkable() && isInRange(state, tile.getPosition());
    }

    @Override
    public void onUse(GameState state, Target target) {
        Tile destination = ((TileTarget) target).getTile();
        state.getMap().moveUnit(owner, destination);
        owner.addEffect(new FeastEffect(duration, attacks, lifesteal, rootDuration));

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
