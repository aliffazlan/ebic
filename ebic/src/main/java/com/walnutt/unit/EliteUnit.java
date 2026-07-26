package com.walnutt.unit;

import com.walnutt.game.Team;

public class EliteUnit extends Unit {
    public EliteUnit(String name, Team team, UnitStats baseStats) {
        super(name, team, UnitType.ELITE, baseStats);
    }
}
