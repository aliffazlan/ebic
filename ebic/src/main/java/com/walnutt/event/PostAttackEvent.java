package com.walnutt.event;

import com.walnutt.unit.Unit;

public record PostAttackEvent(Unit attacker, Unit defender, DamageEvent damageEvent) implements GameEvent {
}
