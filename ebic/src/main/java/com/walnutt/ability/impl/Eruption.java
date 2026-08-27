package com.walnutt.ability.impl;

import java.util.List;

import com.walnutt.ability.Ability;
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
    private final int duration;
    private final int initialBurnStacks;
    private final int burnStacks;

    public Eruption(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 3));
        setRange(definition.getInt("cast_range", 3));
        this.duration = definition.getInt("duration", 7);
        this.initialBurnStacks = definition.getInt("burn_stacks_init", 2);
        this.burnStacks = definition.getInt("burn_stacks", 1);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target)) {
            return false;
        }
        if (!(target instanceof TileTarget tileTarget)) {
            return false;
        }
        Position position = tileTarget.getTile().getPosition();
        // Deliberately not requiring a walkable/empty tile - igniting the ground an enemy
        // is standing on is the point of the ability. Ground that is already alight is
        // refused though: a second patch on one tile just burns whoever stands there
        // twice per round, so re-casting there is a wasted cooldown.
        return state.getMap().getTile(position) != null
            && isInRange(state, position)
            && !BurningGroundEffect.isBurning(state, position);
    }

    @Override
    public void onUse(GameState state, Target target) {
        Tile ground = ((TileTarget) target).getTile();

        for (Unit occupant : List.copyOf(ground.getOccupants())) {
            if (occupant.isDead() || occupant.getTeam() == owner.getTeam()) {
                continue;
            }
            BurnEffect.apply(state, occupant, owner, initialBurnStacks);
        }
        owner.addEffect(new BurningGroundEffect(owner, ground.getPosition(), duration, burnStacks));

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
