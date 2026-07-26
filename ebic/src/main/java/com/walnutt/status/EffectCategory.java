package com.walnutt.status;

/**
 * Classifies what an Effect is FROM THE OWNER'S PERSPECTIVE - used by dispel
 * (Thaddeus's Holy Shield clears DEBUFFs) and by tick-rate modulation (Chronos's
 * Dilation makes BUFFs tick faster and DEBUFFs tick slower). Effects default to
 * NEUTRAL (untouched by either mechanic) unless a subclass says otherwise.
 */
public enum EffectCategory {
    BUFF,
    DEBUFF,
    NEUTRAL
}
