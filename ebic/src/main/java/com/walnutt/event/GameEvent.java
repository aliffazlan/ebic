package com.walnutt.event;

/**
 * Marker interface for anything published on the EventBus. Deliberately left open
 * (not sealed) so a brand-new ability can introduce its own event type without
 * touching this file, EventBus, or TriggerHandler.
 */
public interface GameEvent {
}
