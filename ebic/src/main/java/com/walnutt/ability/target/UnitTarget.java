package com.walnutt.ability.target;

import com.walnutt.unit.Unit;

public class UnitTarget implements Target {
    private final Unit unit;

    public UnitTarget(Unit unit) {
        this.unit = unit;
    }

    public Unit getUnit() {
        return this.unit;
    }
}
