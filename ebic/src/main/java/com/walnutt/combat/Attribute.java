package com.walnutt.combat;

public enum Attribute {
    STRENGTH,
    AGILITY,
    INTELLIGENCE;

    /** Returns true if this attribute beats the other in the rock-paper-scissors relationship. */
    public boolean beats(Attribute other) {
        return switch (this) {
            case STRENGTH -> other == INTELLIGENCE;
            case INTELLIGENCE -> other == AGILITY;
            case AGILITY -> other == STRENGTH;
        };
    }
}
