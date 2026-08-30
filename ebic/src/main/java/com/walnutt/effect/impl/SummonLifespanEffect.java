package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.event.TurnEndEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.RemovalReason;
import com.walnutt.status.EffectCategory;
import com.walnutt.unit.Unit;

/**
 * How long a summon that joined its player's ROSTER stands for.
 *
 * That distinction is the whole reason this class exists. A summon registered with
 * state.registerSummon never receives startTurn/endTurn, so an effect on it would never tick -
 * Overgrowth's Branchlings are timed by an effect on Branch instead. A summon added to the
 * roster (so the player can select and act with it) does tick, and can therefore time itself.
 *
 * It deliberately does NOT use that ordinary tick. TurnManager runs unit.endTurn(state) over the
 * whole roster BEFORE it publishes TurnEndEvent, so an effect expiring in that loop takes its
 * unit off the board a moment before the unit's own end-of-turn work would have run - costing a
 * Killer Drone its final strike, and costing every summon a whole turn of life on the turn it
 * was created. Counting down from the TurnEndEvent hook instead puts both on the same clock:
 * Unit.getAllTriggerHandlers lists abilities before effects, so the summon acts first and only
 * then does its clock run down. OrbEffect and InfernalBladeEffect use the same "opt out of
 * tick(), drive it from a turn hook" trick for their own timing reasons.
 *
 * Removal follows PsychicProjectionEffect: leave the roster as well as the board, which
 * TurnManager tolerates mid-loop because it snapshots the roster before iterating it.
 */
public abstract class SummonLifespanEffect extends Effect {
    private boolean removed;

    protected SummonLifespanEffect(String name, String description, int duration) {
        super(name, description, duration);
        this.category = EffectCategory.NEUTRAL;
        this.dispellable = false;
    }

    /** Deliberately empty - see the class comment; onTurnEnd drives the countdown. */
    @Override
    public final void tick() {
    }

    @Override
    public void onTurnEnd(GameState state, TurnEndEvent event) {
        Unit summon = getOwner();
        if (removed || summon == null || isExpired() || event.team() != summon.getTeam()) {
            return;
        }
        setRemainingTurns(getRemainingTurns() - 1);
        if (getRemainingTurns() <= 0) {
            remove(state);
        }
    }

    /** Safety net for a summon removed some other way; the latch stops a double removal. */
    @Override
    public void onExpire(GameState state) {
        remove(state);
    }

    /**
     * Takes the summon off the board and out of the roster, once. Callable by an ability that
     * ends a summon early - Recall disperses the shadow it teleports its summoner onto.
     */
    public void remove(GameState state) {
        Unit summon = getOwner();
        if (removed || summon == null || summon.isDead()) {
            return;
        }
        removed = true;
        state.getPlayer(summon.getTeam()).removeUnit(summon);
        state.removeUnit(summon, RemovalReason.DESPAWN);
    }
}
