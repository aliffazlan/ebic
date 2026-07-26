package com.walnutt.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.walnutt.effect.StatusEffect;
import com.walnutt.game.Team;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class TileTest {

    private Unit newUnit(String name) {
        return new BasicUnit(name, Team.PLAYER_ONE, new UnitStats(1, 1, 1, 10));
    }

    @Test
    void supportsMultipleStackedOccupants() {
        Tile tile = new Tile(new Position(0, 0), TileType.PLAIN);
        Unit a = newUnit("A");
        Unit b = newUnit("B");

        assertFalse(tile.isOccupied());

        tile.addOccupant(a);
        tile.addOccupant(b);

        assertTrue(tile.isOccupied());
        assertEquals(2, tile.getOccupants().size());
        assertEquals(a, tile.getFirstOccupant());

        tile.removeOccupant(a);
        assertEquals(1, tile.getOccupants().size());
        assertEquals(b, tile.getFirstOccupant());
    }

    @Test
    void blockedTileIsNeverWalkable() {
        Tile tile = new Tile(new Position(0, 0), TileType.BLOCKED);
        assertFalse(tile.isWalkable());
    }

    @Test
    void normalOccupantMakesTileUnwalkable_butHiddenOccupantDoesNot() {
        Tile tile = new Tile(new Position(0, 0), TileType.PLAIN);
        assertTrue(tile.isWalkable());

        Unit visible = newUnit("Visible");
        tile.addOccupant(visible);
        assertFalse(tile.isWalkable());
        tile.removeOccupant(visible);

        Unit hidden = newUnit("Hidden");
        hidden.addEffect(new StatusEffect("Cloaked", 5, StatusFlag.HIDDEN));
        tile.addOccupant(hidden);
        assertTrue(tile.isWalkable(), "a hidden occupant shouldn't block others from walking onto its tile");
    }
}
