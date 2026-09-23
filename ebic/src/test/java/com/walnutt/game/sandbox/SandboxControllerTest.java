package com.walnutt.game.sandbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.walnutt.TriggerHandler;
import com.walnutt.ability.Ability;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.JsonDataLoader;
import com.walnutt.data.UnitDefinition;
import com.walnutt.effect.impl.DuelEffect;
import com.walnutt.effect.impl.PoisonEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.DeathEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.map.TileType;
import com.walnutt.unit.ChampionUnit;
import com.walnutt.unit.HealthPool;
import com.walnutt.unit.SummonedUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;
import com.walnutt.unit.UnitType;

class SandboxControllerTest {

    private static final JsonDataLoader LOADER = new JsonDataLoader(JsonDataLoader.locateDesignIdeasRoot());
    private static final Map<String, UnitDefinition> UNITS = LOADER.loadAllUnits();
    private static final Map<String, AbilityDefinition> ABILITIES = LOADER.loadAllAbilities();

    private GameState state;
    private Player one;
    private Player two;

    @BeforeEach
    void setUp() {
        one = new Player("Player One", Team.PLAYER_ONE);
        two = new Player("Player Two", Team.PLAYER_TWO);
        state = new GameState(new GameMap(4), List.of(one, two), new Random(1));
        state.setUnitDefinitions(UNITS);
        state.setAbilityDefinitions(ABILITIES);
        state.setSandbox(true);
    }

    private Unit spawn(Team team, String definitionId, int q, int r) {
        assertNull(SandboxController.apply(state,
            new SandboxCommand.Spawn(team, definitionId, new Position(q, r))));
        return state.getMap().getTile(new Position(q, r)).getOccupants().stream()
            .filter(u -> u.getTeam() == team && u.occupiesTile()).findFirst().orElseThrow();
    }

    @Test
    void spawn_placesAFreshDraftableUnitOnTheTeamsRoster() {
        Unit valor = spawn(Team.PLAYER_TWO, "valor", 1, 0);

        assertEquals(UnitType.CHAMPION, valor.getUnitType());
        assertTrue(two.getUnits().contains(valor));
        assertEquals(new Position(1, 0), valor.getPosition());
        assertFalse(valor.hasMovedThisTurn());
        for (Ability ability : valor.getAbilities()) {
            assertTrue(ability.isReady(), ability.getName() + " should start off cooldown");
        }
    }

    @Test
    void spawn_basicBuildsTheGenericBasicWithAUniqueName() {
        Unit first = spawn(Team.PLAYER_ONE, SandboxController.BASIC_ID, 0, 0);
        Unit second = spawn(Team.PLAYER_ONE, SandboxController.BASIC_ID, 1, 0);

        assertEquals(UnitType.BASIC, first.getUnitType());
        assertEquals(300, first.getMaxHealth());
        assertFalse(first.getName().equals(second.getName()));
    }

    @Test
    void spawn_allowsSeveralChampionsOnOneTeam() {
        spawn(Team.PLAYER_ONE, "valor", 0, 0);
        spawn(Team.PLAYER_ONE, "zenith", 1, 0);

        assertEquals(2, one.getUnits().stream().filter(u -> u.getUnitType() == UnitType.CHAMPION).count());
    }

    @Test
    void spawn_refusesSummonPrototypesAndUnknownIds() {
        assertNotNull(SandboxController.apply(state,
            new SandboxCommand.Spawn(Team.PLAYER_ONE, "branchling", new Position(0, 0))));
        assertNotNull(SandboxController.apply(state,
            new SandboxCommand.Spawn(Team.PLAYER_ONE, "zenith_pylon", new Position(0, 0))));
        assertNotNull(SandboxController.apply(state,
            new SandboxCommand.Spawn(Team.PLAYER_ONE, "nobody", new Position(0, 0))));
        assertTrue(one.getUnits().isEmpty());
    }

    @Test
    void spawn_refusesOccupiedBlockedAndOffMapTiles_butStacksOnANonOccupyingUnit() {
        spawn(Team.PLAYER_ONE, "valor", 0, 0);
        state.getMap().setTileType(new Position(1, 0), TileType.BLOCKED);

        assertNotNull(SandboxController.apply(state,
            new SandboxCommand.Spawn(Team.PLAYER_TWO, "valor", new Position(0, 0))));
        assertNotNull(SandboxController.apply(state,
            new SandboxCommand.Spawn(Team.PLAYER_TWO, "valor", new Position(1, 0))));
        assertNotNull(SandboxController.apply(state,
            new SandboxCommand.Spawn(Team.PLAYER_TWO, "valor", new Position(99, 0))));

        Unit summoner = one.getUnits().get(0);
        Unit pylon = new SummonedUnit("Pylon", Team.PLAYER_ONE, UnitType.BASIC, new UnitStats(0, 0, 0, 50),
            new HealthPool(50), summoner, false, false);
        state.getMap().moveUnit(pylon, state.getMap().getTile(new Position(2, 0)));
        state.registerSummon(pylon);

        spawn(Team.PLAYER_TWO, "valor", 2, 0);
        assertFalse(SandboxController.spawnTiles(state).stream()
            .anyMatch(t -> t.getPosition().equals(new Position(0, 0))));
    }

