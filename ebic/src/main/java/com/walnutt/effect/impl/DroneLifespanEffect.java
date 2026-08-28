package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.event.TurnEndEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.RemovalReason;
import com.walnutt.status.EffectCategory;
import com.walnutt.unit.Unit;

/**
 * How long one of Maxwell's Killer Drones lasts.
 *
 * Unlike Overgrowth's timer, this can live on the summon itself: drones are added to
 * their player's roster (so they can be selected and moved), which means the turn loop
 * gives them startTurn/endTurn and their effects actually tick.
 *
 * It does NOT use that ordinary tick, though, and the reason is the drone's own strike.
 * TurnManager runs unit.endTurn(state) over the whole roster BEFORE it publishes
 * TurnEndEvent, so an effect expiring in that loop takes the drone off the roster and off
 * the board a moment before DroneAutoAttack would have fired - costing the drone its final
 * strike, and costing it a whole turn of life on the turn it was deployed. Counting down
 * from the TurnEndEvent hook instead puts both on the same clock: Unit.getAllTriggerHandlers
 * lists abilities before effects, so the strike always resolves first and only then does
 * the power cell run down. Same "opt out of tick(), drive it from a turn hook" trick
 * OrbEffect and InfernalBladeEffect use, for their own timing reasons.
 */
public class DroneLifespanEffect extends Effect {
    private boolean dismantled;

    public DroneLifespanEffect(int duration) {
        super("Power Cell",
            "This drone shuts down and is dismantled when its power cell runs out.",
            duration);
        this.category = EffectCategory.NEUTRAL;
        this.dispellable = false;
    }

    /** Deliberately empty - see the class comment; onTurnEnd drives the countdown. */
    @Override
    public void tick() {
    }

    @Override
    public void onTurnEnd(GameState state, TurnEndEvent event) {
        Unit drone = getOwner();
        if (dismantled || drone == null || isExpired() || event.team() != drone.getTeam()) {
            return;
        }
        setRemainingTurns(getRemainingTurns() - 1);
        if (getRemainingTurns() <= 0) {
            dismantle(state);
        }
    }

    /** Safety net for a drone removed some other way; the latch stops a double removal. */
    @Override
    public void onExpire(GameState state) {
        dismantle(state);
    }

    /**
     * Removal follows PsychicProjectionEffect: leave the roster as well as the board, which
     * TurnManager tolerates mid-loop because it snapshots the roster before iterating it.
     */
    private void dismantle(GameState state) {
        Unit drone = getOwner();
        if (dismantled || drone == null || drone.isDead()) {
            return;
        }
        dismantled = true;
        state.getPlayer(drone.getTeam()).removeUnit(drone);
        state.removeUnit(drone, RemovalReason.DESPAWN);
    }
}
