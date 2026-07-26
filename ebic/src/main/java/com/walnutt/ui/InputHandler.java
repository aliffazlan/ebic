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

    Attribute chooseAttribute(GameState state, Unit unit);

    /** Draft phase: player picks one of the offered candidates. */
    UnitDefinition choosePick(GameState state, Player player, List<UnitDefinition> options);

    /** Placement phase: player picks a legal tile for the given unit. */
    Tile choosePlacementTile(GameState state, Player player, Unit unitToPlace, List<Tile> candidates);
}
