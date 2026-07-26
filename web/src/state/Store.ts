// Tiny pub-sub store. No React/Vue/Redux — a render loop and DOM UI both
// subscribe directly to plain instances of this class.

export type Listener<T> = (state: T) => void;
export type Unsubscribe = () => void;

export class Store<T> {
  private listeners = new Set<Listener<T>>();
  private state: T;

  constructor(state: T) {
    this.state = state;
  }

  getState(): T {
    return this.state;
  }

  setState(patch: Partial<T>): void {
    this.state = { ...this.state, ...patch };
    this.emit();
  }

  /** Replace the whole state (useful when a patch would be misleading, e.g. resetting). */
  replaceState(state: T): void {
    this.state = state;
    this.emit();
  }

  subscribe(listener: Listener<T>): Unsubscribe {
    this.listeners.add(listener);
    listener(this.state);
    return () => {
      this.listeners.delete(listener);
    };
  }

  private emit(): void {
    for (const listener of this.listeners) {
      listener(this.state);
    }
  }
}
