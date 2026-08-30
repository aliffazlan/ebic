package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.combat.Attribute;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.AbilityFactory;
import com.walnutt.data.JsonDataLoader;
import com.walnutt.data.UnitDefinition;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.map.Tile;
import com.walnutt.status.Stat;
import com.walnutt.ui.ActionChoice;
import com.walnutt.ui.ChoiceOption;
import com.walnutt.ui.InputHandler;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.EliteUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/** Shawl - Insight from damage dealt, spent through a dialogue to unlock an ally's ability. */
class HiddenPotentialTest {

    /** Answers the unlock dialogue however the test says, and records what it was offered. */
    private static final class ScriptedChooser implements InputHandler {
        private final Function<List<ChoiceOption>, ChoiceOption> answer;
        private List<ChoiceOption> lastOffered = List.of();
        private int timesAsked;

        ScriptedChooser(Function<List<ChoiceOption>, ChoiceOption> answer) {
            this.answer = answer;
        }

        /** Takes the named ability if it is on offer AND enabled, mirroring a real client. */
        static ScriptedChooser picking(String id) {
            return new ScriptedChooser(options -> options.stream()
                .filter(option -> option.id().equals(id) && option.enabled())
                .findFirst().orElse(null));
        }

        static ScriptedChooser cancelling() {
            return new ScriptedChooser(options -> null);
        }

        @Override
        public ChoiceOption chooseOption(GameState state, Unit unit, String title, List<ChoiceOption> options) {
            lastOffered = options;
            timesAsked++;
            return answer.apply(options);
        }

        ChoiceOption offered(String id) {
            return lastOffered.stream().filter(o -> o.id().equals(id)).findFirst().orElse(null);
        }

        @Override
        public ActionChoice chooseAction(GameState state, Player player) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Attribute chooseAttribute(GameState state, Unit unit, Unit opponent) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UnitDefinition choosePick(GameState state, Player player, List<UnitDefinition> options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Tile choosePlacementTile(GameState state, Player player, Unit toPlace, List<Tile> candidates) {
            throw new UnsupportedOperationException();
        }
    }

    private static final Map<String, AbilityDefinition> DEFINITIONS =
        new JsonDataLoader(JsonDataLoader.locateDesignIdeasRoot()).loadAllAbilities();

    private static Ability real(String id) {
        AbilityDefinition definition = DEFINITIONS.get(id);
        assertNotNull(definition, id + ".json is missing");
        return AbilityFactory.create(id, definition);
    }

    private record Fixture(GameState state, HiddenPotential hiddenPotential, Unit shawl, Unit ally,
                            Unit enemy, ScriptedChooser chooser) {
    }

    /**
     * An ability whose upgrade is designed in JSON but has no implementation behind it.
     *
     * Every shipped ability is now implemented, so this has to be built rather than borrowed: a
     * real ability re-stamped with an id that is not in AbilityFactory's UPGRADE_IMPLEMENTED set,
     * which is exactly the shape of a hero added later with an upgrade nobody has written yet.
     * That is the case the disabled path exists for, and the only way left to cover it.
     */
    private static Ability designedButUnbuilt() {
        Ability ability = real("perplexing_shot");
        ability.setDefinitionId("not_yet_built");
        return ability;
    }

    /**
     * Shawl beside an ally who carries one ability with a working upgrade (Perplexing Shot) and
     * one whose upgrade is designed but unbuilt. Those two are what separate "enabled" from
     * "listed but not takeable" in the dialogue.
     */
    private static Fixture fixture(ScriptedChooser chooser) {
        Unit shawl = new EliteUnit("Shawl", Team.PLAYER_ONE, new UnitStats(48, 32, 54, 760));
        HiddenPotential hiddenPotential = (HiddenPotential) real("hidden_potential");
        shawl.addAbility(hiddenPotential);
        shawl.addAbility(real("acidic_brew"));

        Unit ally = new EliteUnit("Ally", Team.PLAYER_ONE, new UnitStats(30, 30, 30, 500));
        ally.addAbility(real("perplexing_shot"));
        ally.addAbility(designedButUnbuilt());

        Unit enemy = new BasicUnit("Enemy", Team.PLAYER_TWO, new UnitStats(10, 10, 10, 500));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(shawl);
        p1.addUnit(ally);
        p2.addUnit(enemy);

        GameMap map = new GameMap(4);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setInputHandler(chooser);
        state.setAbilityDefinitions(DEFINITIONS);
        state.setRemainingMoves(3);
        map.moveUnit(shawl, map.getTile(new Position(0, 0)));
        map.moveUnit(ally, map.getTile(new Position(0, 1)));
        map.moveUnit(enemy, map.getTile(new Position(0, 2)));
        return new Fixture(state, hiddenPotential, shawl, ally, enemy, chooser);
    }

