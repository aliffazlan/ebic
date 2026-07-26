package com.walnutt.event;

import java.util.ArrayList;
import java.util.List;

import com.walnutt.TriggerHandler;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/**
 * Plain fan-out dispatcher, owned by GameState (not static/global) so simulation,
 * save/load, and tests can each have their own isolated instance.
 */
public final class EventBus {
    /**
     * Listeners that aren't tied to any particular unit - e.g. a web frontend's VFX
     * bridge, which needs every event in the game regardless of which unit (if any)
     * "owns" it. Empty by default; nothing in the existing 39 abilities/effects
     * populates this, so their behavior is unaffected.
     */
    private final List<TriggerHandler> globalListeners = new ArrayList<>();

    public void addGlobalListener(TriggerHandler listener) {
        globalListeners.add(listener);
    }

    public <T extends GameEvent> T publish(GameState state, T event) {
        for (Unit unit : state.getAllActiveUnits()) {
            for (TriggerHandler handler : unit.getAllTriggerHandlers()) {
                handler.dispatch(state, event);
            }
        }
        for (TriggerHandler listener : globalListeners) {
            listener.dispatch(state, event);
        }
        return event;
    }
}
