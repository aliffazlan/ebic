package com.walnutt.event;

import com.walnutt.map.Position;
import com.walnutt.unit.Unit;

public record PostMoveEvent(Unit unit, Position from, Position to) implements GameEvent {
}