    /** Deals `times` separate instances of damage from `source`, the way Insight is actually earned. */
    private static void dealDamage(GameState state, Unit source, Unit target, int times) {
        for (int i = 0; i < times; i++) {
            target.takeDamage(state, new DamageEvent(source, target, 1));
        }
    }

    @Test
    void insightAccruesOnePerInstanceOfDamageShawlDeals() {
        Fixture f = fixture(ScriptedChooser.picking("perplexing_shot"));
        assertEquals(0, f.hiddenPotential.getInsight(), "the pool exists from turn one, reading zero");

        dealDamage(f.state, f.shawl, f.enemy, 4);

        assertEquals(4, f.hiddenPotential.getInsight(), "one per instance, not per point of damage");
    }

    @Test
    void insightIgnoresDamageShawlNeitherDealtNorAimedOutward() {
        Fixture f = fixture(ScriptedChooser.picking("perplexing_shot"));

        dealDamage(f.state, f.ally, f.enemy, 3);   // an ally's work is not his
        dealDamage(f.state, f.enemy, f.shawl, 3);  // being hit is not dealing damage
        dealDamage(f.state, f.shawl, f.shawl, 3);  // and he cannot farm it off himself

        assertEquals(0, f.hiddenPotential.getInsight());
    }

    @Test
    void cannotBeCastWithoutTheFullCost_norOnAnEnemy() {
        Fixture f = fixture(ScriptedChooser.picking("perplexing_shot"));
        dealDamage(f.state, f.shawl, f.enemy, 9);

        assertFalse(f.hiddenPotential.canUse(f.state, new UnitTarget(f.ally)), "9 Insight, costs 10");

        dealDamage(f.state, f.shawl, f.enemy, 1);

        assertTrue(f.hiddenPotential.canUse(f.state, new UnitTarget(f.ally)));
        assertTrue(f.hiddenPotential.canUse(f.state, new UnitTarget(f.shawl)), "he may brew for himself");
        assertFalse(f.hiddenPotential.canUse(f.state, new UnitTarget(f.enemy)));
    }

    @Test
    void castingUnlocksTheChosenAbility_andSpendsTheInsightAndTheCooldown() {
        Fixture f = fixture(ScriptedChooser.picking("perplexing_shot"));
        dealDamage(f.state, f.shawl, f.enemy, 12);

        f.hiddenPotential.onUse(f.state, new UnitTarget(f.ally));

        Ability perplexingShot = f.ally.getAbilities().stream()
            .filter(a -> "perplexing_shot".equals(a.getDefinitionId())).findFirst().orElseThrow();
        assertTrue(perplexingShot.isUpgraded());
        assertEquals(10, perplexingShot.getStats().get("bounces").intValue());
        assertEquals(2, f.hiddenPotential.getInsight(), "12 earned, 10 spent");
        assertEquals(1, f.hiddenPotential.getUpgradesGranted());
        assertFalse(f.hiddenPotential.isReady());
    }

    @Test
    void cancellingCostsNeitherInsightNorCooldown() {
        Fixture f = fixture(ScriptedChooser.cancelling());
        dealDamage(f.state, f.shawl, f.enemy, 12);

        f.hiddenPotential.onUse(f.state, new UnitTarget(f.ally));

        assertEquals(1, f.chooser.timesAsked, "the dialogue was genuinely raised");
        assertEquals(12, f.hiddenPotential.getInsight(), "nothing spent");
        assertEquals(0, f.hiddenPotential.getUpgradesGranted());
        assertTrue(f.hiddenPotential.isReady(), "and it can be cast again immediately");
    }

    @Test
    void theDialogueListsWhatCannotBeTakenAlongsideWhatCan() {
        Fixture f = fixture(ScriptedChooser.picking("perplexing_shot"));
        dealDamage(f.state, f.shawl, f.enemy, 30);

        f.hiddenPotential.onUse(f.state, new UnitTarget(f.ally));

        // An upgrade designed but not built is shown with its reason rather than quietly omitted.
        ChoiceOption pending = f.chooser.offered("not_yet_built");
        assertNotNull(pending, "an upgrade that exists in JSON is always listed");
        assertFalse(pending.enabled());
        assertEquals("Not yet available", pending.detail());

        // Move and Attack carry no upgrade at all and are not worth a row on every unit.
        assertNotNull(f.chooser.offered("perplexing_shot"));
        assertEquals(2, f.chooser.lastOffered.size(), "only the two real abilities");
    }

