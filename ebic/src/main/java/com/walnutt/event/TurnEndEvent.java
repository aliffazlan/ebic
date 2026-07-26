package com.walnutt.event;

import com.walnutt.game.Team;

public record TurnEndEvent(Team team) implements GameEvent {
}
