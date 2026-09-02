// Pure camera math for the board, no PixiJS dependency, so it's trivially
// unit-testable under vitest's node environment (everything that touches
// Application/Container isn't). The DOM/Pixi glue that drives this lives in
// CameraController.ts.
//
// Convention: the camera maps board-local ("world") coordinates to screen
// coordinates as `screen = world * zoom + (x, y)` - exactly what Pixi applies
// when Board.root gets `position = (x, y)` and `scale = zoom`, so applying a
// camera is a single container write and every layer, tween and particle
// underneath keeps working in board-local coordinates.

import { type AxialCoord, axialToPixel, mapTiles, type PixelCoord } from "../hex/HexMath";

export interface Camera {
  /** Screen position of world origin (hex 0,0). */
  x: number;
  y: number;
  zoom: number;
}

export interface Viewport {
  width: number;
  height: number;
}

export interface Bounds {
  minX: number;
  minY: number;
  maxX: number;
  maxY: number;
}

/** Far enough out that the whole board plus its graveyard columns fits a small viewport. */
export const MIN_ZOOM = 0.4;
/** ~85px hexes - close enough to read a crowded tile without the board turning to mush. */
export const MAX_ZOOM = 2.5;
/** How much of the board must stay on screen: you can never lose it and have to hunt. */
export const EDGE_MARGIN = 120;

// Wheel deltas are wildly inconsistent across devices and platforms, so they're
// first normalised to approximate pixels and then fed through an exponential -
// scaling multiplicatively is what makes a notch feel like the same amount of
// zoom at every level. The per-event clamp stops one violent trackpad flick
// (or a browser that reports a whole page of delta) from crossing the entire
// zoom range in a single frame.
const ZOOM_SENSITIVITY = 0.0015;
const LINE_HEIGHT_PX = 16;
const PAGE_HEIGHT_PX = 400;
const MAX_FACTOR_PER_EVENT = 1.5;

export function clampZoom(zoom: number): number {
  return Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, zoom));
}

/**
 * A wheel event's multiplicative zoom factor, normalised across `deltaMode`
 * 0 (pixels - trackpads and most mice), 1 (lines) and 2 (pages). Scrolling up
 * (negative deltaY, the platform convention for "away from you") zooms in, so
 * the factor is > 1.
 */
export function wheelZoomFactor(deltaY: number, deltaMode: number): number {
  const scale = deltaMode === 1 ? LINE_HEIGHT_PX : deltaMode === 2 ? PAGE_HEIGHT_PX : 1;
  const factor = Math.exp(-deltaY * scale * ZOOM_SENSITIVITY);
  return Math.min(MAX_FACTOR_PER_EVENT, Math.max(1 / MAX_FACTOR_PER_EVENT, factor));
}

/**
 * Zooms about a screen point, keeping the world point currently under it in
 * exactly the same place. The zoom is clamped *before* the position is
 * recomputed, which is what keeps the anchor exact when a scroll runs into
 * MIN_ZOOM/MAX_ZOOM instead of drifting the board sideways at the limit.
 */
export function zoomAt(camera: Camera, screen: PixelCoord, factor: number): Camera {
  const zoom = clampZoom(camera.zoom * factor);
  const world = screenToWorld(camera, screen);
  return {
    x: screen.x - world.x * zoom,
    y: screen.y - world.y * zoom,
    zoom,
  };
}

/** Moves the view by a screen-space delta (camera x/y are screen-space, so no zoom scaling). */
export function panBy(camera: Camera, dx: number, dy: number): Camera {
  return { x: camera.x + dx, y: camera.y + dy, zoom: camera.zoom };
}

export function screenToWorld(camera: Camera, screen: PixelCoord): PixelCoord {
  return { x: (screen.x - camera.x) / camera.zoom, y: (screen.y - camera.y) / camera.zoom };
}

export function worldToScreen(camera: Camera, world: PixelCoord): PixelCoord {
  return { x: world.x * camera.zoom + camera.x, y: world.y * camera.zoom + camera.y };
}

/**
 * Keeps at least EDGE_MARGIN pixels of the board overlapping the viewport on
 * each axis, so the board can never be panned entirely out of sight. When the
 * board is small enough (or zoomed out far enough) that the two constraints
 * cross, the axis is simply centred instead - there is no valid range to pick
 * from, and centring is what the user wants anyway.
 */
export function clampCamera(camera: Camera, content: Bounds, viewport: Viewport): Camera {
  return {
    zoom: camera.zoom,
    x: clampAxis(camera.x, content.minX, content.maxX, camera.zoom, viewport.width),
    y: clampAxis(camera.y, content.minY, content.maxY, camera.zoom, viewport.height),
  };
}

function clampAxis(pos: number, min: number, max: number, zoom: number, extent: number): number {
  // The content's far edge must sit at least EDGE_MARGIN inside the viewport's
  // near edge, and vice versa.
  const low = EDGE_MARGIN - max * zoom;
  const high = extent - EDGE_MARGIN - min * zoom;
  if (low > high) return (extent - (min + max) * zoom) / 2;
  return Math.min(high, Math.max(low, pos));
}

/**
 * World-space extent of everything the board draws: the hex grid (derived from
 * mapTiles rather than from `radius` alone, since the board is row-trimmed and
 * being inside the radius doesn't mean a tile exists) padded by one hex, unioned
 * with the graveyard columns.
 *
 * The graveyard constants mirror Board.placeInGraveyard exactly, including its
 * `* 1.5` x factor - which doesn't match axialToPixel's SQRT3 x-spacing, so the
 * corpse columns actually land inside the grid's horizontal extent. That's the
 * board's business, not this function's: the union is correct either way.
 */
export function contentBounds(mapRadius: number, mapRowLimit: number, hexSize: number): Bounds {
  const tiles: AxialCoord[] = mapTiles(mapRadius, mapRowLimit);
  const bounds: Bounds = { minX: 0, minY: 0, maxX: 0, maxY: 0 };
  for (const tile of tiles) {
    const p = axialToPixel(tile, hexSize);
    bounds.minX = Math.min(bounds.minX, p.x - hexSize);
    bounds.maxX = Math.max(bounds.maxX, p.x + hexSize);
    bounds.minY = Math.min(bounds.minY, p.y - hexSize);
    bounds.maxY = Math.max(bounds.maxY, p.y + hexSize);
  }

  const graveyardEdge = Math.abs((mapRadius + 1.6) * hexSize * 1.5);
  const graveyardTop = -Math.abs((mapRowLimit + 0.5) * hexSize * 1.732);
  bounds.minX = Math.min(bounds.minX, -graveyardEdge - hexSize);
  bounds.maxX = Math.max(bounds.maxX, graveyardEdge + hexSize);
  bounds.minY = Math.min(bounds.minY, graveyardTop - hexSize);
  bounds.maxY = Math.max(bounds.maxY, -graveyardTop + hexSize);
  return bounds;
}
