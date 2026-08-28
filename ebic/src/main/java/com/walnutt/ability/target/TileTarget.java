package com.walnutt.ability.target;

import com.walnutt.map.Tile;

public class TileTarget implements Target {
    private final Tile tile;

    public TileTarget(Tile tile) {
        this.tile = tile;
    }

    public Tile getTile() {
        return this.tile;
    }
}
