package com.walnutt.ai;

import java.util.List;

import com.walnutt.data.UnitDefinition;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.ui.Renderer;

/** Discards everything - self-play runs thousands of turns and wants no output at all. */
public final class SilentRenderer implements Renderer {
    @Override
    public void render(GameState state) {
    }

    @Override
    public void renderMessage(String message) {
    }

    @Override
    public void renderGameOver(GameState state) {
    }

    @Override
    public void renderDraftRound(String roundLabel, Player playerOne, List<UnitDefinition> playerOneOptions,
                                  Player playerTwo, List<UnitDefinition> playerTwoOptions) {
    }
}
