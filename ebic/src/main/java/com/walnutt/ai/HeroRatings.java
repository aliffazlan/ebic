package com.walnutt.ai;

import java.util.Map;
import java.util.Set;

import com.walnutt.data.UnitDefinition;
import com.walnutt.web.Identifiers;

/**
 * How much the bot wants a given hero, used to pick the better of the two offered each
 * draft round.
 *
 * Most of the rating is *derived from the hero's own definition* rather than hand-typed,
 * so a balance pass that changes a stat block moves the draft preference automatically
 * instead of silently leaving the bot drafting to last season's numbers. Only the part
 * that stats genuinely cannot express - a kit that adds whole extra units to the board,
 * or grants permanent rather than temporary gains - is hand-adjusted, and each
 * adjustment below states the objective reason it exists.
 *
 * Ratings are only ever compared within a draft round, which always offers two heroes of
 * the same type, so the champion and elite scales never need to be commensurate.
 */
public final class HeroRatings {

    /**
     * Kit value not visible in a stat block. Deliberately limited to objective facts -
     * "this summons another body", "this gain is permanent" - rather than taste, so it
     * stays defensible as the roster changes. Anything absent scores 0, which is the
     * right default for a kit whose value is already reflected in its stats.
     */
    private static final Map<String, Double> KIT_ADJUSTMENTS = Map.ofEntries(
        // Summons additional units - raw stats price one body, these put more on the board.
        Map.entry("yuki", 60.0),        // Snow Golem is a 1000 HP unit, more than Yuki herself
        Map.entry("zenith", 30.0),      // Pylons - attackable bodies that don't block movement
        Map.entry("branch", 30.0),      // Overgrowth's Branchlings
        Map.entry("lanaya", 20.0),      // Psychic Projection's player-controlled clone
        // Permanent gains rather than temporary buffs - they compound over a long match.
        Map.entry("valor", 25.0),       // Duel's winner keeps +20 to every attribute
        Map.entry("grivath", 20.0),     // Cripple's drained stats transfer permanently
        Map.entry("dirge", 15.0),       // Decay's permanent max-health gain
        // Effects with no natural duration, or that protect other units outright.
        Map.entry("lucifer", 40.0),     // Doom never expires on its own - it needs a kill to lift
        Map.entry("thaddeus", 45.0)     // The only real barrier/dispel support in the pool
    );

    /**
     * Heroes the bot refuses to draft, whatever their stats say.
     *
     * Maxwell is here on purpose, not because the bot cannot cope with him - self-play
     * runs him without error and the scoring baselines are unchanged - but because
     * playing him *well* is a different problem from playing him legally. His whole kit
     * arrives through Eureka's construction choice, which is a long-horizon investment
     * decision the greedy scorer has no way to reason about: it cannot see that a gadget
     * bought now pays off in ten turns. Until the bot can plan that far, offering him is
     * a worse experience than simply passing.
     *
     * Joker is here for the same class of reason. Mimic's payoff is an ability acquired
     * now for use later, and whether that is worth a cast depends on what the enemy will
     * do over the following turns - again invisible to a scorer that only looks at the
     * position in front of it. Superior Mastery compounds it, since playing him well
     * means sequencing casts to milk the cooldown refunds.
     *
     * Shawl is the third, and the clearest case of the three. His entire contribution is
     * Hidden Potential, which spends a currency now to make an ALLY better later - the same
     * long-horizon investment the greedy scorer cannot price, except that here the payoff
     * lands on a different unit entirely. A bot Shawl would bank Insight it never spends
     * well, on a hero with 760 HP and no way to use it.
     *
     * A round offering two avoided heroes is dealt again before anyone sees it rather than
     * resolved by picking one anyway - see ConcurrentSetupFlow.drawTwoFor. That reroll is
     * what lets this list grow without the bot ever being cornered into an entry.
     */
    private static final Set<String> AVOIDED = Set.of("maxwell", "joker", "shawl");

    private HeroRatings() {
    }

    /** True if the bot should take the other option in this round rather than this one. */
    public static boolean isAvoided(UnitDefinition definition) {
        return AVOIDED.contains(Identifiers.normalize(definition.name()));
    }

    public static double rate(UnitDefinition definition) {
        int statTotal = definition.strength() + definition.agility() + definition.intelligence();
        int bestStat = Math.max(definition.strength(),
            Math.max(definition.agility(), definition.intelligence()));

        double score = definition.maxHp() * 0.05
            + statTotal
            + (definition.effectiveAttackRange() - 1) * 15.0;

        // Reward a spread of attributes. The encounter is rock-paper-scissors on the
        // three attributes, so a hero with everything in one of them is easy to defend
        // against - the opponent simply picks the attribute that beats it.
        score += (statTotal - bestStat) * 0.3;

        return score + KIT_ADJUSTMENTS.getOrDefault(Identifiers.normalize(definition.name()), 0.0);
    }

    /**
     * The better of two offered heroes. Ties go to the first, which is arbitrary but
     * harmless.
     *
     * An avoided hero loses to any alternative regardless of rating, but still wins
     * against another avoided one - the caller must always get a real pick back, since
     * the draft has no "pass" and returning null would abort the match.
     */
    public static UnitDefinition preferred(UnitDefinition a, UnitDefinition b) {
        boolean avoidA = isAvoided(a);
        boolean avoidB = isAvoided(b);
        if (avoidA != avoidB) {
            return avoidA ? b : a;
        }
        return rate(b) > rate(a) ? b : a;
    }
}
