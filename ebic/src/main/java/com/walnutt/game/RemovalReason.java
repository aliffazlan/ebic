package com.walnutt.game;

public enum RemovalReason {
    /** Runs the DeathEvent/KillEvent pipeline and checks the win condition. */
    DEATH,
    /** Silently detaches from the map/registry - a clone despawning is not a death. */
    DESPAWN
}
