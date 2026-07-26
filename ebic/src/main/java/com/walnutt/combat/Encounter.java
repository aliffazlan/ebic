package com.walnutt.combat;

import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/**
 * An encounter knows who's fighting and HOW the attacker/defender attribute is
 * picked - the three ways to pick are NormalEncounter (attributes chosen by a
 * player/ability ahead of time), RandomEncounter (uniform random), and
 * WeightedEncounter (random, weighted by each unit's own attribute values).
 * EncounterResolver only ever calls resolveAttackerAttribute/resolveDefenderAttribute
 * - it doesn't need to know which kind of encounter it's resolving.
 */
public sealed interface Encounter permits NormalEncounter, RandomEncounter, WeightedEncounter {
    Unit attacker();

    Unit defender();

    Attribute resolveAttackerAttribute(GameState state);

    Attribute resolveDefenderAttribute(GameState state);
}
