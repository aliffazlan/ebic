package com.walnutt.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/** The four worked examples from chat.txt's combat spec, byte-for-byte. */
class EncounterResolverTest {

    private final EncounterResolver resolver = new EncounterResolver();

    private GameState newState(Unit... units) {
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        for (Unit u : units) {
            (u.getTeam() == Team.PLAYER_ONE ? p1 : p2).addUnit(u);
        }
        return new GameState(new GameMap(3), List.of(p1, p2), new Random(1));
    }

    private Unit attacker() {
        return new BasicUnit("Attacker", Team.PLAYER_ONE, new UnitStats(50, 30, 10, 100));
    }

    private Unit defender() {
        return new BasicUnit("Defender", Team.PLAYER_TWO, new UnitStats(20, 80, 40, 100));
    }

    @Test
    void strengthBeatsIntelligence() {
        Unit a = attacker();
        Unit d = defender();
        GameState state = newState(a, d);
        DamageEvent event = resolver.resolve(state, new NormalEncounter(a, d, Attribute.STRENGTH, Attribute.INTELLIGENCE));
        assertEquals(50, event.getDamage());
    }

    @Test
    void agilityBeatsStrength_noDamage() {
        Unit a = attacker();
        Unit d = defender();
        GameState state = newState(a, d);
        DamageEvent event = resolver.resolve(state, new NormalEncounter(a, d, Attribute.STRENGTH, Attribute.AGILITY));
        assertEquals(0, event.getDamage());
    }

    @Test
    void sameAttribute_differenceApplies() {
        Unit a = attacker();
        Unit d = defender();
        GameState state = newState(a, d);
        DamageEvent event = resolver.resolve(state, new NormalEncounter(a, d, Attribute.STRENGTH, Attribute.STRENGTH));
        assertEquals(30, event.getDamage());
    }

    @Test
    void sameAttribute_negativeDifferenceClampsToZero() {
        Unit a = attacker();
        Unit d = defender();
        GameState state = newState(a, d);
        DamageEvent event = resolver.resolve(state, new NormalEncounter(a, d, Attribute.AGILITY, Attribute.AGILITY));
        assertEquals(0, event.getDamage());
    }
}
