/** A sideways drag must be at least this far, and clearly more sideways than vertical, to count as a swipe. */
export const SWIPE_MIN_PX = 64
export const SWIPE_DOMINANCE = 1.6
/** Drags that begin this close to a screen edge are left to the system (browser back swipe) and to the app's own edge gestures. */
export const SWIPE_EDGE_PX = 24

/**
 * Which tab a finished drag should land on, or null for "stay". Swiping left moves to the next tab, right to the previous;
 * the first and last tab do not wrap.
 */
export function swipeTarget(
  drag: { startX: number; dx: number; dy: number }, index: number, count: number, screenWidth: number,
): number | null {
  const { startX, dx, dy } = drag
  if (startX < SWIPE_EDGE_PX || startX > screenWidth - SWIPE_EDGE_PX) return null
  if (Math.abs(dx) < SWIPE_MIN_PX || Math.abs(dx) < Math.abs(dy) * SWIPE_DOMINANCE) return null
  const next = index + (dx < 0 ? 1 : -1)
  return next >= 0 && next < count ? next : null
}
