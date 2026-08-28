package com.walnutt.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.walnutt.ability.Ability;
import com.walnutt.ability.impl.Translocation;
import com.walnutt.ability.target.MultiTarget;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.ui.ActionChoice;
import com.walnutt.ui.ChoiceOption;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.EliteUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/**
 * The two additions Maxwell needed from the web bridge: the generic option dialogue, and
 * two-part targeting. Both use the same blocking-call-on-a-background-thread shape as
 * WebInputHandlerTest, resolved from the test thread via offer(...).
 */
class WebInputHandlerChoiceTest {

    private static JsonObject lastPayload(RecordingChannel channel) {
        return JsonParser.parseString(channel.last()).getAsJsonObject().getAsJsonObject("payload");
    }

    private static JsonObject message(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void chooseOption_promptsTheActingTeamOnly_thenReturnsTheChosenOption() throws Exception {
        ChannelHub hub = new ChannelHub();
        RecordingChannel p1 = new RecordingChannel();
        RecordingChannel p2 = new RecordingChannel();
        hub.register(Team.PLAYER_ONE, p1);
        hub.register(Team.PLAYER_TWO, p2);

        UnitIdRegistry ids = new UnitIdRegistry();
        WebInputHandler input = new WebInputHandler(hub, ids);
        Unit maxwell = new EliteUnit("Maxwell", Team.PLAYER_ONE, new UnitStats(18, 12, 84, 510, 2));
        String unitId = ids.idFor(maxwell);
        GameState state = new GameState(new GameMap(3),
            List.of(new Player("P1", Team.PLAYER_ONE), new Player("P2", Team.PLAYER_TWO)), new Random(1));

        List<ChoiceOption> options = List.of(
            new ChoiceOption("plasma_cannon", "Plasma Cannon", "Deals 60 damage.", "Cooldown: 4 turns"),
            new ChoiceOption("gyroscope", "Gyroscope", "More range.", "Passive"));

        CompletableFuture<ChoiceOption> future =
            CompletableFuture.supplyAsync(() -> input.chooseOption(state, maxwell, "Construct a gadget", options));

        JsonObject payload = null;
        for (int i = 0; i < 200 && payload == null; i++) {
            if (p1.last() != null) {
                payload = lastPayload(p1);
            } else {
                Thread.sleep(5);
            }
        }
        assertEquals("choice", payload.get("kind").getAsString());
        assertEquals(unitId, payload.get("unitId").getAsString(), "so the UI can say who is choosing");
        assertEquals("Construct a gadget", payload.get("title").getAsString());
        JsonArray sent = payload.getAsJsonArray("options");
        assertEquals(2, sent.size());
        assertEquals("Plasma Cannon", sent.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("Cooldown: 4 turns", sent.get(0).getAsJsonObject().get("detail").getAsString());
        assertTrue(p2.getSent().isEmpty(), "the opponent is not prompted");

        input.offer(Team.PLAYER_ONE, message("{\"type\":\"choice\",\"optionId\":\"gyroscope\"}"));

        assertEquals("gyroscope", future.get(2, TimeUnit.SECONDS).id());
    }

    /** The contract's rule: the client submits intent, the server decides, and a bad id just re-asks. */
    @Test
    void chooseOption_rejectsAnUnknownOptionAndKeepsWaiting() throws Exception {
        ChannelHub hub = new ChannelHub();
        RecordingChannel p1 = new RecordingChannel();
        hub.register(Team.PLAYER_ONE, p1);

        UnitIdRegistry ids = new UnitIdRegistry();
        WebInputHandler input = new WebInputHandler(hub, ids);
        Unit maxwell = new EliteUnit("Maxwell", Team.PLAYER_ONE, new UnitStats(18, 12, 84, 510, 2));
        GameState state = new GameState(new GameMap(3),
            List.of(new Player("P1", Team.PLAYER_ONE), new Player("P2", Team.PLAYER_TWO)), new Random(1));
        List<ChoiceOption> options = List.of(new ChoiceOption("reload", "Reload", "desc", "Passive"));

        CompletableFuture<ChoiceOption> future =
            CompletableFuture.supplyAsync(() -> input.chooseOption(state, maxwell, "Construct a gadget", options));
        while (p1.last() == null) {
            Thread.sleep(5);
        }

        input.offer(Team.PLAYER_ONE, message("{\"type\":\"choice\",\"optionId\":\"not_a_gadget\"}"));
        Thread.sleep(50);
        assertFalse(future.isDone(), "an unrecognised option must not resolve the dialogue");
        // Decoded, not raw: Gson is HTML-safe by default, so the apostrophe in the
        // rejection text is escaped to \u0027 on the wire.
        JsonObject rejection = message(p1.last());
        assertEquals("message", rejection.get("type").getAsString());
        assertTrue(rejection.get("text").getAsString().contains("isn't one of the options offered"));

        input.offer(Team.PLAYER_ONE, message("{\"type\":\"choice\",\"optionId\":\"reload\"}"));
        assertEquals("reload", future.get(2, TimeUnit.SECONDS).id());
    }

    @Test
    void chooseAction_resolvesAMultiTargetCastFromOneMessage() throws Exception {
        ChannelHub hub = new ChannelHub();
        RecordingChannel p1 = new RecordingChannel();
        hub.register(Team.PLAYER_ONE, p1);

        UnitIdRegistry ids = new UnitIdRegistry();
        WebInputHandler input = new WebInputHandler(hub, ids);

        Unit maxwell = new EliteUnit("Maxwell", Team.PLAYER_ONE, new UnitStats(18, 12, 84, 510, 2));
        Unit enemy = new BasicUnit("Enemy", Team.PLAYER_TWO, new UnitStats(10, 10, 10, 300));
        Translocation translocation = new Translocation(
            new AbilityDefinition("Translocation", "active", "desc", java.util.Map.of(
                "cooldown", 5.0, "cast_range", 3.0, "self_range", 4.0, "ally_range", 3.0, "enemy_range", 2.0)));
        maxwell.addAbility(translocation);

        Player playerOne = new Player("P1", Team.PLAYER_ONE);
        Player playerTwo = new Player("P2", Team.PLAYER_TWO);
        playerOne.addUnit(maxwell);
        playerTwo.addUnit(enemy);
        GameMap map = new GameMap(5);
        GameState state = new GameState(map, List.of(playerOne, playerTwo), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(maxwell, map.getTile(new Position(0, 0)));
        map.moveUnit(enemy, map.getTile(new Position(0, 2)));
        String maxwellId = ids.idFor(maxwell);
        String enemyId = ids.idFor(enemy);

        CompletableFuture<ActionChoice> future =
            CompletableFuture.supplyAsync(() -> input.chooseAction(state, playerOne));
        while (p1.last() == null) {
            Thread.sleep(5);
        }

        // The prompt must advertise the two-stage shape, or the client cannot drive it.
        JsonObject multi = lastPayload(p1).getAsJsonObject("legalTargets")
            .getAsJsonObject(maxwellId).getAsJsonObject("translocation").getAsJsonObject("multi");
        assertTrue(multi.getAsJsonArray("primaryUnitIds").contains(new com.google.gson.JsonPrimitive(enemyId)),
            "the enemy is within cast range, so it should be pickable");
        assertFalse(multi.getAsJsonObject("destinationsByPrimary").getAsJsonArray(enemyId).isEmpty(),
            "and should come with its own list of legal destinations");

        input.offer(Team.PLAYER_ONE, message("{\"type\":\"action\",\"kind\":\"ability\",\"unitId\":\"" + maxwellId
            + "\",\"abilityId\":\"translocation\",\"targetKind\":\"multi\",\"targetUnitId\":\"" + enemyId
            + "\",\"q\":1,\"r\":2}"));

        ActionChoice choice = future.get(2, TimeUnit.SECONDS);
        Target target = choice.getTarget();
        assertTrue(target instanceof MultiTarget, "both halves must survive the round trip");
        MultiTarget pair = (MultiTarget) target;
        assertEquals(enemy, ((UnitTarget) pair.primary()).getUnit());
        assertEquals(new Position(1, 2), ((TileTarget) pair.secondary()).getTile().getPosition());

        Ability ability = choice.getAbility();
        assertTrue(ability.canUse(state, target), "and the resolved pair must actually be castable");
    }
}
