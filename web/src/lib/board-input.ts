type PointerPosition = { clientX: number; clientY: number };
type DropEvent = PointerPosition | { changedTouches: ArrayLike<PointerPosition> };

export function dropClientPoint(event: DropEvent) {
  const pointer = 'changedTouches' in event ? event.changedTouches[0] : event;
  return pointer ? { x: pointer.clientX, y: pointer.clientY } : null;
}

export function dropSquare(point: { x: number; y: number }, bounds: {
  left: number; top: number; width: number; height: number;
}, flipped: boolean): string | null {
  const x = point.x - bounds.left;
  const y = point.y - bounds.top;
  if (bounds.width <= 0 || bounds.height <= 0 || x < 0 || y < 0 || x >= bounds.width || y >= bounds.height) return null;
  const col = Math.floor(x / bounds.width * 8);
  const row = Math.floor(y / bounds.height * 8);
  return `${'abcdefgh'[flipped ? 7 - col : col]}${flipped ? row + 1 : 8 - row}`;
}
