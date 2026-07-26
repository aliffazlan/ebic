package com.walnutt.game;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.impl.PsychicProjection;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.combat.Attribute;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.UnitDefinition;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.map.Tile;
import com.walnutt.ui.ActionChoice;
import com.walnutt.ui.InputHandler;
import com.walnutt.ui.Renderer;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/**
 * Regression test for a real crash hit during playtesting: an effect whose onExpire
 * removes a unit from Player.getUnits() (Lanaya's Psychic Projection clone, the only
 * such case today) threw ConcurrentModificationException when it expired mid-iteration
 * inside TurnManager.takeTurn's own endTurn loop over that same list.
 */
class TurnManagerEffectExpiryTest {

    @Test
    void effectExpiryRemovingAUnitDuringEndTurnDoesNotThrow() {
        GameMap map = new GameMap(6);
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);

        Unit lanaya = new BasicUnit("Lanaya", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 100));
        PsychicProjection projection = new PsychicProjection(new AbilityDefinition("Psychic Projection", "active", "desc",
            Map.of("cooldown", 6.0, "cast_range", 4.0, "duration", 1.0)));
        lanaya.addAbility(projection);
        Unit ally = new BasicUnit("Ally", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 50));
        Unit enemy = new BasicUnit("Enemy", Team.PLAYER_TWO, new UnitStats(10, 10, 10, 50));

        // Order matters for reproducing the bug: Lanaya must be processed by
        // TurnManager's endTurn loop BEFORE the list shrinks past her position, and
        // there must be at least one unit after the clone's slot so the iterator's
        // next() actually gets called again post-removal (ArrayList's Itr silently
        // tolerates removing the second-to-last element without a next() call).
        p1.addUnit(lanaya);
        p1.addUnit(ally);
        p2.addUnit(enemy);

        map.moveUnit(lanaya, map.getTile(new Position(0, 0)));
        map.moveUnit(ally, map.getTile(new Position(2, 0)));
        map.moveUnit(enemy, map.getTile(new Position(0, 4)));

        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);

        // Cast it directly (bypassing TurnManager) - this appends the clone to
        // p1.getUnits(), after Lanaya and the ally.
        TileTarget castTarget = new TileTarget(map.getTile(new Position(1, 0)));
        projection.onUse(state, castTarget);
        assertEquals(3, p1.getUnits().size(), "clone should have been added to the roster");

        InputHandler endTurnImmediately = new InputHandler() {
            @Override
            public ActionChoice chooseAction(GameState s, Player player) {
                return ActionChoice.endTurn();
            }

            @Override
            public Attribute chooseAttribute(GameState s, Unit unit) {
                return Attribute.STRENGTH;
            }

            @Override
            public UnitDefinition choosePick(GameState s, Player player, List<UnitDefinition> options) {
                return options.get(0);
            }

            @Override
            public Tile choosePlacementTile(GameState s, Player player, Unit unitToPlace, List<Tile> candidates) {
                return candidates.get(0);
            }
        };
        Renderer silent = new Renderer() {
            @Override public void render(GameState s) {}
            @Override public void renderMessage(String message) {}
            @Override public void renderGameOver(GameState s) {}
            @Override public void renderDraftRound(String roundLabel, Player pOne, List<UnitDefinition> pOneOptions,
                                                     Player pTwo, List<UnitDefinition> pTwoOptions) {}
        };
        state.setInputHandler(endTurnImmediately);

        // duration=1: this single takeTurn's own endTurn loop ticks the effect to 0
        // and expires it (removing the clone) while still iterating p1.getUnits().
        assertDoesNotThrow(() -> new TurnManager().takeTurn(state, endTurnImmediately, silent));

        assertEquals(2, p1.getUnits().size(), "clone should have been removed once its effect expired");
    }
}
