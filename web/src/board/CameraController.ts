// Input glue for the board camera: wheel zoom, drag panning, keyboard panning.
// All the arithmetic lives in Camera.ts (pure, unit-tested); this file is only
// listeners, gesture state and the "was that a click or a drag?" decision.

import {
  type Bounds,
  type Camera,
  clampCamera,
  panBy,
  type Viewport,
  wheelZoomFactor,
  zoomAt,
} from "./Camera";

/**
 * How far the pointer must travel before a press counts as a pan rather than a
 * click. Comfortably above the jitter of a deliberate click, comfortably below
 * anything a user would consider a drag.
 */
const DRAG_THRESHOLD = 5;
/** Screen pixels moved per arrow/WASD keypress (auto-repeat does the rest). */
const KEY_PAN_STEP = 60;

export interface CameraControllerOptions {
  /** The renderer's logical size (Pixi's app.screen), not the canvas's CSS size. */
  getViewport(): Viewport;
  getContentBounds(): Bounds;
  /** Applies the camera - Board writes it to root.position/root.scale. */
  onChange(camera: Camera): void;
}

interface DragState {
  pointerId: number;
  lastX: number;
  lastY: number;
  totalX: number;
  totalY: number;
  /** True once DRAG_THRESHOLD has been crossed and this is really a pan. */
  panning: boolean;
}

export class CameraController {
  private canvas: HTMLCanvasElement;
  private options: CameraControllerOptions;
  private camera: Camera = { x: 0, y: 0, zoom: 1 };
  private drag: DragState | null = null;
  // Set the moment a press becomes a pan, and cleared on the *next*
  // pointerdown rather than on pointerup. Pixi's EventSystem listens on this
  // same canvas, so the ordering between our pointerup handler and the
  // pointertap it synthesises isn't guaranteed - but a pointerdown always
  // precedes the next pointertap, which makes clearing there unambiguous.
  private suppressTap = false;

  constructor(canvas: HTMLCanvasElement, options: CameraControllerOptions) {
    this.canvas = canvas;
    this.options = options;
    canvas.addEventListener("wheel", this.onWheel, { passive: false });
    canvas.addEventListener("pointerdown", this.onPointerDown);
    canvas.addEventListener("pointermove", this.onPointerMove);
    canvas.addEventListener("pointerup", this.onPointerUp);
    canvas.addEventListener("pointercancel", this.onPointerUp);
    canvas.addEventListener("auxclick", this.onAuxClick);
    window.addEventListener("keydown", this.onKeyDown);
  }

  destroy(): void {
    this.canvas.removeEventListener("wheel", this.onWheel);
    this.canvas.removeEventListener("pointerdown", this.onPointerDown);
    this.canvas.removeEventListener("pointermove", this.onPointerMove);
    this.canvas.removeEventListener("pointerup", this.onPointerUp);
    this.canvas.removeEventListener("pointercancel", this.onPointerUp);
    this.canvas.removeEventListener("auxclick", this.onAuxClick);
    window.removeEventListener("keydown", this.onKeyDown);
  }

  /**
   * True when the tap Pixi is about to deliver is the tail end of a pan and
   * should not be treated as a board action. Every pointertap handler on the
   * board has to consult this.
   */
  shouldSuppressTap(): boolean {
    return this.suppressTap;
  }

  /** Back to the default view: board centred, unzoomed. */
  reset(): void {
    const viewport = this.options.getViewport();
    this.apply({ x: viewport.width / 2, y: viewport.height / 2, zoom: 1 });
  }

  /**
   * Re-clamps the current camera without moving it otherwise - for a viewport
   * resize or a change of board size. Deliberately not a reset: resizing the
   * window shouldn't throw away where the user was looking.
   */
  reclamp(): void {
    this.apply(this.camera);
  }

  private apply(camera: Camera): void {
    this.camera = clampCamera(camera, this.options.getContentBounds(), this.options.getViewport());
    this.options.onChange(this.camera);
  }

  /**
   * CSS pixels -> renderer units. The canvas is sized by Pixi's `resizeTo`, so
   * today these are 1:1, but reading the ratio rather than assuming it keeps
   * the zoom anchor and the drag distance honest if a `resolution`/`autoDensity`
   * is ever set on the Application.
   */
  private stageScale(): { sx: number; sy: number } {
    const rect = this.canvas.getBoundingClientRect();
    const viewport = this.options.getViewport();
    return {
      sx: rect.width > 0 ? viewport.width / rect.width : 1,
      sy: rect.height > 0 ? viewport.height / rect.height : 1,
    };
  }

