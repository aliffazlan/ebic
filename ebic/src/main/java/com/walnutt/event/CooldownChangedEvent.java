package com.walnutt.event;

import com.walnutt.ability.Ability;
import com.walnutt.unit.Unit;

public record CooldownChangedEvent(Unit unit, Ability ability, int delta) implements GameEvent {
}
