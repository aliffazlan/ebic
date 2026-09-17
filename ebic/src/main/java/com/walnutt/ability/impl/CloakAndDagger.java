package com.walnutt.ability.impl;

import java.util.ArrayList;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.CloakEffect;
import com.walnutt.event.PostMoveEvent;
import com.walnutt.game.GameState;
import com.walnutt.map.Position;
import com.walnutt.map.Tile;
import com.walnutt.unit.Unit;

/**
 * Evayne - vanishes onto a targeted tile (occupied or not - forces a stack if
 * someone's already there) and ambushes every enemy caught on it.
 */
public class CloakAndDagger extends Ability {
    private int duration;
    private double damagePenalty;
    private int ambushControlDuration;

    public CloakAndDagger(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 5));
        setRange(definition.getInt("cast_range", 2));
        this.duration = definition.getInt("duration", 2);
        this.damagePenalty = definition.getDouble("dmg_penalty", 0.4);
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
        Tile destination = ((TileTarget) target).getTile();
        Position from = owner.getPosition();
        state.getMap().moveUnit(owner, destination); // forced stack if occupied - bypasses isWalkable() on purpose
        // A forced relocation, not a chosen Move: no markMoved, no PreMoveEvent (nothing should
        // be able to cancel it) - but PostMoveEvent still fires so anything tracking this unit's
        // position (a Feast latch, Cloak's own onMove ambush) sees it, same as an ordinary step.
        state.getEventBus().publish(state, new PostMoveEvent(owner, from, destination.getPosition()));

        CloakEffect cloak = new CloakEffect(duration, damagePenalty);
        cloak.setAmbushControlDuration(ambushControlDuration);
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

    @Override
    protected void onUpgraded() {
        this.duration = statInt("duration", duration);
        this.damagePenalty = stat("dmg_penalty", damagePenalty);
        this.ambushControlDuration = statInt("cc_duration", 0);
    }
}
