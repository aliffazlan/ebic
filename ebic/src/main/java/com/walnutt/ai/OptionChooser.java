package com.walnutt.ai;

import java.util.List;

import com.walnutt.ui.ChoiceOption;

/**
 * How the bot answers a {@link com.walnutt.ui.InputHandler#chooseOption} dialogue.
 *
 * Only Maxwell's Eureka raises one today, so the knowledge here is a preference ordering
 * over his gadgets, in the spirit of {@link HeroRatings}: crude, but far better than the
 * interface default of "take the first option", which would have every bot Maxwell build
 * the same gadget every match.
 *
 * The ordering favours things whose value the greedy scorer can actually see and act on -
 * direct damage and a body on the board - over the ones that pay off through positioning
 * or patience, which it does not yet plan for. Reload in particular is close to worthless
 * to a bot that acts every turn by construction.
 *
 * Anything unrecognised falls through to the first option, so a future ability raising a
 * dialogue of its own still gets a legal answer without touching this class.
 */
public final class OptionChooser {

    private static final List<String> GADGET_PREFERENCE = List.of(
        "plasma_cannon",
        "homing_missile",
        "killer_drone",
        "shrink_ray",
        "gyroscope",
        "energy_shield",
        "nanobots",
        "translocation",
        "reload"
    );

    private OptionChooser() {
    }

    /**
     * Disabled options are skipped outright rather than ranked: they are shown to a human to
     * explain an absence, and answering with one would be an illegal move. A dialogue with
     * nothing enabled yields null, which every caller already treats as "cancel".
     */
    public static ChoiceOption choose(List<ChoiceOption> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        ChoiceOption best = null;
        int bestRank = Integer.MAX_VALUE;
        for (ChoiceOption option : options) {
            if (!option.enabled()) {
                continue;
            }
            int optionRank = rank(option);
            if (best == null || optionRank < bestRank) {
                best = option;
                bestRank = optionRank;
            }
        }
        return best;
    }

    /** Lower is better; anything unlisted sorts after everything listed. */
    private static int rank(ChoiceOption option) {
        int index = GADGET_PREFERENCE.indexOf(option.id());
        return index < 0 ? Integer.MAX_VALUE : index;
    }
}
