package com.walnutt.unit;

import com.walnutt.game.Team;

public class BasicUnit extends Unit {
    public BasicUnit(String name, Team team, UnitStats baseStats) {
        super(name, team, UnitType.BASIC, baseStats);
    }
}
