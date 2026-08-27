// Pure hex-grid math, no PixiJS dependency, so it's trivially unit-testable.
//
// Convention: axial (q, r) coordinates, pointed-top orientation, matching the
// backend's Position.java (implied cube coordinate s = -q - r) and the
// redblobgames.com/grids/hexagons reference. Zero translation is needed
// between server and client coordinates.

export interface AxialCoord {
  q: number;
  r: number;
}

export interface PixelCoord {
  x: number;
  y: number;
}

/** The six axial neighbor offsets, matching backend Position.DIRECTIONS. */
export const AXIAL_DIRECTIONS: readonly AxialCoord[] = [
  { q: 1, r: 0 },
  { q: 1, r: -1 },
  { q: 0, r: -1 },
  { q: -1, r: 0 },
  { q: -1, r: 1 },
  { q: 0, r: 1 },
];

/**
 * Every tile on the board, mirroring the server's GameMap constructor.
 *
 * `rowLimit` is the largest |r| that exists: equal to `radius` for a regular hexagon, and
 * lower when the top and bottom rows have been trimmed off to make an elongated one (r is
 * the vertical axis, so a "row" is a constant r). With a trim in play, being inside the
 * radius no longer means a tile exists - anything drawing the board must go through this
 * rather than re-deriving the bounds from `radius` alone.
 */
export function mapTiles(radius: number, rowLimit: number): AxialCoord[] {
  const limit = Math.min(rowLimit, radius);
  const tiles: AxialCoord[] = [];
  for (let q = -radius; q <= radius; q++) {
    const rMin = Math.max(-radius, -q - radius);
    const rMax = Math.min(radius, -q + radius);
    for (let r = rMin; r <= rMax; r++) {
      if (Math.abs(r) > limit) continue;
      tiles.push({ q, r });
    }
  }
  return tiles;
}

const SQRT3 = Math.sqrt(3);

/** Axial hex coordinate -> pixel center, pointed-top orientation. */
export function axialToPixel(coord: AxialCoord, size: number): PixelCoord {
  const x = size * (SQRT3 * coord.q + (SQRT3 / 2) * coord.r);
  const y = size * (1.5 * coord.r);
  return { x, y };
}

/** Pixel position -> nearest axial hex coordinate (rounded via cube rounding). */
export function pixelToAxial(pixel: PixelCoord, size: number): AxialCoord {
  const q = ((SQRT3 / 3) * pixel.x - (1 / 3) * pixel.y) / size;
  const r = ((2 / 3) * pixel.y) / size;
  return roundAxial({ q, r });
}

/** Rounds fractional cube/axial coordinates to the nearest valid hex. */
export function roundAxial(coord: AxialCoord): AxialCoord {
  const x = coord.q;
  const z = coord.r;
  const y = -x - z;

  let rx = Math.round(x);
  let ry = Math.round(y);
  let rz = Math.round(z);

  const xDiff = Math.abs(rx - x);
  const yDiff = Math.abs(ry - y);
  const zDiff = Math.abs(rz - z);

  if (xDiff > yDiff && xDiff > zDiff) {
    rx = -ry - rz;
  } else if (yDiff > zDiff) {
    ry = -rx - rz;
  } else {
    rz = -rx - ry;
  }

  return { q: rx, r: rz };
}

/** Cube-coordinate hex distance between two axial coordinates. */
export function hexDistance(a: AxialCoord, b: AxialCoord): number {
  const as = -a.q - a.r;
  const bs = -b.q - b.r;
  return Math.max(Math.abs(a.q - b.q), Math.abs(a.r - b.r), Math.abs(as - bs));
}

/**
 * Pixel offset of hex corner `i` (0-5) from a hex's center, pointed-top
 * orientation (corners at -30, 30, 90, 150, 210, 270 degrees).
 */
export function hexCorner(center: PixelCoord, size: number, i: number): PixelCoord {
  const angleDeg = 60 * i - 30;
  const angleRad = (Math.PI / 180) * angleDeg;
  return {
    x: center.x + size * Math.cos(angleRad),
    y: center.y + size * Math.sin(angleRad),
  };
}

/** All 6 corner points of a hex centered at `center`, as a flat [x,y,...] array (for Pixi's Graphics.poly). */
export function hexPolygonPoints(center: PixelCoord, size: number): number[] {
  const points: number[] = [];
  for (let i = 0; i < 6; i++) {
    const c = hexCorner(center, size, i);
    points.push(c.x, c.y);
  }
  return points;
}

export function axialKey(coord: AxialCoord): string {
  return `${coord.q},${coord.r}`;
}

export function axialEquals(a: AxialCoord, b: AxialCoord): boolean {
  return a.q === b.q && a.r === b.r;
}
