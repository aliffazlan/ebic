package com.walnutt.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.effect.Effect;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;

/**
 * Regression cover for Unit.removeExpiredEffects, which runs each expiring effect's
 * onExpire hook and then removes it. The hook is allowed to do real work - including
 * granting a new effect - so expiry must not be walking a live iterator while that
 * happens.
 */
class EffectExpiryTest {

    private static GameState newState() {
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        return new GameState(new GameMap(3), List.of(p1, p2), new Random(1));
    }

    /** An effect whose payload grants another effect to its OWN owner - Sprout's barrier shape. */
    private static class GrantsToSelfOnExpire extends Effect {
        GrantsToSelfOnExpire(int duration) {
            super("Grants To Self", duration);
        }

        @Override
        public void onExpire(GameState state) {
            getOwner().addEffect(new Effect("Granted", Effect.PERMANENT) {
            });
        }
    }

    @Test
    void anEffectMayGrantAnEffectToItsOwnOwnerWhileExpiring() {
        GameState state = newState();
        Unit unit = new BasicUnit("Subject", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 100));
        unit.addEffect(new GrantsToSelfOnExpire(0));

        // Before the fix this threw ConcurrentModificationException: onExpire mutated
        // `effects` while removeExpiredEffects held an iterator over it.
        unit.removeExpiredEffects(state);

        assertTrue(unit.getEffects().stream().anyMatch(e -> e.getName().equals("Granted")),
            "the granted effect should have been added");
        assertTrue(unit.getEffects().stream().noneMatch(e -> e.getName().equals("Grants To Self")),
            "the expired effect should have been removed");
        assertEquals(1, unit.getEffects().size());
    }

    @Test
    void severalEffectsExpiringAtOnceAreAllRemoved() {
        GameState state = newState();
        Unit unit = new BasicUnit("Subject", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 100));
        unit.addEffect(new Effect("A", 0) {
        });
        unit.addEffect(new Effect("B", 0) {
        });
        unit.addEffect(new Effect("Survivor", 5) {
        });

        unit.removeExpiredEffects(state);

        assertEquals(1, unit.getEffects().size());
        assertEquals("Survivor", unit.getEffects().get(0).getName());
    }
}
