package com.walnutt.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitType;

public class Player {
    private final String name;
    private final Team team;
    private final List<Unit> units = new ArrayList<>();

    public Player(String name, Team team) {
        this.name = name;
        this.team = team;
    }

    public String getName() {
        return name;
    }

    public Team getTeam() {
        return team;
    }

    public void addUnit(Unit unit) {
        units.add(unit);
    }

    /** For temporary roster additions (Psychic Projection's clone) once their effect expires. */
    public void removeUnit(Unit unit) {
        units.remove(unit);
    }

    public List<Unit> getUnits() {
        return units;
    }

    public List<Unit> getLivingUnits() {
        return units.stream().filter(u -> !u.isDead()).toList();
    }

    public Optional<Unit> getChampion() {
        return units.stream().filter(u -> u.getUnitType() == UnitType.CHAMPION).findFirst();
    }

    public boolean isDefeated() {
        return getChampion().map(Unit::isDead).orElse(true);
    }
}
