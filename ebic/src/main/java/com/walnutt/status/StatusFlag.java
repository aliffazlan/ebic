package com.walnutt.status;

public enum StatusFlag {
    STUNNED,
    SILENCED,
    DISARMED,
    ROOTED,
    INVULNERABLE,
    HIDDEN,
    FROZEN,
    IMMUNE_TO_HEALING,
    UNTARGETABLE,
    TAUNTED,
    COOLDOWNS_PAUSED,
    /** Locked into a forced duel (Valor) - blocks all three action kinds, same as STUNNED. */
    DUELING,
    /**
     * Under an enemy's time-dilation aura (Chronos's Dilation): doesn't block any
     * action itself - Unit.endTurn interprets it to make BUFFs tick faster and
     * DEBUFFs tick slower (see Effect.tick(TickRate)).
     */
    TIME_DILATED;

    public boolean blocksMovement() {
        return this == STUNNED || this == ROOTED || this == FROZEN || this == DUELING;
    }

    public boolean blocksAttack() {
        return this == STUNNED || this == DISARMED || this == FROZEN || this == DUELING;
    }

    public boolean blocksAbility() {
        return this == STUNNED || this == SILENCED || this == FROZEN || this == DUELING;
    }
}
