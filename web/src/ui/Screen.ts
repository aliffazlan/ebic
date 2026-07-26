/** Common interface every top-level screen implements (auth / lobby / match). */
export interface Screen {
  mount(): void;
  unmount(): void;
}
