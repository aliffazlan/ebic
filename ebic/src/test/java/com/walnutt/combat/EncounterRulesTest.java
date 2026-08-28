package com.walnutt.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Attack;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.UnitDefinition;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.map.Tile;
import com.walnutt.ui.ActionChoice;
import com.walnutt.ui.InputHandler;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.EliteUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/**
 * The two encounter rules added this round: a Basic on either side makes the encounter
 * automatic, and a unit can never bring an attribute it has none of.
 */
class EncounterRulesTest {

    /** Records what it was asked, and fails loudly if asked something it shouldn't be. */
    private static final class RecordingHandler implements InputHandler {
        private int attributeCalls;
        private int pairCalls;
        private Attribute answer = Attribute.STRENGTH;
        // Keyed by unit: chooseAttributePair asks both sides, so a single "last offered"
        // field would only ever show whichever was asked second.
        private final Map<String, List<Attribute>> offered = new HashMap<>();

        @Override
        public Attribute chooseAttribute(GameState state, Unit unit, Unit opponent) {
            attributeCalls++;
            offered.put(unit.getName(), unit.getUsableAttributes());
            return answer;
        }

        @Override
        public Attribute[] chooseAttributePair(GameState state, Unit attacker, Unit defender) {
            pairCalls++;
            return new Attribute[] { chooseAttribute(state, attacker, defender),
                                     chooseAttribute(state, defender, attacker) };
        }

        @Override
        public ActionChoice chooseAction(GameState state, Player player) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UnitDefinition choosePick(GameState state, Player player, List<UnitDefinition> options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Tile choosePlacementTile(GameState state, Player player, Unit unitToPlace, List<Tile> candidates) {
            throw new UnsupportedOperationException();
        }
    }

    private record Fixture(GameState state, RecordingHandler handler) {
    }

    private static Fixture fixture(Unit attacker, Unit defender) {
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(attacker);
        p2.addUnit(defender);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(attacker, map.getTile(new Position(0, 0)));
        map.moveUnit(defender, map.getTile(new Position(0, 1)));
        RecordingHandler handler = new RecordingHandler();
        state.setInputHandler(handler);
        return new Fixture(state, handler);
    }

    private static Attack attackFor(Unit unit) {
        Attack attack = new Attack();
        unit.addAbility(attack);
        return attack;
    }

    // ---- Basic units fight automatically ----

    /**
     * All-Strength attacker into an all-Intelligence defender, so the weighted roll can
     * only produce the one matchup - a deterministic outcome without depending on the RNG
     * sequence (see CLAUDE.md's WeightedEncounter gotcha).
     */
    @Test
    void aBasicAttackingAnEliteAsksNobodyAnything() {
        Unit basic = new BasicUnit("Grunt", Team.PLAYER_ONE, new UnitStats(30, 0, 0, 300));
        Unit elite = new EliteUnit("Elite", Team.PLAYER_TWO, new UnitStats(0, 0, 40, 600));
        Fixture f = fixture(basic, elite);

        attackFor(basic).onUse(f.state(), new UnitTarget(elite));

        assertEquals(0, f.handler().attributeCalls, "no prompt to either side");
        assertEquals(0, f.handler().pairCalls);
        assertEquals(570, elite.getHealth(), "30 Strength beats 40 Intelligence for full damage");
    }

    @Test
    void anEliteAttackingABasicAlsoAsksNobody() {
        Unit elite = new EliteUnit("Elite", Team.PLAYER_ONE, new UnitStats(50, 0, 0, 600));
        Unit basic = new BasicUnit("Grunt", Team.PLAYER_TWO, new UnitStats(0, 0, 20, 300));
        Fixture f = fixture(elite, basic);

        attackFor(elite).onUse(f.state(), new UnitTarget(basic));

        assertEquals(0, f.handler().attributeCalls);
        assertEquals(250, basic.getHealth());
    }

    @Test
    void twoNonBasicsStillPromptBothSides() {
        Unit a = new EliteUnit("A", Team.PLAYER_ONE, new UnitStats(30, 30, 30, 600));
        Unit b = new EliteUnit("B", Team.PLAYER_TWO, new UnitStats(30, 30, 30, 600));
        Fixture f = fixture(a, b);

        attackFor(a).onUse(f.state(), new UnitTarget(b));

        assertEquals(1, f.handler().pairCalls, "the simultaneous two-sided prompt is unchanged");
    }

