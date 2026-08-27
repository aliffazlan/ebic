package com.walnutt.ai;

/**
 * How much one ability cast is worth, in the same "expected damage" currency the rest
 * of the bot's weights use.
 *
 * Hints are content, not machinery. The bot can already play every ability *legally*
 * without any of them (see {@link LegalActionEnumerator}); a hint is what makes it play
 * one *well*. They accrete one at a time - see {@link AbilityHints} - so an ability
 * added tomorrow works from day one on the generic fallback and can be taught later.
 */
@FunctionalInterface
public interface AbilityHint {
    double score(HintContext context);
}
