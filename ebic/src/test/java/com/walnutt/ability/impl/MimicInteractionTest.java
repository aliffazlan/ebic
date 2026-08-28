package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.AbilityFactory;
import com.walnutt.event.AbilityCastEvent;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.PostAttackEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.ChampionUnit;
import com.walnutt.unit.EliteUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/**
 * The rule the user asked for explicitly, and the one thing that silently regresses if
 * held abilities ever leak into Unit.getAbilities(): an ability Joker has copied but
 * parked is invisible to anything that reads a unit's kit - notably Wei, whose whole kit
 * is about punishing the cooldowns a unit is carrying. Superior Mastery is the deliberate
 * exception, since a parked copy has to keep getting closer to usable.
 */
class MimicInteractionTest {

    private record Fixture(GameState state, GameMap map, Unit joker, Mimic mimic,
                           Unit enemy, Ability parked, Ability equipped) {
    }

    private static Map<String, AbilityDefinition> definitions() {
        Map<String, AbilityDefinition> defs = new HashMap<>();
        defs.put("fireblast", new AbilityDefinition("Fireblast", "active", "desc",
            Map.of("cooldown", 2.0, "cast_range", 3.0, "damage", 24.0, "burn_stacks", 3.0)));
        defs.put("soul_rip", new AbilityDefinition("Soul Rip", "active", "desc",
            Map.of("cooldown", 4.0, "cast_range", 3.0)));
        return defs;
    }

    /** Joker with Soul Rip parked (carrying 6 turns of cooldown) and Fireblast equipped. */
    private static Fixture fixture() {
        Unit joker = new ChampionUnit("Joker", Team.PLAYER_ONE, new UnitStats(54, 68, 85, 990, 2));
        Mimic mimic = new Mimic(new AbilityDefinition("Mimic", "active", "desc", Map.of(
            "cooldown", 2.0, "cast_range", 3.0, "duration", 10.0, "steal_window", 3.0, "max_abilities", 1.0)));
        joker.addAbility(mimic);

        Unit enemy = new EliteUnit("Enemy", Team.PLAYER_TWO, new UnitStats(40, 40, 40, 500, 1));
        Player one = new Player("P1", Team.PLAYER_ONE);
        Player two = new Player("P2", Team.PLAYER_TWO);
        one.addUnit(joker);
        two.addUnit(enemy);
        GameMap map = new GameMap(8);
        GameState state = new GameState(map, List.of(one, two), new Random(1));
        state.setAbilityDefinitions(definitions());
        state.setRemainingMoves(3);
        map.moveUnit(joker, map.getTile(new Position(0, 0)));
        map.moveUnit(enemy, map.getTile(new Position(0, 2)));

        steal(state, mimic, enemy, "soul_rip");
        Ability parked = joker.getAbilities().stream()
            .filter(a -> a.getName().equals("Soul Rip")).findFirst().orElseThrow();
        parked.increaseCooldown(6);
        steal(state, mimic, enemy, "fireblast");
        Ability equipped = joker.getAbilities().stream()
            .filter(a -> a.getName().equals("Fireblast")).findFirst().orElseThrow();

        assertEquals(List.of(parked), mimic.getHeldAbilities());
        return new Fixture(state, map, joker, mimic, enemy, parked, equipped);
    }

    private static void steal(GameState state, Mimic mimic, Unit victim, String abilityId) {
        Ability cast = AbilityFactory.create(abilityId, state.getAbilityDefinitions().get(abilityId));
        state.getEventBus().publish(state,
            new AbilityCastEvent(victim, cast, new UnitTarget(victim), AbilityCastEvent.Phase.PRE));
        mimic.onUse(state, new UnitTarget(victim));
    }

    @Test
    void weisEnergyBreakBurnsOnlyWhatJokerIsActuallyWielding() {
        Fixture f = fixture();
        EnergyBreak energyBreak = new EnergyBreak(new AbilityDefinition("Energy Break", "passive", "desc",
            Map.of("cooldown_increase", 1.0, "bonus_increase", 3.0)));
        Unit wei = new EliteUnit("Wei", Team.PLAYER_TWO, new UnitStats(40, 40, 40, 500, 1));
        wei.addAbility(energyBreak);

        DamageEvent hit = new DamageEvent(wei, f.joker, 10);
        energyBreak.onPostAttack(f.state, new PostAttackEvent(wei, f.joker, hit));

        assertEquals(3, f.equipped.getCurrentCooldown(), "the equipped copy took the full penalty");
        assertEquals(6, f.parked.getCurrentCooldown(), "the parked one was never in reach");
    }

    @Test
    void weisImplosionPricesOnlyWhatJokerIsActuallyWielding() {
        Fixture f = fixture();
        Unit wei = new EliteUnit("Wei", Team.PLAYER_TWO, new UnitStats(40, 40, 40, 500, 1));
        Implosion implosion = new Implosion(new AbilityDefinition("Implosion", "active", "desc",
            Map.of("cooldown", 5.0, "cast_range", 2.0, "dmg_per_cooldown", 12.0, "radius", 1.0)));
        wei.addAbility(implosion);
        f.state.getPlayer(Team.PLAYER_TWO).addUnit(wei);
        f.map.moveUnit(wei, f.map.getTile(new Position(0, 1)));

        int before = f.joker.getHealth();
        implosion.onUse(f.state, new UnitTarget(f.joker));

        // Mimic is sitting on its own 2-turn cooldown from the steal, and the equipped
        // copy on 0. The parked copy's 6 would have quadrupled this if it were visible.
        assertEquals(2 * 12, before - f.joker.getHealth());
    }

    /** The deliberate exception - a parked copy still sharpens, or it could never come back. */
    @Test
    void superiorMasteryReachesAParkedCopy() {
        Fixture f = fixture();
        SuperiorMastery mastery = new SuperiorMastery(new AbilityDefinition(
            "Superior Mastery", "passive", "desc",
            Map.of("cast_range_bonus", 2.0, "cooldown_reduction", 1.0)));
        f.joker.addAbility(mastery);
        f.equipped.increaseCooldown(2);

        f.state.getEventBus().publish(f.state,
            new AbilityCastEvent(f.joker, f.mimic, new UnitTarget(f.enemy), AbilityCastEvent.Phase.PRE));

        assertEquals(5, f.parked.getCurrentCooldown(), "the parked copy was refunded a turn too");
        assertEquals(1, f.equipped.getCurrentCooldown());
    }

    /** Held abilities receive no events, so a parked copy cannot react to anything. */
    @Test
    void aParkedCopyIsNotATriggerHandler() {
        Fixture f = fixture();

        assertTrue(f.joker.getAllTriggerHandlers().stream().noneMatch(handler -> handler == f.parked));
        assertTrue(f.joker.getAbilities().stream().noneMatch(ability -> ability == f.parked));
    }
}
