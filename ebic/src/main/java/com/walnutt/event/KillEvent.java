package com.walnutt.event;

import com.walnutt.unit.Unit;

/** killer may be null (e.g. death by poison / self-inflicted). */
public record KillEvent(Unit killer, Unit victim) implements GameEvent {
}
