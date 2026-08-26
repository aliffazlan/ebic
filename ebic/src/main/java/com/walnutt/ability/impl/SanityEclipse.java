package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.OrbEffect;
import com.walnutt.game.GameState;
import com.walnutt.map.Tile;

/** Harbinger - launches a delayed orb that explodes for intelligence-difference damage in an area. */
public class SanityEclipse extends Ability {
    private final int delay;
    private final int radius;
    private final double intDiffMultiplier;

    public SanityEclipse(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 9));
        setRange(definition.getInt("cast_range", 4));
        this.delay = definition.getInt("delay", 1);
        this.radius = definition.getInt("radius", 1);
        this.intDiffMultiplier = definition.getDouble("int_diff_dmg", 1);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target)) {
            return false;
        }
        if (!(target instanceof TileTarget tileTarget)) {
            return false;
        }
        return isInRange(state, tileTarget.getTile().getPosition());
    }

    @Override
    public void onUse(GameState state, Target target) {
        Tile tile = ((TileTarget) target).getTile();
        owner.addEffect(new OrbEffect(owner, tile.getPosition(), delay, radius, intDiffMultiplier));

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
