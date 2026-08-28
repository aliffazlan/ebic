package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.game.GameState;
import com.walnutt.game.RemovalReason;
import com.walnutt.status.EffectCategory;
import com.walnutt.unit.Unit;

/**
 * How long one of Maxwell's Killer Drones lasts.
 *
 * Unlike Overgrowth's timer, this can live on the summon itself: drones are added to
 * their player's roster (so they can be selected and moved), which means the turn loop
 * gives them startTurn/endTurn and their effects actually tick. A drone registered as a
 * plain summon would never tick and would last forever.
 *
 * Removal follows PsychicProjectionEffect: leave the roster as well as the board, which
 * TurnManager tolerates mid-loop because it snapshots the roster before iterating it.
 */
public class DroneLifespanEffect extends Effect {

    public DroneLifespanEffect(int duration) {
        super("Power Cell",
            "This drone shuts down and is dismantled when its power cell runs out.",
            duration);
        this.category = EffectCategory.NEUTRAL;
        this.dispellable = false;
    }

    @Override
    public void onExpire(GameState state) {
        Unit drone = getOwner();
        if (drone == null || drone.isDead()) {
            return;
        }
        state.getPlayer(drone.getTeam()).removeUnit(drone);
        state.removeUnit(drone, RemovalReason.DESPAWN);
    }
}
