package com.walnutt.event;

import com.walnutt.status.StatusFlag;
import com.walnutt.unit.Unit;

public record StatusAppliedEvent(Unit unit, StatusFlag flag, Object source) implements GameEvent {
}
