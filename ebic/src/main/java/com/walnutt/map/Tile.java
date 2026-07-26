package com.walnutt.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.walnutt.status.StatusFlag;
import com.walnutt.unit.Unit;

public class Tile {
    private final Position position;
    private final TileType type;
    private final List<Unit> occupants = new ArrayList<>();

    public Tile(Position position, TileType type) {
        this.position = position;
        this.type = type;
    }

    public Position getPosition() {
        return position;
    }

    public TileType getType() {
        return type;
    }

    /** Normally at most one unit; can hold more when an ability forces stacking (e.g. Cloak and Dagger). */
    public List<Unit> getOccupants() {
        return Collections.unmodifiableList(occupants);
    }

    public void addOccupant(Unit unit) {
        occupants.add(unit);
    }

    public void removeOccupant(Unit unit) {
        occupants.remove(unit);
    }

    public boolean isOccupied() {
        return !occupants.isEmpty();
    }

    /** Convenience for the common single-occupant case (rendering, quick lookups). */
    public Unit getFirstOccupant() {
        return occupants.isEmpty() ? null : occupants.get(0);
    }

    /**
     * True for ordinary movement purposes: not blocked, and no occupant that both
     * occupies its tile (Unit.occupiesTile() - false for structures like Pylons)
     * AND isn't HIDDEN. A hidden unit doesn't stop others from walking onto its
     * tile - they don't know it's there, which is what lets Cloak and Dagger
     * ambush them; a non-tile-occupying unit never blocks at all, hidden or not.
     */
    public boolean isWalkable() {
        if (type == TileType.BLOCKED) {
            return false;
        }
        for (Unit occupant : occupants) {
            if (occupant.occupiesTile() && !occupant.hasStatus(StatusFlag.HIDDEN)) {
                return false;
            }
        }
        return true;
    }
}
