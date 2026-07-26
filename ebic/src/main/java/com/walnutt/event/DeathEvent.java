package com.walnutt.event;

import com.walnutt.unit.Unit;

public record DeathEvent(Unit unit) implements GameEvent {
}