    @Test
    void remove_takesTheUnitAndItsSummonsOutWithoutADeath() {
        Unit branch = spawn(Team.PLAYER_ONE, "branch", 0, 0);
        Unit branchling = new SummonedUnit("Branchling", Team.PLAYER_ONE, UnitType.BASIC,
            new UnitStats(0, 0, 0, 50), new HealthPool(50), branch, false, true);
        state.getMap().moveUnit(branchling, state.getMap().getTile(new Position(1, 0)));
        state.registerSummon(branchling);
        Unit golemLike = spawn(Team.PLAYER_ONE, SandboxController.BASIC_ID, 2, 0);
        state.recordSummoner(golemLike, branch);

        List<DeathEvent> deaths = new ArrayList<>();
        state.getEventBus().addGlobalListener(new TriggerHandler() {
            @Override
            public void onDeath(GameState s, DeathEvent event) {
                deaths.add(event);
            }
        });

        assertNull(SandboxController.apply(state, new SandboxCommand.Remove(branch)));

        assertTrue(state.getAllActiveUnits().isEmpty());
        assertFalse(state.getMap().getTile(new Position(0, 0)).isOccupied());
        assertFalse(state.getMap().getTile(new Position(1, 0)).isOccupied());
        assertFalse(state.getMap().getTile(new Position(2, 0)).isOccupied());
        assertTrue(deaths.isEmpty(), "removal is not a death");
    }

    @Test
    void remove_stripsEffectsTheRemovedUnitAppliedOrIsLinkedBy() {
        Unit poisoner = spawn(Team.PLAYER_ONE, SandboxController.BASIC_ID, 0, 0);
        Unit victim = spawn(Team.PLAYER_TWO, SandboxController.BASIC_ID, 1, 0);
        Unit bystander = spawn(Team.PLAYER_TWO, SandboxController.BASIC_ID, 2, 0);
        victim.addEffect(new PoisonEffect(poisoner, 3, 10));
        victim.addEffect(new DuelEffect(poisoner, 3, 0.1, 0.1, 1.5));
        bystander.addEffect(new PoisonEffect(victim, 3, 10));

        SandboxController.apply(state, new SandboxCommand.Remove(poisoner));

        assertTrue(victim.getEffects().isEmpty());
        assertEquals(1, bystander.getEffects().size(), "an effect with an unrelated source stays");
    }

    @Test
    void clear_removesEveryUnitIncludingTheDead() {
        spawn(Team.PLAYER_ONE, "valor", 0, 0);
        Unit doomed = spawn(Team.PLAYER_TWO, SandboxController.BASIC_ID, 1, 0);
        doomed.takeDamage(state, new DamageEvent(null, doomed, 10_000));
        assertTrue(doomed.isDead());

        SandboxController.apply(state, new SandboxCommand.Clear());

        assertTrue(one.getUnits().isEmpty());
        assertTrue(two.getUnits().isEmpty());
        assertTrue(state.getAllActiveUnits().isEmpty());
    }

    @Test
    void championDeath_doesNotEndASandbox() {
        Unit champion = spawn(Team.PLAYER_ONE, "valor", 0, 0);
        champion.takeDamage(state, new DamageEvent(null, champion, 10_000));

        assertTrue(champion.isDead());
        assertFalse(state.isGameOver());
    }

    @Test
    void championDeath_stillEndsAnOrdinaryMatch() {
        state.setSandbox(false);
        Unit champion = new ChampionUnit("Champ", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 100));
        one.addUnit(champion);
        state.getMap().moveUnit(champion, state.getMap().getTile(new Position(0, 0)));
        two.addUnit(new ChampionUnit("Other", Team.PLAYER_TWO, new UnitStats(10, 10, 10, 100)));

        champion.takeDamage(state, new DamageEvent(null, champion, 10_000));

        assertTrue(state.isGameOver());
    }

    @Test
    void heal_restoresFullHealth() {
        Unit unit = spawn(Team.PLAYER_ONE, SandboxController.BASIC_ID, 0, 0);
        unit.takeDamage(state, new DamageEvent(null, unit, 120));

        SandboxController.apply(state, new SandboxCommand.Heal(unit));

        assertEquals(unit.getHealthPool().getMax(), unit.getHealth());
    }

    @Test
    void resetCooldowns_readiesEveryAbility() {
        Unit valor = spawn(Team.PLAYER_ONE, "valor", 0, 0);
        valor.getAbilities().forEach(a -> a.increaseCooldown(3));

        SandboxController.apply(state, new SandboxCommand.ResetCooldowns());

        valor.getAbilities().forEach(a -> assertTrue(a.isReady()));
    }

    @Test
    void refillMoves_restoresTheAllowanceAndTheCurrentTeamsFlags() {
        Unit unit = spawn(Team.PLAYER_ONE, SandboxController.BASIC_ID, 0, 0);
        state.setRemainingMoves(0);
        unit.markMoved();
        unit.markAttacked();

        SandboxController.apply(state, new SandboxCommand.RefillMoves());

        assertEquals(3, state.getRemainingMoves());
        assertFalse(unit.hasMovedThisTurn());
        assertFalse(unit.hasAttackedThisTurn());
    }

    @Test
    void switchTeam_handsControlOverWithAFreshAllowance() {
        Unit red = spawn(Team.PLAYER_TWO, SandboxController.BASIC_ID, 0, 0);
        red.markMoved();
        state.setRemainingMoves(1);

        SandboxController.apply(state, new SandboxCommand.SwitchTeam());

        assertEquals(two, state.getCurrentPlayer());
        assertEquals(3, state.getRemainingMoves());
        assertFalse(red.hasMovedThisTurn());
    }
}
