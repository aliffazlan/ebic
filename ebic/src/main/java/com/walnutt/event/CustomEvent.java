package com.walnutt.event;

import java.util.Map;

/**
 * Escape hatch for a mechanic nobody anticipated: publish/subscribe on a string key
 * without adding a new class to this package or touching EventBus/TriggerHandler.
 */
public record CustomEvent(String key, Map<String, Object> payload) implements GameEvent {
}
