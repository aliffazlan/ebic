package com.walnutt.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Attack;
import com.walnutt.ability.Move;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.ui.ActionChoice;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.ChampionUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class BotHandlerTest {

    private final BotConfig config = BotConfig.standard().withoutThinkDelay();

    /**
     * The single most valuable thing the bot does. A BASIC's attack costs no move points,
     * so taking it can never be a trade-off - but it competes for the same decision slot
     * as everything else, and a bot that scored purely on magnitude would spend its move
     * points first and leave free damage unclaimed.
     */
    @Test
    void takesAFreeBasicAttackBeforeSpendingAnyMovePoints() {
        GameMap map = new GameMap(4);
        Unit basic = new BasicUnit("Ours", Team.PLAYER_ONE, new UnitStats(20, 20, 20, 300));
        basic.addAbility(new Move());
        basic.addAbility(new Attack());
        Unit enemy = new BasicUnit("Theirs", Team.PLAYER_TWO, new UnitStats(20, 20, 20, 300));
        enemy.addAbility(new Move());
        enemy.addAbility(new Attack());

        map.moveUnit(basic, map.getTile(new Position(0, 0)));
        map.moveUnit(enemy, map.getTile(new Position(1, 0)));
        GameState state = stateOf(map, List.of(basic), List.of(enemy));

        ActionChoice choice = new BotHandler(config).chooseAction(state, state.getPlayer(Team.PLAYER_ONE));

        assertFalse(choice.isEndTurn());
        assertSame(basic, choice.getUnit());
        assertTrue(choice.getAbility() instanceof Attack, "expected the free attack, got " + choice.getAbility().getName());
        assertEquals(0, choice.getAbility().getMoveCost(state), "a BASIC attack must be the cost-free one");
        assertEquals(3, state.getRemainingMoves(), "no move point should have been committed yet");
    }

    /** Given two reachable enemies, finish the one that can actually be finished. */
    @Test
    void prefersTheTargetItCanActuallyKill() {
        GameMap map = new GameMap(4);
        Unit attacker = new BasicUnit("Ours", Team.PLAYER_ONE, new UnitStats(20, 20, 20, 300));
        attacker.addAbility(new Move());
        attacker.addAbility(new Attack());

        Unit healthy = new BasicUnit("Healthy", Team.PLAYER_TWO, new UnitStats(20, 20, 20, 300));
        healthy.addAbility(new Attack());
        Unit nearlyDead = new BasicUnit("NearlyDead", Team.PLAYER_TWO, new UnitStats(20, 20, 20, 300));
        nearlyDead.addAbility(new Attack());
        nearlyDead.getHealthPool().setCurrent(1);

        map.moveUnit(attacker, map.getTile(new Position(0, 0)));
        map.moveUnit(healthy, map.getTile(new Position(1, 0)));
        map.moveUnit(nearlyDead, map.getTile(new Position(0, 1)));
        GameState state = stateOf(map, List.of(attacker), List.of(healthy, nearlyDead));

        ActionChoice choice = new BotHandler(config).chooseAction(state, state.getPlayer(Team.PLAYER_ONE));

        assertTrue(choice.getTarget() instanceof UnitTarget);
        assertSame(nearlyDead, ((UnitTarget) choice.getTarget()).getUnit());
    }

    /** With no enemy reachable and nothing worth doing, the bot must yield rather than flail. */
    @Test
    void endsItsTurnWhenNothingIsWorthDoing() {
        GameMap map = new GameMap(2);
        Unit lonely = new BasicUnit("Ours", Team.PLAYER_ONE, new UnitStats(20, 20, 20, 300));
        lonely.addAbility(new Attack());
        map.moveUnit(lonely, map.getTile(new Position(0, 0)));

        GameState state = stateOf(map, List.of(lonely), List.of());

        assertTrue(new BotHandler(config).chooseAction(state, state.getPlayer(Team.PLAYER_ONE)).isEndTurn());
    }

    /**
     * TurnManager re-prompts silently whenever an action turns out to be illegal, so a
     * strategy stuck in a loop would pin the match thread forever and spam the opponent.
     * The cap is the backstop; without it this test would not terminate.
     */
    @Test
    void stopsAfterTheActionCapEvenIfTheStrategyNeverEndsItsTurn() {
        GameState state = trivialState();
        Player player = state.getPlayer(Team.PLAYER_ONE);
        Unit unit = player.getUnits().get(0);

        BotStrategy neverStops = (s, p) -> new ActionChoice(unit, unit.getAbilities().get(0), new UnitTarget(unit));
        BotHandler handler = new BotHandler(config, neverStops);

        int calls = 0;
        while (!handler.chooseAction(state, player).isEndTurn()) {
            calls++;
            assertTrue(calls < 500, "bot never yielded its turn - the action cap is not working");
        }
        assertTrue(calls > 0, "the cap should allow real actions before tripping");
    }

    /**
     * GameSession treats a thrown exception as a fatal match error and tears the whole
     * match down, so a bug in the bot must never escape as one - a person's game should
     * survive the computer playing a bad turn.
     */
    @Test
    void aStrategyThatThrowsEndsTheTurnInsteadOfPropagating() {
        GameState state = trivialState();
        BotStrategy broken = (s, p) -> {
            throw new IllegalStateException("simulated bot bug");
        };

        ActionChoice choice = new BotHandler(config, broken)
            .chooseAction(state, state.getPlayer(Team.PLAYER_ONE));

        assertNotNull(choice);
        assertTrue(choice.isEndTurn());
    }

    /** Every action handed back must already be legal, since an illegal one just loops. */
    @Test
    void neverReturnsAnActionThatFailsItsOwnLegalityCheck() {
        GameMap map = new GameMap(4);
        Unit ours = new BasicUnit("Ours", Team.PLAYER_ONE, new UnitStats(25, 15, 10, 300));
        ours.addAbility(new Move());
        ours.addAbility(new Attack());
        Unit champion = new ChampionUnit("Champ", Team.PLAYER_ONE, new UnitStats(40, 30, 30, 900));
        champion.addAbility(new Move());
        champion.addAbility(new Attack());
        Unit enemy = new BasicUnit("Theirs", Team.PLAYER_TWO, new UnitStats(20, 20, 20, 300));
        enemy.addAbility(new Attack());

        map.moveUnit(ours, map.getTile(new Position(0, 0)));
        map.moveUnit(champion, map.getTile(new Position(-1, 0)));
        map.moveUnit(enemy, map.getTile(new Position(1, 0)));
        GameState state = stateOf(map, List.of(ours, champion), List.of(enemy));

        BotHandler handler = new BotHandler(config);
        // Attack.onUse resolves the encounter through the state's handler, so this test
        // has to seat one the way a real match does before executing anything.
        state.setInputHandler(handler);
        Player player = state.getPlayer(Team.PLAYER_ONE);
        for (int i = 0; i < 20; i++) {
            ActionChoice choice = handler.chooseAction(state, player);
            if (choice.isEndTurn()) {
                break;
            }
            assertTrue(choice.getAbility().canUse(state, choice.getTarget()),
                "bot returned an illegal " + choice.getAbility().getName());
            choice.getAbility().onUse(state, choice.getTarget());
        }
    }

    private GameState trivialState() {
        GameMap map = new GameMap(2);
        Unit unit = new BasicUnit("Ours", Team.PLAYER_ONE, new UnitStats(20, 20, 20, 300));
        unit.addAbility(new Attack());
        map.moveUnit(unit, map.getTile(new Position(0, 0)));
        return stateOf(map, List.of(unit), List.of());
    }

    private GameState stateOf(GameMap map, List<Unit> ours, List<Unit> theirs) {
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        ours.forEach(p1::addUnit);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        theirs.forEach(p2::addUnit);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        return state;
    }
}
