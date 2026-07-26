package com.walnutt.map;

/**
 * Axial coordinate (q, r) on a hexagonal grid; the implied cube coordinate is
 * s = -q - r. Six neighbor directions (DIRECTIONS) and the cube-coordinate
 * distance formula give consistent, correct adjacency/range/ring queries -
 * see https://www.redblobgames.com/grids/hexagons/ for the reference this follows.
 */
public final class Position {
    public static final Position[] DIRECTIONS = {
        new Position(1, 0), new Position(1, -1), new Position(0, -1),
        new Position(-1, 0), new Position(-1, 1), new Position(0, 1)
    };

    private final int q;
    private final int r;

    public Position(int q, int r) {
        this.q = q;
        this.r = r;
    }

    public int getQ() {
        return q;
    }

    public int getR() {
        return r;
    }

    public int getS() {
        return -q - r;
    }

    public Position plus(Position other) {
        return new Position(q + other.q, r + other.r);
    }

    public int hexDistance(Position other) {
        return (Math.abs(q - other.q) + Math.abs(r - other.r) + Math.abs(getS() - other.getS())) / 2;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Position other)) return false;
        return q == other.q && r == other.r;
    }

    @Override
    public int hashCode() {
        return 31 * q + r;
    }

    @Override
    public String toString() {
        return "(" + q + ", " + r + ")";
    }
}
