package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.Attack;
import com.walnutt.ability.Move;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.StatusEffect;
import com.walnutt.effect.impl.PsychicProjectionEffect;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.map.Tile;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.HealthPool;
import com.walnutt.unit.SummonedUnit;
import com.walnutt.unit.Unit;

/** Lanaya - summons a player-controllable, invulnerable clone; the caster is stunned while it exists. */
public class PsychicProjection extends Ability {
    private final int duration;

    public PsychicProjection(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 6));
        setRange(definition.getInt("cast_range", 4));
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
        Tile tile = tileTarget.getTile();
        return tile.isWalkable() && isInRange(state, tile.getPosition());
    }

    @Override
    public void onUse(GameState state, Target target) {
        Tile tile = ((TileTarget) target).getTile();
        Player player = state.getPlayer(owner.getTeam());

        Unit clone = new SummonedUnit(owner.getName() + " (Clone)", owner.getTeam(), owner.getBaseStats(),
            new HealthPool(owner.getBaseStats().maxHealth()), owner, false);
        clone.addAbility(new Move());
        clone.addAbility(new Attack());
        clone.addEffect(new StatusEffect("Invulnerable", duration, StatusFlag.INVULNERABLE));

        state.getMap().moveUnit(clone, tile);
        player.addUnit(clone);

        owner.addEffect(new PsychicProjectionEffect(clone, player, duration));

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
