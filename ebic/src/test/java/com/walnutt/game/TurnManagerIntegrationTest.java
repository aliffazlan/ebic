package com.walnutt.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Attack;
import com.walnutt.ability.Move;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.combat.Attribute;
import com.walnutt.data.UnitDefinition;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.map.Tile;
import com.walnutt.ui.ActionChoice;
import com.walnutt.ui.InputHandler;
import com.walnutt.ui.Renderer;
import com.walnutt.unit.ChampionUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/** End-to-end: move/attack/win loop via TurnManager, exactly what the terminal MVP drives. */
class TurnManagerIntegrationTest {

    @Test
    void attackKillsEnemyChampion_endsGameWithCorrectWinner() {
        GameMap map = new GameMap(5);
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);

        Unit championOne = new ChampionUnit("Champ1", Team.PLAYER_ONE, new UnitStats(100, 10, 10, 50));
        championOne.addAbility(new Move());
        championOne.addAbility(new Attack());
        Unit championTwo = new ChampionUnit("Champ2", Team.PLAYER_TWO, new UnitStats(1, 1, 1, 10));
        championTwo.addAbility(new Move());
        championTwo.addAbility(new Attack());

        p1.addUnit(championOne);
        p2.addUnit(championTwo);

        map.moveUnit(championOne, map.getTile(new Position(0, 0)));
        map.moveUnit(championTwo, map.getTile(new Position(1, 0)));

        GameState state = new GameState(map, List.of(p1, p2), new Random(1));

        Attack attackAbility = (Attack) championOne.getAbilities().stream()
            .filter(a -> a instanceof Attack).findFirst().orElseThrow();
        ActionChoice attackChoice = new ActionChoice(championOne, attackAbility, new UnitTarget(championTwo));

        InputHandler scripted = new InputHandler() {
            private boolean used = false;

            @Override
            public ActionChoice chooseAction(GameState s, Player player) {
                if (used) {
                    return ActionChoice.endTurn();
                }
                used = true;
                return attackChoice;
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
            @Override public void renderDraftRound(String roundLabel, Player p1, List<UnitDefinition> p1Options,
                                                     Player p2, List<UnitDefinition> p2Options) {}
        };

        state.setInputHandler(scripted);
        new TurnManager().takeTurn(state, scripted, silent);

        assertTrue(state.isGameOver());
        assertEquals(p1, state.getWinner());
        assertTrue(championTwo.isDead());
    }
}
