package com.walnutt.ui;

import java.util.List;

import com.walnutt.combat.Attribute;
import com.walnutt.data.UnitDefinition;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.map.Tile;
import com.walnutt.unit.Unit;

/**
 * Pluggable input source. A terminal MVP implements this with a Scanner; a future
 * frontend implements the same interface (e.g. reading from a queue fed by network
 * requests) without the engine (Game/TurnManager/Ability) changing at all.
 */
public interface InputHandler {
    ActionChoice chooseAction(GameState state, Player player);

    /** {@code opponent} is the other party in this encounter (whichever of attacker/defender isn't {@code unit}) - a UI can use it to show/highlight both sides together. */
    Attribute chooseAttribute(GameState state, Unit unit, Unit opponent);

    /**
     * Requests both sides' attribute picks for one encounter. Default is sequential
     * (attacker asked, then defender) - correct for a single shared terminal, where
     * there's no real "simultaneity" to offer anyway. A networked handler with two
     * independent human seats (see WebInputHandler) should override this to request
     * both picks concurrently, so neither player waits on the other's answer before
     * even seeing the prompt.
     */
    default Attribute[] chooseAttributePair(GameState state, Unit attacker, Unit defender) {
        Attribute attackerChoice = chooseAttribute(state, attacker, defender);
        Attribute defenderChoice = chooseAttribute(state, defender, attacker);
        return new Attribute[] { attackerChoice, defenderChoice };
    }

    /** Draft phase: player picks one of the offered candidates. */
    UnitDefinition choosePick(GameState state, Player player, List<UnitDefinition> options);

    /** Placement phase: player picks a legal tile for the given unit. */
    Tile choosePlacementTile(GameState state, Player player, Unit unitToPlace, List<Tile> candidates);
}
