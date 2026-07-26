package com.walnutt.event;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.unit.Unit;

public record AbilityCastEvent(Unit user, Ability ability, Target target, Phase phase) implements GameEvent {
    public enum Phase {
        PRE,
        POST
    }
}