  private onWheel = (e: WheelEvent): void => {
    // Without this the page (and, in a browser without overflow:hidden, the
    // whole document) scrolls instead of the board zooming.
    e.preventDefault();
    const rect = this.canvas.getBoundingClientRect();
    const { sx, sy } = this.stageScale();
    const anchor = { x: (e.clientX - rect.left) * sx, y: (e.clientY - rect.top) * sy };
    this.apply(zoomAt(this.camera, anchor, wheelZoomFactor(e.deltaY, e.deltaMode)));
  };

  private onPointerDown = (e: PointerEvent): void => {
    // A fresh press always starts with a clean slate - see suppressTap's note.
    this.suppressTap = false;
    if (e.button !== 0 && e.button !== 1) return;
    // Middle-click otherwise starts autoscroll on Windows.
    if (e.button === 1) e.preventDefault();
    this.drag = {
      pointerId: e.pointerId,
      lastX: e.clientX,
      lastY: e.clientY,
      totalX: 0,
      totalY: 0,
      panning: false,
    };
    // Capture keeps the gesture alive (and endable) when the pointer leaves the
    // window mid-drag. Events still target the canvas, so Pixi keeps seeing them.
    this.canvas.setPointerCapture(e.pointerId);
  };

  private onPointerMove = (e: PointerEvent): void => {
    const drag = this.drag;
    if (!drag || e.pointerId !== drag.pointerId) return;

    const { sx, sy } = this.stageScale();
    const dx = (e.clientX - drag.lastX) * sx;
    const dy = (e.clientY - drag.lastY) * sy;
    drag.lastX = e.clientX;
    drag.lastY = e.clientY;
    drag.totalX += dx;
    drag.totalY += dy;

    if (!drag.panning) {
      if (Math.hypot(drag.totalX, drag.totalY) < DRAG_THRESHOLD) return;
      drag.panning = true;
      this.suppressTap = true;
      this.canvas.style.cursor = "grabbing";
      // Apply everything accumulated so far, so the board picks up from where
      // the pointer actually is rather than lagging by the threshold.
      this.apply(panBy(this.camera, drag.totalX, drag.totalY));
      return;
    }
    this.apply(panBy(this.camera, dx, dy));
  };

  private onPointerUp = (e: PointerEvent): void => {
    if (!this.drag || e.pointerId !== this.drag.pointerId) return;
    if (this.canvas.hasPointerCapture(e.pointerId)) {
      this.canvas.releasePointerCapture(e.pointerId);
    }
    this.drag = null;
    // Blank rather than a fixed value, so Pixi's own per-object cursor
    // management (tiles and units set "pointer") takes over again.
    this.canvas.style.cursor = "";
  };

  private onAuxClick = (e: MouseEvent): void => {
    if (e.button === 1) e.preventDefault();
  };

  private onKeyDown = (e: KeyboardEvent): void => {
    if (e.ctrlKey || e.metaKey || e.altKey) return;
    if (isTextEntry(document.activeElement)) return;

    switch (e.key) {
      // Arrow/WASD pan the *view*: pressing left reveals what's to the left,
      // which means moving the board's contents right.
      case "ArrowLeft": case "a": case "A":
        this.apply(panBy(this.camera, KEY_PAN_STEP, 0));
        break;
      case "ArrowRight": case "d": case "D":
        this.apply(panBy(this.camera, -KEY_PAN_STEP, 0));
        break;
      case "ArrowUp": case "w": case "W":
        this.apply(panBy(this.camera, 0, KEY_PAN_STEP));
        break;
      case "ArrowDown": case "s": case "S":
        this.apply(panBy(this.camera, 0, -KEY_PAN_STEP));
        break;
      case "Home": case "0":
        this.reset();
        break;
      default:
        return;
    }
    e.preventDefault();
  };
}

/** Keeps the auth/lobby text fields (and any future one) typable while a board exists. */
function isTextEntry(element: Element | null): boolean {
  if (!element) return false;
  const tag = element.tagName;
  return tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" ||
    (element as HTMLElement).isContentEditable === true;
}
