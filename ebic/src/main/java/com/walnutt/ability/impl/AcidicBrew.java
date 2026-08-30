package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.AcidPoolEffect;
import com.walnutt.game.GameState;
import com.walnutt.map.Position;

/**
 * Shawl - spills a pool of acid that softens whoever stands in it and scalds whoever
 * lingers.
 *
 * Modelled on Eruption: a tile cast that leaves an effect on the caster remembering a board
 * position. Unlike Eruption it deliberately does NOT refuse ground already covered - re-
 * spilling to refresh a pool that is about to run out is a reasonable use of a cooldown, and
 * overlapping pools stacking their amplification is a real (and expensive) choice.
 *
 * The upgrade only widens {@code radius} from 0 to 1 and lengthens the throw, both of which
 * this already reads, so it needs nothing beyond the re-read below.
 */
public class AcidicBrew extends Ability {
    private int duration;
    private int radius;
    private int tickDamage;
    private int bonusDamage;

    public AcidicBrew(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 3));
        setRange(definition.getInt("cast_range", 2));
        this.duration = definition.getInt("duration", 3);
        // Absent in the base definition: one tile is a radius of nothing.
        this.radius = definition.getInt("radius", 0);
        this.tickDamage = definition.getInt("damage", 5);
        this.bonusDamage = definition.getInt("bonus_damage", 10);
    }

    @Override
    protected void onUpgraded() {
        this.duration = statInt("duration", duration);
        this.radius = statInt("radius", radius);
        this.tickDamage = statInt("damage", tickDamage);
        this.bonusDamage = statInt("bonus_damage", bonusDamage);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target)) {
            return false;
        }
        if (!(target instanceof TileTarget tileTarget)) {
            return false;
        }
        // Any real tile in range, occupied or not - dousing the ground an enemy is standing
        // on is the whole point.
        Position position = tileTarget.getTile().getPosition();
        return state.getMap().getTile(position) != null && isInRange(state, position);
    }

    @Override
    public void onUse(GameState state, Target target) {
        Position centre = ((TileTarget) target).getTile().getPosition();
        owner.addEffect(new AcidPoolEffect(owner, centre, radius, duration, tickDamage, bonusDamage));

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
