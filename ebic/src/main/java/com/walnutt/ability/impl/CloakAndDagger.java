package com.walnutt.ability.impl;

import java.util.ArrayList;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.CloakEffect;
import com.walnutt.game.GameState;
import com.walnutt.map.Tile;
import com.walnutt.unit.Unit;

/**
 * Evayne - vanishes onto a targeted tile (occupied or not - forces a stack if
 * someone's already there) and ambushes every enemy caught on it.
 */
public class CloakAndDagger extends Ability {
    private final int duration;
    private final double damagePenalty;

    public CloakAndDagger(AbilityDefinition definition) {
        super(definition.name(), definition.description(), false);
        setMaxCooldown(definition.getInt("cooldown", 5));
        setRange(definition.getInt("cast_range", 2));
        this.duration = definition.getInt("duration", 2);
        this.damagePenalty = definition.getDouble("dmg_penalty", definition.getDouble("dmg_penality", 0.4));
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target) || !state.canSpendMoves(getMoveCost(state))) {
            return false;
        }
        if (!(target instanceof TileTarget tileTarget)) {
            return false;
        }
        return state.getMap().getDistance(owner.getPosition(), tileTarget.getTile().getPosition()) <= getRange();
    }

    @Override
    public void onUse(GameState state, Target target) {
        Tile destination = ((TileTarget) target).getTile();
        state.getMap().moveUnit(owner, destination); // forced stack if occupied - bypasses isWalkable() on purpose

        CloakEffect cloak = new CloakEffect(duration, damagePenalty);
        owner.addEffect(cloak);

        // Snapshot occupants first - ambushAttack can kill/remove units mid-iteration.
        for (Unit occupant : new ArrayList<>(destination.getOccupants())) {
            if (occupant != owner && occupant.getTeam() != owner.getTeam() && !occupant.isDead()) {
                cloak.ambushAttack(state, occupant);
            }
        }

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
