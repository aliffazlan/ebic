package com.walnutt.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import com.walnutt.combat.Attribute;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.ui.ActionChoice;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;
import com.walnutt.web.ChannelHub;
import com.walnutt.web.RecordingChannel;
import com.walnutt.web.UnitIdRegistry;
import com.walnutt.web.WebInputHandler;

class TeamRoutingHandlerTest {

    /**
     * The bug this class exists to prevent. Attacking and defending are different games -
     * only the attacker deals damage - so a bot asked to defend must answer with the
     * defending side's solution. Route it through the wrong role and it defends with the
     * attacker's mixture, which here is close to the worst possible defence.
     *
     * The stat blocks are chosen so the two roles' solutions barely overlap: the attacker
     * leans Intelligence, the defender leans Agility. A role mix-up is unmissable.
     */
    @Test
    void aBotDefendingUsesTheDefendersSolutionNotTheAttackers() {
        Unit humanUnit = new BasicUnit("Human", Team.PLAYER_ONE, new UnitStats(50, 5, 5, 500));
        Unit botUnit = new BasicUnit("Bot", Team.PLAYER_TWO, new UnitStats(5, 5, 50, 500));
        GameState state = stateWith(humanUnit, botUnit);

        BotHandler bot = new BotHandler(BotConfig.standard().withoutThinkDelay());
        // Both seats are bots here; only the roles are under test.
        TeamRoutingHandler router = TeamRoutingHandler.of(
            new BotHandler(BotConfig.standard().withoutThinkDelay()), bot);

        Map<Attribute, Integer> defenderPicks = new EnumMap<>(Attribute.class);
        int trials = 20_000;
        for (int i = 0; i < trials; i++) {
            Attribute[] pair = router.chooseAttributePair(state, humanUnit, botUnit);
            defenderPicks.merge(pair[1], 1, Integer::sum);
        }

        MatrixGameSolver.Solution expected =
            MatrixGameSolver.solve(AttributeChooser.payoffMatrix(humanUnit, botUnit));
        for (int j = 0; j < 3; j++) {
            Attribute attribute = Attribute.values()[j];
            assertEquals(expected.columnStrategy()[j],
                defenderPicks.getOrDefault(attribute, 0) / (double) trials, 0.02,
                "defending bot sampled " + attribute + " at the attacker's rate, not the defender's");
        }
        assertTrue(defenderPicks.getOrDefault(Attribute.AGILITY, 0) > trials / 2,
            "the defending bot should lean Agility here; leaning Intelligence means it used the attacker's role");
    }

    /**
     * A bot seat has no socket behind it, so nothing must ever wait on one. The human
     * seat is answered from another thread exactly as a real WS message would answer it.
     */
    @Test
    void anEncounterResolvesWhenOnlyOneSeatHasAClient() throws Exception {
        ChannelHub hub = new ChannelHub();
        RecordingChannel humanChannel = new RecordingChannel();
        hub.register(Team.PLAYER_ONE, humanChannel);
        // Deliberately no channel for PLAYER_TWO - that seat is the bot's.

        WebInputHandler human = new WebInputHandler(hub, new UnitIdRegistry());
        BotHandler bot = new BotHandler(BotConfig.standard().withoutThinkDelay());
        TeamRoutingHandler router = TeamRoutingHandler.of(human, bot);

        Unit humanUnit = new BasicUnit("Human", Team.PLAYER_ONE, new UnitStats(30, 10, 10, 500));
        Unit botUnit = new BasicUnit("Bot", Team.PLAYER_TWO, new UnitStats(10, 30, 10, 500));
        GameState state = stateWith(humanUnit, botUnit);

        CompletableFuture<Attribute[]> pending =
            CompletableFuture.supplyAsync(() -> router.chooseAttributePair(state, humanUnit, botUnit));

        JsonObject answer = new JsonObject();
        answer.addProperty("type", "attribute");
        answer.addProperty("value", "STRENGTH");
        // Retry briefly: the prompt is sent from the other thread, so it may not be out yet.
        for (int i = 0; i < 50 && !pending.isDone(); i++) {
            human.offer(Team.PLAYER_ONE, answer);
            Thread.sleep(10);
            if (pending.isDone()) {
                break;
            }
        }

        Attribute[] result = pending.get(5, TimeUnit.SECONDS);
        assertNotNull(result[0]);
        assertNotNull(result[1]);
        assertEquals(Attribute.STRENGTH, result[0], "the human's own answer should be used verbatim");
    }

    /** Turn and setup calls go to the seat whose player they name, not to a fixed handler. */
    @Test
    void turnActionsAreRoutedToTheTeamTheyBelongTo() {
        GameState state = stateWith(
            new BasicUnit("One", Team.PLAYER_ONE, new UnitStats(20, 20, 20, 300)),
            new BasicUnit("Two", Team.PLAYER_TWO, new UnitStats(20, 20, 20, 300)));

        BotStrategy alwaysEnds = (s, p) -> ActionChoice.endTurn();
        BotConfig config = BotConfig.standard().withoutThinkDelay();
        RecordingStrategy oneStrategy = new RecordingStrategy(alwaysEnds);
        RecordingStrategy twoStrategy = new RecordingStrategy(alwaysEnds);
        TeamRoutingHandler router = TeamRoutingHandler.of(
            new BotHandler(config, oneStrategy), new BotHandler(config, twoStrategy));

        router.chooseAction(state, state.getPlayer(Team.PLAYER_TWO));

        assertEquals(0, oneStrategy.calls, "player one's handler should not have been consulted");
        assertEquals(1, twoStrategy.calls, "player two's handler should have been consulted exactly once");
    }

    private static final class RecordingStrategy implements BotStrategy {
        private final BotStrategy delegate;
        private int calls;

        private RecordingStrategy(BotStrategy delegate) {
            this.delegate = delegate;
        }

        @Override
        public ActionChoice decide(GameState state, Player player) {
            calls++;
            return delegate.decide(state, player);
        }
    }

    private GameState stateWith(Unit one, Unit two) {
        GameMap map = new GameMap(3);
        map.moveUnit(one, map.getTile(new Position(0, 0)));
        map.moveUnit(two, map.getTile(new Position(1, 0)));
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        p1.addUnit(one);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p2.addUnit(two);
        GameState state = new GameState(map, List.of(p1, p2), new Random(3));
        state.setRemainingMoves(3);
        return state;
    }
}
