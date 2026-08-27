package com.walnutt.ai;

/**
 * Every tunable number the bot uses, in one immutable object.
 *
 * This exists so that difficulty levels and self-play A/B tuning are *configuration*
 * rather than new code: {@link BotStrategy} and {@link ActionScorer} read weights from
 * here and never hardcode them. A new difficulty is a new factory method; an A/B
 * experiment is two instances handed to SelfPlayRunner.
 *
 * Weights are deliberately expressed in "expected damage" units wherever possible -
 * i.e. a bonus of 30 means "worth about 30 damage" - so they stay comparable to each
 * other and keep their meaning when unit stats are rebalanced.
 */
public record BotConfig(
    /** Multiplier on expected damage from an attack. The unit all other weights are quoted against. */
    double damageWeight,
    /** Flat bonus when an action is expected to actually kill the target. */
    double killBonus,
    /** Extra multiplier applied on top of killBonus when the victim is the enemy champion (i.e. wins the game). */
    double championKillMultiplier,
    /** Priority multiplier for targeting the enemy champion at full health. */
    double championTargetWeight,
    /** Priority multiplier for targeting an enemy elite. */
    double eliteTargetWeight,
    /** Priority multiplier for targeting an enemy basic. */
    double basicTargetWeight,
    /** How much to favour finishing an already-wounded target, scaled by the fraction of its health already gone. */
    double woundedTargetWeight,
    /** Value of each tile of distance closed toward this unit's focus target. */
    double approachWeight,
    /** Bonus for a move that ends with the mover able to attack something it could not reach before. */
    double enterRangeBonus,
    /** Penalty for a move that walks a fragile unit into an enemy's reach. */
    double exposurePenalty,
    /** Baseline value of casting a ready ability with no specific hint, before its own hint adjusts it. */
    double genericAbilityValue,
    /** Score below which an action is not worth spending a move point on; the bot ends its turn instead. */
    double minimumActionScore,
    /**
     * Whether the bot takes its cost-free BASIC attacks. Always true in play - this
     * exists so self-play tuning can pit the real config against a deliberately
     * crippled one and confirm the scoring actually rewards what it should.
     */
    boolean takeFreeAttacks,
    /** Probability of choosing a random legal action instead of the best one. 0 for the standard bot; a lever for future easier levels. */
    double blunderRate,
    /** Pause before each action so a human can follow along. 0 for self-play and tests. */
    long thinkDelayMillis
) {

    /** The one difficulty shipped today. */
    public static BotConfig standard() {
        return new BotConfig(
            1.0,    // damageWeight
            40.0,   // killBonus
            50.0,   // championKillMultiplier - killing the champion ends the game, so it dwarfs everything
            3.0,    // championTargetWeight
            1.4,    // eliteTargetWeight
            1.0,    // basicTargetWeight
            25.0,   // woundedTargetWeight
            4.0,    // approachWeight
            18.0,   // enterRangeBonus
            6.0,    // exposurePenalty
            12.0,   // genericAbilityValue
            0.5,    // minimumActionScore
            true,   // takeFreeAttacks
            0.0,    // blunderRate
            400L    // thinkDelayMillis
        );
    }

    /** Same weights, no pause - for self-play runs and tests, where the delay is pure waste. */
    public BotConfig withoutThinkDelay() {
        return new BotConfig(damageWeight, killBonus, championKillMultiplier, championTargetWeight,
            eliteTargetWeight, basicTargetWeight, woundedTargetWeight, approachWeight, enterRangeBonus,
            exposurePenalty, genericAbilityValue, minimumActionScore, takeFreeAttacks, blunderRate, 0L);
    }

    public BotConfig withTakeFreeAttacks(boolean value) {
        return new BotConfig(damageWeight, killBonus, championKillMultiplier, championTargetWeight,
            eliteTargetWeight, basicTargetWeight, woundedTargetWeight, approachWeight, enterRangeBonus,
            exposurePenalty, genericAbilityValue, minimumActionScore, value, blunderRate, thinkDelayMillis);
    }

    public BotConfig withBlunderRate(double value) {
        return new BotConfig(damageWeight, killBonus, championKillMultiplier, championTargetWeight,
            eliteTargetWeight, basicTargetWeight, woundedTargetWeight, approachWeight, enterRangeBonus,
            exposurePenalty, genericAbilityValue, minimumActionScore, takeFreeAttacks, value, thinkDelayMillis);
    }
}
