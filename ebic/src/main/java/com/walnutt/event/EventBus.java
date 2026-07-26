package com.walnutt.event;

import com.walnutt.TriggerHandler;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/**
 * Plain fan-out dispatcher, owned by GameState (not static/global) so simulation,
 * save/load, and tests can each have their own isolated instance.
 */
public final class EventBus {

    public <T extends GameEvent> T publish(GameState state, T event) {
        for (Unit unit : state.getAllActiveUnits()) {
            for (TriggerHandler handler : unit.getAllTriggerHandlers()) {
                handler.dispatch(state, event);
            }
        }
        return event;
    }
}
