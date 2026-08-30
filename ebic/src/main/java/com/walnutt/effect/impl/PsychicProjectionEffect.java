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
        this(clone, player, duration, true);
    }

    /**
     * {@code stunsCaster} is false for the upgraded form: the projection stops costing Lanaya
     * her own turn, which is the whole of what makes it worth unlocking. The effect still has to
     * exist on her - it is what despawns the clone - so the flag is dropped rather than the
     * effect.
     */
    public PsychicProjectionEffect(Unit clone, Player player, int duration, boolean stunsCaster) {
        super("Psychic Projection",
            (stunsCaster
                ? "Stuns Lanaya for the duration while her "
                : "Her ")
                + "invulnerable psychic clone acts independently on the battlefield; when the effect "
                + "ends, the clone is removed from the board and from her roster.",
            duration);
        this.clone = clone;
        this.player = player;
        if (stunsCaster) {
            this.flags.add(StatusFlag.STUNNED);
        }
        this.category = stunsCaster ? EffectCategory.DEBUFF : EffectCategory.BUFF;
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
