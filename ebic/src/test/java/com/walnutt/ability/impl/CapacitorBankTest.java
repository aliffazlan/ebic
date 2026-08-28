package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Ability;
import com.walnutt.ability.Attack;
import com.walnutt.ability.Move;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.CapacitorChargeEffect;
import com.walnutt.event.AbilityCastEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.EliteUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class CapacitorBankTest {

    private record Fixture(GameState state, GameMap map, Unit maxwell, CapacitorBank bank,
                           PlasmaCannon cannon, Unit enemy) {

        /** Drives one cast the way TurnManager does: PRE, onUse, POST. */
        void cast(Ability ability, Target target) {
            state.getEventBus().publish(state,
                new AbilityCastEvent(maxwell, ability, target, AbilityCastEvent.Phase.PRE));
            ability.onUse(state, target);
            state.getEventBus().publish(state,
                new AbilityCastEvent(maxwell, ability, target, AbilityCastEvent.Phase.POST));
        }

        void startOwnTurn() {
            maxwell.startTurn(state);
            state.setRemainingMoves(3);
            state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_ONE));
        }

        int charges() {
            return maxwell.getActiveEffect(CapacitorChargeEffect.class).orElseThrow().getAmount();
        }
    }

    private static CapacitorBank newBank() {
        return new CapacitorBank(new AbilityDefinition("Capacitor Bank", "passive", "desc",
            Map.of("charge_per_turn", 1.0, "max_charges", 3.0)));
    }

    private static Fixture fixture() {
        Unit maxwell = new EliteUnit("Maxwell", Team.PLAYER_ONE, new UnitStats(18, 12, 84, 510, 2));
        CapacitorBank bank = newBank();
        PlasmaCannon cannon = new PlasmaCannon(new AbilityDefinition("Plasma Cannon", "active", "desc",
            Map.of("cooldown", 4.0, "cast_range", 2.0, "damage", 60.0, "duration", 2.0)));
        maxwell.addAbility(new Move());
        maxwell.addAbility(new Attack());
        maxwell.addAbility(cannon);
        maxwell.addAbility(bank);

        // A BASIC on one side makes the encounter automatic (weighted, no prompt), so the
        // attack in moveAndAttackNeitherBenefit... needs no InputHandler wired up.
        Unit enemy = new BasicUnit("Enemy", Team.PLAYER_TWO, new UnitStats(40, 40, 40, 500));
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(maxwell);
        p2.addUnit(enemy);
        GameMap map = new GameMap(5);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(maxwell, map.getTile(new Position(0, 0)));
        map.moveUnit(enemy, map.getTile(new Position(0, 1)));
        return new Fixture(state, map, maxwell, bank, cannon, enemy);
    }

    @Test
    void theBankIsVisibleFromTheMomentItIsBuilt() {
        Fixture f = fixture();

        assertTrue(f.maxwell.getActiveEffect(CapacitorChargeEffect.class).isPresent(),
            "the charge count renders in the sidebar for free by being an Effect");
        assertEquals(0, f.charges(), "and starts empty");
    }

    @Test
    void banksOneChargeEachTurnAndStopsAtCapacity() {
        Fixture f = fixture();

        for (int turn = 0; turn < 3; turn++) {
            f.startOwnTurn();
        }
        assertEquals(3, f.charges());

        f.startOwnTurn();
        f.startOwnTurn();

        assertEquals(3, f.charges(), "charges past capacity are simply not stored");
    }

    /** The whole point: an ability cast on a charge costs no action at all. */
    @Test
    void aBankedChargePaysForACastInsteadOfAMovePoint() {
        Fixture f = fixture();
        f.startOwnTurn();
        assertEquals(1, f.charges());

        assertEquals(0, f.cannon.getMoveCost(f.state), "reported free while a charge is banked");
        f.cast(f.cannon, new UnitTarget(f.enemy));

        assertEquals(3, f.state.getRemainingMoves(), "the full slate of move points survives");
        assertEquals(0, f.charges(), "the charge itself was consumed");
    }

    @Test
    void withoutAChargeAnAbilityCostsAMovePointAsNormal() {
        Fixture f = fixture();
        // No turn start, so nothing is banked.
        assertEquals(1, f.cannon.getMoveCost(f.state));

        f.cast(f.cannon, new UnitTarget(f.enemy));

        assertEquals(2, f.state.getRemainingMoves());
    }

    /**
     * The late-game payoff Maxwell is built around: a full bank is three free casts in one
     * turn, with every move point still in hand afterwards.
     */
    @Test
    void aFullBankCastsThreeAbilitiesForFree() {
        Fixture f = fixture();
        for (int turn = 0; turn < 3; turn++) {
            f.startOwnTurn();
        }
        assertEquals(3, f.charges());

        for (int i = 0; i < 3; i++) {
            f.cannon.decreaseCooldown(f.cannon.getCurrentCooldown());
            f.cast(f.cannon, new UnitTarget(f.enemy));
        }

        assertEquals(0, f.charges());
        assertEquals(3, f.state.getRemainingMoves(), "three casts, and not one action spent");
    }

    /** Castable with an empty action budget, which is the reason getMoveCost is the seam. */
    @Test
    void anAbilityIsStillCastableAtZeroRemainingMoves() {
        Fixture f = fixture();
        f.startOwnTurn();
        f.state.setRemainingMoves(0);

        assertTrue(f.cannon.canUse(f.state, new UnitTarget(f.enemy)));
        assertFalse(f.cannon.getLegalTargets(f.state).isEmpty(),
            "so it is highlighted for the player and offered to the bot");
    }

    /**
     * Move and Attack override getMoveCost outright, so they are outside this by
     * construction - they neither become free nor silently burn a charge for nothing.
     */
    @Test
    void moveAndAttackNeitherBenefitFromAChargeNorConsumeOne() {
        Fixture f = fixture();
        f.startOwnTurn();
        Move move = (Move) f.maxwell.getAbilities().stream()
            .filter(a -> a instanceof Move).findFirst().orElseThrow();
        Attack attack = (Attack) f.maxwell.getAbilities().stream()
            .filter(a -> a instanceof Attack).findFirst().orElseThrow();

        assertEquals(1, move.getMoveCost(f.state), "still costs an action");
        assertEquals(1, attack.getMoveCost(f.state));

        f.cast(move, new TileTarget(f.map.getTile(new Position(1, 0))));
        f.cast(attack, new UnitTarget(f.enemy));

        assertEquals(1, f.charges(), "and the bank is untouched");
    }

    /**
     * The regression the marker subclasses exist to prevent. Both Inspiration and the
     * charge bank are ResourceEffects, and Unit.getActiveEffect returns the FIRST match -
     * so a lookup by the base class would hand Eureka whichever happened to be added first.
     */
    @Test
    void eurekaStillFindsItsOwnInspirationPoolWithABankPresent() {
        Map<String, AbilityDefinition> defs =
            new com.walnutt.data.JsonDataLoader(
                com.walnutt.data.JsonDataLoader.locateDesignIdeasRoot()).loadAllAbilities();
        Unit maxwell = new EliteUnit("Maxwell", Team.PLAYER_ONE, new UnitStats(18, 12, 84, 510, 2));
        Eureka eureka = new Eureka(defs.get("eureka"));
        maxwell.addAbility(eureka);
        maxwell.addAbility(newBank());

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        GameState state = new GameState(new GameMap(5), List.of(p1, new Player("P2", Team.PLAYER_TWO)),
            new Random(1));
        p1.addUnit(maxwell);
        state.getMap().moveUnit(maxwell, state.getMap().getTile(new Position(0, 0)));

        // A turn's worth of Inspiration lands in the Inspiration pool, not in the bank.
        state.getEventBus().publish(state, new com.walnutt.event.TurnEndEvent(Team.PLAYER_ONE));

        assertEquals(2, eureka.getInspiration(), "Eureka reads its own pool");
        assertEquals(0, maxwell.getActiveEffect(CapacitorChargeEffect.class).orElseThrow().getAmount(),
            "and the bank is a separate count entirely");
    }
}
