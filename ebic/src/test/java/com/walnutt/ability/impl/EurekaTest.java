package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.NoTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.JsonDataLoader;
import com.walnutt.event.AbilityCastEvent;
import com.walnutt.event.TurnEndEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.ui.ChoiceOption;
import com.walnutt.ui.InputHandler;
import com.walnutt.unit.EliteUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class EurekaTest {

    /** Answers the construction dialogue with a fixed id, and records what it was offered. */
    private static final class ScriptedChooser implements InputHandler {
        private final String wanted;
        private List<ChoiceOption> lastOffered = List.of();

        ScriptedChooser(String wanted) {
            this.wanted = wanted;
        }

        @Override
        public ChoiceOption chooseOption(GameState state, Unit unit, String title, List<ChoiceOption> options) {
            lastOffered = options;
            return options.stream().filter(o -> o.id().equals(wanted)).findFirst().orElse(options.get(0));
        }

        @Override
        public com.walnutt.ui.ActionChoice chooseAction(GameState state, Player player) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.walnutt.combat.Attribute chooseAttribute(GameState state, Unit unit, Unit opponent) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.walnutt.data.UnitDefinition choosePick(GameState state, Player player,
                                                          List<com.walnutt.data.UnitDefinition> options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.walnutt.map.Tile choosePlacementTile(GameState state, Player player, Unit unitToPlace,
                                                         List<com.walnutt.map.Tile> candidates) {
            throw new UnsupportedOperationException();
        }
    }

    private record Fixture(GameState state, Eureka eureka, Unit maxwell, ScriptedChooser chooser) {
    }

    private static Fixture fixture(String wantedGadget) {
        Unit maxwell = new EliteUnit("Maxwell", Team.PLAYER_ONE, new UnitStats(18, 12, 84, 510, 2));
        Eureka eureka = new Eureka(new AbilityDefinition("Eureka", "active", "desc", Map.of(
            "cooldown", 1.0, "passive_inspiration", 2.0, "bonus_inspiration", 1.0,
            "cost", 6.0, "cost_increase", 10.0)));
        maxwell.addAbility(eureka);

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(maxwell);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(maxwell, map.getTile(new Position(0, 0)));
        // The real ability definitions - Eureka builds its dialogue straight from them.
        state.setAbilityDefinitions(new JsonDataLoader(JsonDataLoader.locateDesignIdeasRoot()).loadAllAbilities());
        ScriptedChooser chooser = new ScriptedChooser(wantedGadget);
        state.setInputHandler(chooser);
        return new Fixture(state, eureka, maxwell, chooser);
    }

    /** One full turn of Maxwell's, optionally with him having acted during it. */
    private static void passOwnTurn(Fixture f, boolean acted) {
        f.eureka().onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));
        if (acted) {
            f.eureka().onAbilityUsed(f.state(),
                new AbilityCastEvent(f.maxwell(), f.eureka(), new NoTarget(), AbilityCastEvent.Phase.POST));
        }
        f.eureka().onTurnEnd(f.state(), new TurnEndEvent(Team.PLAYER_ONE));
    }

    @Test
    void inspirationAccruesTwoPerQuietTurnAndThreeOnATurnMaxwellActed() {
        Fixture f = fixture("plasma_cannon");
        assertEquals(0, f.eureka().getInspiration(), "starts empty");

        passOwnTurn(f, false);
        assertEquals(2, f.eureka().getInspiration());

        passOwnTurn(f, true);
        assertEquals(5, f.eureka().getInspiration(), "2 passive + 1 bonus for having acted");

        // The opponent's turn ending must not pay Maxwell.
        f.eureka().onTurnEnd(f.state(), new TurnEndEvent(Team.PLAYER_TWO));
        assertEquals(5, f.eureka().getInspiration());
    }

    @Test
    void cannotBeCastUntilTheNextGadgetIsAffordable() {
        Fixture f = fixture("plasma_cannon");

        assertFalse(f.eureka().canUse(f.state(), new NoTarget()), "0 inspiration, cost is 6");
        passOwnTurn(f, false);
        passOwnTurn(f, false);
        assertEquals(4, f.eureka().getInspiration());
        assertFalse(f.eureka().canUse(f.state(), new NoTarget()), "4 is still short of 6");

        passOwnTurn(f, false);
        assertEquals(6, f.eureka().getInspiration());
        assertTrue(f.eureka().canUse(f.state(), new NoTarget()), "6 is exactly the price");
    }

    @Test
    void constructingAGadgetSpendsTheInspirationAndAddsARealUsableAbility() {
        Fixture f = fixture("plasma_cannon");
        for (int i = 0; i < 3; i++) {
            passOwnTurn(f, false);
        }

        f.eureka().onUse(f.state(), new NoTarget());

        assertEquals(Eureka.GADGET_IDS.size(), f.chooser().lastOffered.size(),
            "every unbuilt gadget is offered");
        assertEquals(0, f.eureka().getInspiration(), "6 of 6 spent");

        Ability built = f.maxwell().getAbilities().stream()
            .filter(a -> a instanceof PlasmaCannon).findFirst().orElse(null);
        assertNotNull(built, "the chosen gadget should now be part of Maxwell's kit");
        assertTrue(built.isReady(), "and be usable straight away");
    }

    @Test
    void eachGadgetCostsMoreThanTheLast_andAlreadyBuiltOnesStopBeingOffered() {
        Fixture f = fixture("plasma_cannon");
        assertEquals(6, f.eureka().currentCost());

        for (int i = 0; i < 3; i++) {
            passOwnTurn(f, false);
        }
        f.eureka().onUse(f.state(), new NoTarget());
        assertEquals(16, f.eureka().currentCost(), "6 + 10");

        for (int i = 0; i < 8; i++) {
            passOwnTurn(f, false);
        }
        f.eureka().decreaseCooldown(f.eureka().getCurrentCooldown());
        f.state().setRemainingMoves(3);
        f.eureka().onUse(f.state(), new NoTarget());

        assertEquals(26, f.eureka().currentCost(), "16 + 10");
        // lastOffered is what the SECOND dialogue showed - the whole pool minus the one
        // already built. Derived from GADGET_IDS rather than hardcoded, so adding a gadget
        // doesn't fail a test that isn't about the pool's size.
        assertEquals(Eureka.GADGET_IDS.size() - 1, f.chooser().lastOffered.size());
        assertTrue(f.chooser().lastOffered.stream().noneMatch(o -> o.id().equals("plasma_cannon")),
            "an already-built gadget must not be offered again");
        assertEquals(2, f.maxwell().getAbilities().stream().filter(a -> !(a instanceof Eureka)).count(),
            "two gadgets built, and nothing else has crept into the kit");
    }
}
