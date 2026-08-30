package com.walnutt.ability.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.walnutt.ability.Ability;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.AbilityFactory;
import com.walnutt.data.JsonDataLoader;
import com.walnutt.data.UnitDefinition;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.EliteUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/**
 * Shared setup for the per-hero upgrade tests.
 *
 * Abilities are built from the REAL design_ideas/ JSON rather than from hand-written stat maps,
 * which is the whole point: an upgrade only exists in that JSON, and a test that invented its
 * own numbers would pass while the shipped ones were wrong. It also means these tests fail if
 * an upgrade block is edited out from under them.
 */
final class UpgradeFixture {

    static final Map<String, AbilityDefinition> DEFINITIONS =
        new JsonDataLoader(JsonDataLoader.locateDesignIdeasRoot()).loadAllAbilities();
    static final Map<String, UnitDefinition> UNITS =
        new JsonDataLoader(JsonDataLoader.locateDesignIdeasRoot()).loadAllUnits();

    private final GameState state;
    private final GameMap map;
    private final Player one;
    private final Player two;

    private UpgradeFixture(GameState state, GameMap map, Player one, Player two) {
        this.state = state;
        this.map = map;
        this.one = one;
        this.two = two;
    }

    static UpgradeFixture create() {
        return create(4, 1);
    }

    static UpgradeFixture create(int mapRadius, long seed) {
        Player one = new Player("P1", Team.PLAYER_ONE);
        Player two = new Player("P2", Team.PLAYER_TWO);
        GameMap map = new GameMap(mapRadius);
        GameState state = new GameState(map, List.of(one, two), new Random(seed));
        state.setAbilityDefinitions(DEFINITIONS);
        state.setUnitDefinitions(UNITS);
        state.setRemainingMoves(5);
        return new UpgradeFixture(state, map, one, two);
    }

    GameState state() {
        return state;
    }

    GameMap map() {
        return map;
    }

    /** An ability built the way a drafted unit's is, so it carries its definition and can be upgraded. */
    static Ability ability(String id) {
        AbilityDefinition definition = DEFINITIONS.get(id);
        if (definition == null) {
            throw new IllegalArgumentException(id + ".json is missing from design_ideas/");
        }
        return AbilityFactory.create(id, definition);
    }

    /** As {@link #ability}, already upgraded - the form under test in most of these files. */
    static Ability upgraded(String id) {
        Ability ability = ability(id);
        ability.upgrade();
        return ability;
    }

    Unit place(Unit unit, Team team, int q, int r) {
        (team == Team.PLAYER_ONE ? one : two).addUnit(unit);
        map.moveUnit(unit, map.getTile(new Position(q, r)));
        return unit;
    }

    Unit basic(String name, Team team, UnitStats stats, int q, int r) {
        return place(new BasicUnit(name, team, stats), team, q, r);
    }

    Unit elite(String name, Team team, UnitStats stats, int q, int r) {
        return place(new EliteUnit(name, team, stats), team, q, r);
    }

    /** A unit carrying the named abilities, upgraded or not, placed on the board. */
    Unit heroWith(String name, Team team, UnitStats stats, int q, int r, boolean upgrade, String... abilityIds) {
        Unit unit = new EliteUnit(name, team, stats);
        List<Ability> built = new ArrayList<>();
        for (String id : abilityIds) {
            Ability ability = ability(id);
            built.add(ability);
            unit.addAbility(ability);
        }
        // Upgraded AFTER attachment, exactly as Hidden Potential does it - several upgrades
        // grant the owner something (Longshot's range, Energy Shield's plating) and would be
        // silently lost if applied to an ability that had no owner yet.
        if (upgrade) {
            built.forEach(Ability::upgrade);
        }
        return place(unit, team, q, r);
    }
}
