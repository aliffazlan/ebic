package com.walnutt.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Attack;
import com.walnutt.ability.Move;
import com.walnutt.effect.Effect;
import com.walnutt.effect.impl.HomingMissileEffect;
import com.walnutt.effect.impl.OrbEffect;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.status.EffectCategory;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.ChampionUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;
import com.walnutt.web.dto.EffectSnapshot;
import com.walnutt.web.dto.GameStateSnapshot;
import com.walnutt.web.dto.TileEffectSnapshot;
import com.walnutt.web.dto.UnitSnapshot;

/** Pure mapper test - hand-build a small GameState, no JSON/engine loop involved. */
class GameStateSnapshotMapperTest {

    /**
     * Both of these describe something that has not happened yet, which is the whole
     * reason they are on the wire at all: a delay only gives the opponent a chance to
     * react if they can see where the blast is going to be.
     */
    @Test
    void flattensAPendingEclipseOverItsWholeBlastRadius() {
        GameMap map = new GameMap(5);
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        Unit harbinger = new ChampionUnit("Harbinger", Team.PLAYER_ONE, new UnitStats(40, 40, 90, 1000));
        p1.addUnit(harbinger);
        map.moveUnit(harbinger, map.getTile(new Position(0, 0)));
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));

        harbinger.addEffect(new OrbEffect(harbinger, new Position(0, 2), 1, 1, 1.0));
        List<TileEffectSnapshot> eclipse = new GameStateSnapshotMapper(new UnitIdRegistry())
            .toSnapshot(state).tileEffects().stream().filter(t -> t.kind().equals("eclipse")).toList();

        // A radius-1 blast covers the centre plus its six neighbours, all of which exist
        // on a plain radius-5 board.
        assertEquals(7, eclipse.size());
        assertTrue(eclipse.stream().anyMatch(t -> t.q() == 0 && t.r() == 2), "the centre is painted too");
    }

    @Test
    void marksAHomingMissileOnWhicheverTileItsTargetIsCurrentlyStandingOn() {
        GameMap map = new GameMap(5);
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        Unit maxwell = new ChampionUnit("Maxwell", Team.PLAYER_ONE, new UnitStats(18, 12, 84, 510));
        Unit victim = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(20, 20, 20, 300));
        p1.addUnit(maxwell);
        p2.addUnit(victim);
        map.moveUnit(maxwell, map.getTile(new Position(0, 0)));
        map.moveUnit(victim, map.getTile(new Position(0, 3)));
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));

        victim.addEffect(new HomingMissileEffect(maxwell, 1, 50, 20));
        GameStateSnapshotMapper mapper = new GameStateSnapshotMapper(new UnitIdRegistry());

        List<TileEffectSnapshot> before = missileMarkers(mapper.toSnapshot(state));
        assertEquals(1, before.size());
        assertEquals(3, before.get(0).r());

        // The effect stores no position of its own, so the marker follows the target
        // simply by being recomputed from wherever they now are.
        map.moveUnit(victim, map.getTile(new Position(0, 1)));
        List<TileEffectSnapshot> after = missileMarkers(mapper.toSnapshot(state));
        assertEquals(1, after.size());
        assertEquals(1, after.get(0).r());
    }

    private static List<TileEffectSnapshot> missileMarkers(GameStateSnapshot snapshot) {
        return snapshot.tileEffects().stream().filter(t -> t.kind().equals("missile")).toList();
    }

    @Test
    void mapsUnitPositionsStatsAndAbilities() {
        GameMap map = new GameMap(5);
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);

        Unit champ = new ChampionUnit("Valor", Team.PLAYER_ONE, new UnitStats(60, 50, 60, 1100));
        champ.addAbility(new Move());
        champ.addAbility(new Attack());
        p1.addUnit(champ);

        Unit basic = new BasicUnit("P2 Basic 1", Team.PLAYER_TWO, new UnitStats(20, 20, 20, 300));
        basic.addAbility(new Move());
        basic.addAbility(new Attack());
        p2.addUnit(basic);

        map.moveUnit(champ, map.getTile(new Position(0, 0)));
        map.moveUnit(basic, map.getTile(new Position(1, 0)));

        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);

        GameStateSnapshotMapper mapper = new GameStateSnapshotMapper(new UnitIdRegistry());
        GameStateSnapshot snapshot = mapper.toSnapshot(state);

        assertEquals("PLAYER_ONE", snapshot.currentTeam());
        assertEquals(3, snapshot.remainingMoves());
        assertFalse(snapshot.gameOver());
        assertEquals(5, snapshot.mapRadius());
        assertEquals(2, snapshot.units().size());

        UnitSnapshot champSnap = snapshot.units().stream().filter(u -> u.name().equals("Valor")).findFirst().orElseThrow();
        assertEquals("valor", champSnap.definitionId());
        assertEquals("CHAMPION", champSnap.unitType());
        assertEquals("PLAYER_ONE", champSnap.team());
        assertEquals(0, champSnap.q());
        assertEquals(0, champSnap.r());
        assertEquals(1100, champSnap.currentHp());
        assertEquals(1100, champSnap.maxHp());
        assertFalse(champSnap.dead());
        assertNotNull(champSnap.id());
        // Move + Attack are real Abilities on the unit and must be addressable by id
        // the same way any other ability is (the client needs an abilityId to submit
        // a move/attack action) - see WebInputHandler.
        assertTrue(champSnap.abilities().stream().anyMatch(a -> a.id().equals("move")));
        assertTrue(champSnap.abilities().stream().anyMatch(a -> a.id().equals("attack")));

        UnitSnapshot basicSnap = snapshot.units().stream().filter(u -> u.unitType().equals("BASIC")).findFirst().orElseThrow();
        assertEquals("basic", basicSnap.definitionId());
    }

    @Test
    void assignsStableIdsAcrossCallsForTheSameUnitInstance() {
        Unit unit = new ChampionUnit("Chronos", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 100));
        UnitIdRegistry registry = new UnitIdRegistry();
        GameStateSnapshotMapper mapper = new GameStateSnapshotMapper(registry);

        String first = mapper.toUnitSnapshot(unit).id();
        String second = mapper.toUnitSnapshot(unit).id();

        assertEquals(first, second);
        assertEquals(unit, registry.resolve(first));
    }

    @Test
    void mapsActiveEffectsWithNameDescriptionCategoryAndFlags() {
        Unit unit = new ChampionUnit("Cursed", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 100));
        Effect stun = new Effect("Stunned", "Can't act this turn.", 2) {
            {
                category = EffectCategory.DEBUFF;
                flags.add(StatusFlag.STUNNED);
            }
        };
        unit.addEffect(stun);

        GameStateSnapshotMapper mapper = new GameStateSnapshotMapper(new UnitIdRegistry());
        UnitSnapshot snap = mapper.toUnitSnapshot(unit);

        assertEquals(1, snap.effects().size());
        EffectSnapshot effectSnap = snap.effects().get(0);
        assertEquals("Stunned", effectSnap.name());
        assertEquals("Can't act this turn.", effectSnap.description());
        assertEquals("DEBUFF", effectSnap.category());
        assertFalse(effectSnap.permanent());
        assertEquals(2, effectSnap.remainingTurns());
        assertTrue(effectSnap.statusFlags().contains("STUNNED"));
        assertEquals(null, effectSnap.extraInfo(), "a plain effect with no dynamic state overrides nothing, extraInfo stays null");
    }

    @Test
    void mapsExtraInfoForEffectsWithDynamicRuntimeState() {
        Unit unit = new ChampionUnit("Cursed", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 100));
        Effect stacking = new Effect("Stacking Curse", "Grows stronger each stack.", 3) {
            @Override
            public String getExtraInfo() {
                return "Next hit: 12 damage";
            }
        };
        unit.addEffect(stacking);

        GameStateSnapshotMapper mapper = new GameStateSnapshotMapper(new UnitIdRegistry());
        EffectSnapshot effectSnap = mapper.toUnitSnapshot(unit).effects().get(0);

        assertEquals("Next hit: 12 damage", effectSnap.extraInfo());
    }

    @Test
    void permanentEffectIsFlaggedRatherThanExposingItsHugeRemainingTurnsNumber() {
        Unit unit = new ChampionUnit("Doomed", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 100));
        Effect doom = new Effect("Doom", "Silenced until you land a kill.", Effect.PERMANENT) {
        };
        unit.addEffect(doom);

        GameStateSnapshotMapper mapper = new GameStateSnapshotMapper(new UnitIdRegistry());
        EffectSnapshot effectSnap = mapper.toUnitSnapshot(unit).effects().get(0);

        assertTrue(effectSnap.permanent());
        assertEquals(0, effectSnap.remainingTurns());
    }

    @Test
    void deadUnitIsFlaggedButKeepsItsLastKnownPosition() {
        GameMap map = new GameMap(5);
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Unit unit = new BasicUnit("Dead Basic", Team.PLAYER_ONE, new UnitStats(1, 1, 1, 10));
        p1.addUnit(unit);
        map.moveUnit(unit, map.getTile(new Position(2, -1)));
        unit.getHealthPool().setCurrent(0);

        GameStateSnapshotMapper mapper = new GameStateSnapshotMapper(new UnitIdRegistry());
        UnitSnapshot snap = mapper.toUnitSnapshot(unit);

        assertTrue(snap.dead());
        assertEquals(2, snap.q());
        assertEquals(-1, snap.r());
    }
}
