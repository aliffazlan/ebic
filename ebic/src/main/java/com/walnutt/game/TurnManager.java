package com.walnutt.game;

import java.util.List;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.event.AbilityCastEvent;
import com.walnutt.event.TurnEndEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.sandbox.SandboxController;
import com.walnutt.ui.ActionChoice;
import com.walnutt.ui.InputHandler;
import com.walnutt.ui.Renderer;
import com.walnutt.unit.Unit;

/**
 * Drives one player's turn. The game is player-turn based, not unit-turn based:
 * player gets 3 moves, moves don't carry over, each owned unit resets its per-turn
 * flags, and the player acts until they end their turn or the game ends.
 */
public class TurnManager {

    public void takeTurn(GameState state, InputHandler input, Renderer renderer) {
        Player player = state.getCurrentPlayer();

        state.setRemainingMoves(3);
        // Snapshot before iterating: startTurn/endTurn can expire an effect (e.g. Lanaya's
        // Psychic Projection) whose onExpire removes a unit from this same list mid-loop,
        // which would otherwise throw ConcurrentModificationException.
        for (Unit unit : List.copyOf(player.getUnits())) {
            unit.startTurn(state);
        }
        state.getEventBus().publish(state, new TurnStartEvent(player.getTeam()));

        while (!state.isGameOver()) {
            renderer.render(state);

            ActionChoice choice = input.chooseAction(state, player);
            if (choice.isEndTurn()) {
                break;
            }
            if (choice.isSandbox()) {
                String refusal = SandboxController.apply(state, choice.getSandboxCommand());
                if (refusal != null) {
                    renderer.renderMessage(refusal);
                }
                // Switch Team hands the rest of this turn to the other side.
                player = state.getCurrentPlayer();
                continue;
            }

            Ability ability = choice.getAbility();
            Target target = choice.getTarget();

            if (!ability.canUse(state, target)) {
                renderer.renderMessage("That action isn't available right now.");
                continue;
            }

            Unit user = choice.getUnit();
            state.getEventBus().publish(state, new AbilityCastEvent(user, ability, target, AbilityCastEvent.Phase.PRE));
            ability.onUse(state, target);
            state.getEventBus().publish(state, new AbilityCastEvent(user, ability, target, AbilityCastEvent.Phase.POST));
            state.checkWinCondition();
        }

        for (Unit unit : List.copyOf(player.getUnits())) {
            unit.endTurn(state);
        }
        state.getEventBus().publish(state, new TurnEndEvent(player.getTeam()));
        // Everything that happens on the way out of a turn - poison ticks, burning ground,
        // a Killer Drone's strike - lands after the action loop's last render(), so without
        // this it stayed invisible until the OTHER player's first render, a turn later.
        renderer.render(state);

        if (!state.isGameOver()) {
            state.switchCurrentPlayer();
        }
    }
}
