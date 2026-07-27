package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.effect.StatusEffect;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.Unit;

/**
 * Chronos's Dilation: re-derives adjacency every one of Chronos's own turns
 * (rather than fixing membership at cast time) and pulses a 1-turn TIME_DILATED +
 * COOLDOWNS_PAUSED marker onto whoever is currently in range. TIME_DILATED is a
 * generic flag Unit.endTurn already knows how to interpret (buffs tick fast,
 * debuffs tick slow) - Dilation doesn't need the engine to know about it by name.
 */
public class DilationEffect extends Effect {
    private final int radius;

    public DilationEffect(int duration, int radius) {
        super("Dilation",
            "A time-dilation field around Chronos: each of his turns, adjacent enemies have their "
                + "ability cooldowns paused and their buffs tick down twice as fast while their debuffs "
                + "tick down twice as slow, for 1 turn per pulse.",
            duration);
        this.radius = radius;
    }

    @Override
    public void onTurnStart(GameState state, TurnStartEvent event) {
        Unit owner = getOwner();
        if (owner == null || isExpired() || event.team() != owner.getTeam()) {
            return;
        }
        for (Unit enemy : state.getMap().getUnitsInRadius(owner.getPosition(), radius)) {
            if (enemy != owner && enemy.getTeam() != owner.getTeam() && !enemy.isDead()) {
                enemy.addEffect(new StatusEffect("Dilation Field", 1,
                    StatusFlag.COOLDOWNS_PAUSED, StatusFlag.TIME_DILATED));
            }
        }
    }
}
