package com.walnutt.ui;

import java.util.List;

import com.walnutt.data.UnitDefinition;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;

/** Pluggable output sink - terminal today, a real frontend later implements the same interface. */
public interface Renderer {
    void render(GameState state);

    void renderMessage(String message);

    void renderGameOver(GameState state);

    /** Draft phase: shows both players' current-round pick pairs together (transparency). */
    void renderDraftRound(String roundLabel, Player playerOne, List<UnitDefinition> playerOneOptions,
                           Player playerTwo, List<UnitDefinition> playerTwoOptions);
}
