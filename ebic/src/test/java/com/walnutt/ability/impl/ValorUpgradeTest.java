package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.NoTarget;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.effect.impl.DuelEffect;
import com.walnutt.event.DamageEvent;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

import com.walnutt.combat.Attribute;
import com.walnutt.combat.CombatEngine;
import com.walnutt.game.Team;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/** Valor's unlocked abilities. */
class ValorUpgradeTest {

    /** Damage the attacker takes purely from being countered, with the swing itself unmeasured. */
    private static int counterDamageAgainst(boolean upgraded) {
        UpgradeFixture f = UpgradeFixture.create();
        Unit valor = f.heroWith("Valor", Team.PLAYER_ONE, new UnitStats(60, 20, 20, 2000),
            0, 0, upgraded, "counterstrike");
        Unit attacker = f.basic("Attacker", Team.PLAYER_TWO, new UnitStats(10, 10, 40, 2000), 0, 1);

        int before = attacker.getHealth();
        // The attacker brings INTELLIGENCE and Valor defends with STRENGTH, so Valor wins the
        // matchup: the swing deals nothing and every point the attacker loses is the counter.
        CombatEngine.performAttack(f.state(), attacker, valor, Attribute.INTELLIGENCE, Attribute.STRENGTH);
        return before - attacker.getHealth();
    }

    @Test
    void upgradedCounterstrikeHitsHarderThanTheSwingItAnswers() {
        int base = counterDamageAgainst(false);
        int upgraded = counterDamageAgainst(true);

        assertTrue(base > 0, "the counter should land at all");
        // 80% of a full counter becomes 120% of one - half again as much, not merely the
        // penalty lifted.
        assertEquals(Math.round(base / 0.8 * 1.2), upgraded);
        assertTrue(upgraded > base);
    }

    private static Ability on(Unit unit, String id) {
        return unit.getAbilities().stream()
            .filter(a -> id.equals(a.getDefinitionId())).findFirst().orElseThrow();
    }

    @Test
    void upgradedDuellistsTakeFarMoreDamageFromEachOtherAndNobodyElse() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit valor = f.heroWith("Valor", Team.PLAYER_ONE, new UnitStats(50, 30, 30, 2000),
            0, 0, true, "duel");
        Unit rival = f.basic("Rival", Team.PLAYER_TWO, new UnitStats(30, 30, 30, 2000), 0, 1);
        Unit bystander = f.basic("Bystander", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 2000), 1, 0);

        on(valor, "duel").onUse(f.state(), new UnitTarget(rival));

        rival.takeDamage(f.state(), new DamageEvent(valor, rival, 100));
        assertEquals(300, 2000 - rival.getHealth(), "three times as much (200% more) from their duel partner");

        rival.takeDamage(f.state(), new DamageEvent(bystander, rival, 100));
        assertEquals(400, 2000 - rival.getHealth(), "a third party is unaffected");

        valor.takeDamage(f.state(), new DamageEvent(rival, valor, 100));
        assertEquals(200, 2000 - valor.getHealth(), "what Valor takes back stays at the base rate (100% more)");
    }

    /** Base Duel already carries the mutual 100% amp - that part is no longer upgrade-only. */
    @Test
    void theBaseDuelAlreadyAmplifiesBothWaysEqually() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit valor = f.heroWith("Valor", Team.PLAYER_ONE, new UnitStats(50, 30, 30, 2000),
            0, 0, false, "duel");
        Unit rival = f.basic("Rival", Team.PLAYER_TWO, new UnitStats(30, 30, 30, 2000), 0, 1);

        on(valor, "duel").onUse(f.state(), new UnitTarget(rival));
        rival.takeDamage(f.state(), new DamageEvent(valor, rival, 100));

        assertEquals(200, 2000 - rival.getHealth(), "twice as much, even un-upgraded");
    }

    @Test
    void upgradedWinningADuelBringsItStraightBackUp() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit valor = f.heroWith("Valor", Team.PLAYER_ONE, new UnitStats(50, 30, 30, 2000),
            0, 0, true, "duel");
        Unit rival = f.basic("Rival", Team.PLAYER_TWO, new UnitStats(30, 30, 30, 100), 0, 1);

        on(valor, "duel").onUse(f.state(), new UnitTarget(rival));
        assertFalse(on(valor, "duel").isReady(), "spent on the cast");

        rival.takeDamage(f.state(), new DamageEvent(valor, rival, 500));

        assertTrue(rival.isDead());
        assertTrue(on(valor, "duel").isReady(), "a duel won is a duel that can start again");
    }

    @Test
    void theBaseDuelStaysOnCooldownAfterAWin() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit valor = f.heroWith("Valor", Team.PLAYER_ONE, new UnitStats(50, 30, 30, 2000),
            0, 0, false, "duel");
        Unit rival = f.basic("Rival", Team.PLAYER_TWO, new UnitStats(30, 30, 30, 100), 0, 1);

        on(valor, "duel").onUse(f.state(), new UnitTarget(rival));
        rival.takeDamage(f.state(), new DamageEvent(valor, rival, 500));

        assertTrue(rival.isDead());
        assertFalse(on(valor, "duel").isReady());
    }

    /**
     * The passive half counts the whole board, and hooks the damage pipeline rather than
     * onPostAttack - which is what makes it fire on a Counterstrike counter too, and land
     * after that counter's own damage reduction rather than inside it.
     */
    @Test
    void upgradedOverwhelmingOddsMakesEveryAttackWorthMoreWhenHisSideHasTheNumbers() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit valor = f.heroWith("Valor", Team.PLAYER_ONE, new UnitStats(50, 10, 10, 2000),
            0, 0, true, "overwhelming_odds");
        f.basic("AllyA", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 2000), 1, 0);
        f.basic("AllyB", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 2000), 2, 0);
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 30, 2000), 0, 1);

        // Three allies against one enemy: an advantage of 2, worth 10 apiece.
        CombatEngine.performAttack(f.state(), valor, victim, Attribute.STRENGTH, Attribute.INTELLIGENCE);

        assertEquals(70, 2000 - victim.getHealth(), "50 of strength, plus 20 for the numbers");
    }

    @Test
    void upgradedOverwhelmingOddsHealsHimInsteadWhenHeIsOutnumbered() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit valor = f.heroWith("Valor", Team.PLAYER_ONE, new UnitStats(50, 10, 10, 2000),
            0, 0, true, "overwhelming_odds");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 30, 2000), 0, 1);
        f.basic("EnemyB", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 2000), 1, 0);
        f.basic("EnemyC", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 2000), 2, 0);
        valor.getHealthPool().setCurrent(1000);

        CombatEngine.performAttack(f.state(), valor, victim, Attribute.STRENGTH, Attribute.INTELLIGENCE);

        assertEquals(50, 2000 - victim.getHealth(), "no harder, when he is the one outnumbered");
        assertEquals(1010, valor.getHealth(), "5 per unit of the deficit of 2");
    }

    @Test
    void theBasePassiveDoesNothingAtAll() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit valor = f.heroWith("Valor", Team.PLAYER_ONE, new UnitStats(50, 10, 10, 2000),
            0, 0, false, "overwhelming_odds");
        f.basic("Ally", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 2000), 1, 0);
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 30, 2000), 0, 1);

        CombatEngine.performAttack(f.state(), valor, victim, Attribute.STRENGTH, Attribute.INTELLIGENCE);

        assertEquals(50, 2000 - victim.getHealth());
        assertTrue(on(valor, "overwhelming_odds").canUse(f.state(), new NoTarget()),
            "the active half is untouched");
    }
}
