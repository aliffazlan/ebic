package com.walnutt.unit;

import com.walnutt.game.Team;

public class ChampionUnit extends Unit {
    public ChampionUnit(String name, Team team, UnitStats baseStats) {
        super(name, team, UnitType.CHAMPION, baseStats);
    }
}
