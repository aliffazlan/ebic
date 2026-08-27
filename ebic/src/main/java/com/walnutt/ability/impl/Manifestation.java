package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.ManifestationDebuffEffect;
import com.walnutt.game.GameState;
import com.walnutt.map.Tile;
import com.walnutt.unit.Unit;

/**
 * Mercurial - teleports to a free tile anywhere on the map, debuffing whoever's now
 * adjacent.
 *
 * The destination must border an enemy: the range is still global, but it's a strike
 * rather than a general-purpose escape, so it can only land where it actually does
 * something.
 */
public class Manifestation extends Ability {
    private final double damageReduction;
    private final int duration;

    public Manifestation(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 5));
        setRange(UNLIMITED_RANGE); // castable anywhere on the map - no distance check in canUse
        this.damageReduction = definition.getDouble("dmg_reduction", 0.5);
        this.duration = definition.getInt("duration", 2);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target)) {
            return false;
        }
        if (!(target instanceof TileTarget tileTarget)) {
            return false;
        }
        Tile destination = tileTarget.getTile();
        return destination.isWalkable() && hasAdjacentEnemy(state, destination);
    }

    /** Checks the DESTINATION's neighbours, not the caster's - this runs before the teleport. */
    private boolean hasAdjacentEnemy(GameState state, Tile destination) {
        return !state.getMap().getAdjacentUnits(destination.getPosition(),
            u -> u.getTeam() != owner.getTeam() && !u.isDead()).isEmpty();
    }

    @Override
    public void onUse(GameState state, Target target) {
        Tile destination = ((TileTarget) target).getTile();
        state.getMap().moveUnit(owner, destination);

        for (Unit enemy : state.getMap().getAdjacentUnits(owner.getPosition(),
                u -> u.getTeam() != owner.getTeam() && !u.isDead())) {
            enemy.addEffect(new ManifestationDebuffEffect(duration, damageReduction));
        }

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
