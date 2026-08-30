package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.Attack;
import com.walnutt.ability.Move;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.Effect;
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
    private int duration;

    public PsychicProjection(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 6));
        setRange(definition.getInt("cast_range", 4));
        this.duration = definition.getInt("duration", 3);
    }

    /**
     * Free, as of the v0.3.0 rebalance: projecting costs no action at all, so Lanaya can throw
     * a copy out and still act. Move and Attack are the only other things that spend nothing.
     */
    @Override
    public int getMoveCost(GameState state) {
        return 0;
    }

    @Override
    protected void onUpgraded() {
        this.duration = statInt("duration", duration);
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

        // Upgraded, a copy already out is relocated rather than joined by a second - and the
        // existing one is dropped first so exactly one is ever on the board.
        if (isUpgraded()) {
            owner.getActiveEffect(PsychicProjectionEffect.class).ifPresent(existing -> {
                existing.setRemainingTurns(0);
                owner.removeExpiredEffects(state);
            });
        }

        int life = isUpgraded() ? Effect.PERMANENT : duration;
        Unit clone = new SummonedUnit(owner.getName() + " (Clone)", owner.getTeam(), owner.getBaseStats(),
            new HealthPool(owner.getBaseStats().maxHealth()), owner, false);
        clone.addAbility(new Move());
        clone.addAbility(new Attack());
        clone.addEffect(new StatusEffect("Invulnerable", life, StatusFlag.INVULNERABLE));

        state.getMap().moveUnit(clone, tile);
        player.addUnit(clone);

        // Upgraded, Lanaya is not stunned holding it - which is what the flag below controls.
        owner.addEffect(new PsychicProjectionEffect(clone, player, life, !isUpgraded()));

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