    @Test
    void anAlreadyUnlockedAbilityIsShownGreyedOutRatherThanOfferedTwice() {
        Fixture f = fixture(ScriptedChooser.picking("perplexing_shot"));
        dealDamage(f.state, f.shawl, f.enemy, 30);
        f.hiddenPotential.onUse(f.state, new UnitTarget(f.ally));
        f.hiddenPotential.decreaseCooldown(99);

        f.hiddenPotential.onUse(f.state, new UnitTarget(f.ally));

        ChoiceOption perplexingShot = f.chooser.offered("perplexing_shot");
        assertNotNull(perplexingShot);
        assertFalse(perplexingShot.enabled());
        assertEquals("Already upgraded", perplexingShot.detail());
        assertEquals(1, f.hiddenPotential.getUpgradesGranted(), "the second cast unlocked nothing");
        assertEquals(20, f.hiddenPotential.getInsight(), "and so charged nothing");
    }

    @Test
    void anAllyWithNothingToUnlockStillAcceptsTheCastAndSaysSo() {
        Fixture f = fixture(ScriptedChooser.picking("perplexing_shot"));
        dealDamage(f.state, f.shawl, f.enemy, 12);
        Unit basic = new BasicUnit("Basic", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 100));
        f.state.getPlayers().get(0).addUnit(basic);
        f.state.getMap().moveUnit(basic, f.state.getMap().getTile(new Position(1, 0)));

        // Deliberately targetable: refusing the click would leave the player guessing why.
        assertTrue(f.hiddenPotential.canUse(f.state, new UnitTarget(basic)));
        f.hiddenPotential.onUse(f.state, new UnitTarget(basic));

        assertEquals(1, f.chooser.lastOffered.size());
        assertFalse(f.chooser.lastOffered.get(0).enabled());
        assertEquals("Nothing to upgrade", f.chooser.lastOffered.get(0).detail());
        assertEquals(12, f.hiddenPotential.getInsight(), "a dead end charges nothing");
    }

    @Test
    void unlockingHiddenPotentialItselfPaysOutForEveryUnlockAlreadyGranted() {
        Fixture f = fixture(ScriptedChooser.picking("perplexing_shot"));
        dealDamage(f.state, f.shawl, f.enemy, 40);

        // Two unlocks first, while he is not yet paying out at all.
        f.hiddenPotential.onUse(f.state, new UnitTarget(f.ally));
        f.hiddenPotential.decreaseCooldown(99);
        assertEquals(48, (int) f.shawl.getEffective(Stat.STRENGTH), "no bonus until he is unlocked himself");

        f.chooser.lastOffered = List.of();
        int healthBefore = f.shawl.getHealth();

        // Now unlock Hidden Potential itself: worth the backlog (1) plus itself (1) = 2 steps.
        ScriptedChooser selfPick = ScriptedChooser.picking("hidden_potential");
        f.state.setInputHandler(selfPick);
        f.hiddenPotential.onUse(f.state, new UnitTarget(f.shawl));

        assertTrue(f.hiddenPotential.isUpgraded());
        assertEquals(2, f.hiddenPotential.getUpgradesGranted());
        assertEquals(48 + 40, (int) f.shawl.getEffective(Stat.STRENGTH), "20 per unlock, retroactively");
        assertEquals(32 + 40, (int) f.shawl.getEffective(Stat.AGILITY));
        assertEquals(54 + 40, (int) f.shawl.getEffective(Stat.INTELLIGENCE));
        assertEquals(760 + 200, (int) f.shawl.getEffective(Stat.MAX_HEALTH));
        assertEquals(healthBefore + 200, f.shawl.getHealth(), "current health rises with the ceiling");
    }

    /**
     * The trap AbilityTagsTest already guards in the JSON, checked here through a real kit:
     * Hidden Potential's machinery assumes it sits on the unit whose Insight pool it created,
     * so a copy on Joker would be spending a pool that does not exist.
     */
    @Test
    void mimicCannotStealHiddenPotential() {
        AbilityDefinition definition = DEFINITIONS.get("hidden_potential");
        assertTrue(definition.hasTag(AbilityDefinition.NO_COPY));
    }
}
