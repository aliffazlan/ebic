package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.map.Tile;
import com.walnutt.status.EffectCategory;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.Unit;

/**
 * Lanaya's Psychic Projection: owned by the CASTER (stuns them for the duration).
 * When it expires, despawns the clone - both off the map and out of the player's
 * roster (the clone was added there so TurnManager/InputHandler could select it).
 */
public class PsychicProjectionEffect extends Effect {
    private final Unit clone;
    private final Player player;

    public PsychicProjectionEffect(Unit clone, Player player, int duration) {
        super("Psychic Projection", duration);
        this.clone = clone;
        this.player = player;
        this.flags.add(StatusFlag.STUNNED);
        this.category = EffectCategory.DEBUFF;
    }

    @Override
    public void onExpire(GameState state) {
        if (clone.getPosition() != null) {
            Tile tile = state.getMap().getTile(clone.getPosition());
            if (tile != null) {
                tile.removeOccupant(clone);
            }
        }
        player.removeUnit(clone);
    }
}
