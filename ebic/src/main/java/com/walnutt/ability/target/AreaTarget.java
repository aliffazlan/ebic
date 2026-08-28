package com.walnutt.ability.target;

import java.util.List;

import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.map.Tile;

public class AreaTarget implements Target {
    private final Position center;
    private final int radius;

    public AreaTarget(Position center, int radius) {
        this.center = center;
        this.radius = radius;
    }

    public Position getCenter() {
        return center;
    }

    public int getRadius() {
        return radius;
    }

    public List<Tile> getTilesInRadius(GameMap map) {
        return map.getTilesInRadius(center, radius);
    }
}
