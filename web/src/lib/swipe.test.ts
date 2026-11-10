import { describe, expect, it } from 'vitest'
import { swipeTarget } from './swipe'

const W = 400
const drag = (dx: number, dy = 0, startX = 200) => ({ startX, dx, dy })

describe('swipeTarget', () => {
  it('left goes to the next tab, right to the previous', () => {
    expect(swipeTarget(drag(-120), 1, 4, W)).toBe(2)
    expect(swipeTarget(drag(120), 1, 4, W)).toBe(0)
  })

  it('does not wrap at either end', () => {
    expect(swipeTarget(drag(120), 0, 4, W)).toBeNull()
    expect(swipeTarget(drag(-120), 3, 4, W)).toBeNull()
  })

  it('ignores short drags', () => {
    expect(swipeTarget(drag(-63), 1, 4, W)).toBeNull()
    expect(swipeTarget(drag(-64), 1, 4, W)).toBe(2)
  })

  it('ignores drags that are not clearly sideways, so scrolling and pull-to-refresh are safe', () => {
    expect(swipeTarget(drag(-100, 90), 1, 4, W)).toBeNull()   // diagonal
    expect(swipeTarget(drag(-100, 200), 1, 4, W)).toBeNull()  // mostly vertical
    expect(swipeTarget(drag(-100, 60), 1, 4, W)).toBe(2)      // 100 >= 60 * 1.6
  })

  it('leaves drags that start at a screen edge to the system', () => {
    expect(swipeTarget(drag(120, 0, 10), 1, 4, W)).toBeNull()
    expect(swipeTarget(drag(-120, 0, W - 10), 1, 4, W)).toBeNull()
    expect(swipeTarget(drag(120, 0, 24), 1, 4, W)).toBe(0)
  })
})
