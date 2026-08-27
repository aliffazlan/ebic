package com.walnutt.ai;

import java.util.Map;

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

    private HeroRatings() {
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

    /** The better of two offered heroes. Ties go to the first, which is arbitrary but harmless. */
    public static UnitDefinition preferred(UnitDefinition a, UnitDefinition b) {
        return rate(b) > rate(a) ? b : a;
    }
}