    // ---- Zero attributes ----

    @Test
    void aUnitIsOnlyEverOfferedAttributesItActuallyHas() {
        Unit attacker = new EliteUnit("Drained", Team.PLAYER_ONE, new UnitStats(30, 0, 10, 600));
        Unit defender = new EliteUnit("Defender", Team.PLAYER_TWO, new UnitStats(20, 20, 20, 600));
        Fixture f = fixture(attacker, defender);

        attackFor(attacker).onUse(f.state(), new UnitTarget(defender));

        assertEquals(List.of(Attribute.STRENGTH, Attribute.INTELLIGENCE), f.handler().offered.get("Drained"),
            "Agility is at 0, so it is not on offer");
        assertEquals(List.of(Attribute.STRENGTH, Attribute.AGILITY, Attribute.INTELLIGENCE),
            f.handler().offered.get("Defender"), "the healthy defender still gets all three");
        assertEquals(List.of(Attribute.STRENGTH, Attribute.INTELLIGENCE),
            attacker.getUsableAttributes());
    }

    /** An all-0 defender has nothing to block with, so the attacker's pick lands in full. */
    @Test
    void anAllZeroDefenderIsNotPromptedAndTakesFullDamage() {
        Unit attacker = new EliteUnit("Attacker", Team.PLAYER_ONE, new UnitStats(0, 45, 0, 600));
        Unit defender = new EliteUnit("Stripped", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 600));
        Fixture f = fixture(attacker, defender);
        f.handler().answer = Attribute.AGILITY;

        attackFor(attacker).onUse(f.state(), new UnitTarget(defender));

        assertEquals(1, f.handler().attributeCalls, "only the attacker is asked");
        assertEquals(0, f.handler().pairCalls, "never the two-sided prompt");
        assertEquals(555, defender.getHealth(), "the attacker's 45 Agility lands in full");
        assertFalse(defender.hasUsableAttribute());
    }

    @Test
    void anAllZeroUnitCannotAttackAtAll() {
        Unit stripped = new EliteUnit("Stripped", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 600));
        Unit target = new EliteUnit("Target", Team.PLAYER_TWO, new UnitStats(20, 20, 20, 600));
        Fixture f = fixture(stripped, target);
        Attack attack = attackFor(stripped);

        assertFalse(attack.canUse(f.state(), new UnitTarget(target)),
            "nothing to swing with, so the attack is not available");
        assertTrue(attack.getLegalTargets(f.state()).isEmpty(),
            "and it disappears from legal targets, so neither the client nor the bot offers it");
    }

    /** Forced attacks bypass canUse, so the resolver still has to cope with an empty attacker. */
    @Test
    void anAllZeroAttackerForcedIntoAnEncounterDealsNothing() {
        Unit stripped = new EliteUnit("Stripped", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 600));
        Unit target = new EliteUnit("Target", Team.PLAYER_TWO, new UnitStats(20, 20, 20, 600));
        Fixture f = fixture(stripped, target);

        DamageEventProbe.resolve(f.state(), stripped, target);

        assertEquals(600, target.getHealth());
    }

    /** Tiny helper so the forced-attack case reads as one line. */
    private static final class DamageEventProbe {
        static void resolve(GameState state, Unit attacker, Unit defender) {
            CombatEngine.performAttack(state, new WeightedEncounter(attacker, defender));
        }
    }

    @Test
    void theResolverTreatsAMissingAttributeAsNoDefenceRatherThanThrowing() {
        Unit attacker = new EliteUnit("Attacker", Team.PLAYER_ONE, new UnitStats(0, 45, 0, 600));
        Unit defender = new EliteUnit("Stripped", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 600));
        Fixture f = fixture(attacker, defender);

        var event = new EncounterResolver().resolve(f.state(),
            new NormalEncounter(attacker, defender, Attribute.AGILITY, null));

        assertEquals(45, event.getDamage());
        assertNull(event.getDefenderAttribute());
    }
}
