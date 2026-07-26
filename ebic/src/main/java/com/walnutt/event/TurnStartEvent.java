package com.walnutt.event;

import com.walnutt.game.Team;

public record TurnStartEvent(Team team) implements GameEvent {
}
