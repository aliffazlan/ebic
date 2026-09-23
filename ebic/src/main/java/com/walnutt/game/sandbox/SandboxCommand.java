package com.walnutt.game.sandbox;

import com.walnutt.game.Team;
import com.walnutt.map.Position;
import com.walnutt.unit.Unit;

/**
 * One sandbox tool use, already resolved against the live game (a real Unit, a real
 * Position) by whichever InputHandler read it - SandboxController only ever applies it.
 */
public sealed interface SandboxCommand {

    /** {@code definitionId} is a draftable champion/elite id, or {@link SandboxController#BASIC_ID}. */
    record Spawn(Team team, String definitionId, Position position) implements SandboxCommand { }

    record Remove(Unit unit) implements SandboxCommand { }

    record Clear() implements SandboxCommand { }

    record RefillMoves() implements SandboxCommand { }

    record ResetCooldowns() implements SandboxCommand { }

    record Heal(Unit unit) implements SandboxCommand { }

    /** Hands control to the other team without ending the turn - no end/start-of-turn effects. */
    record SwitchTeam() implements SandboxCommand { }
}
