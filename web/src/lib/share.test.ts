// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { shareText } from './share'

const setNav = (o: { share?: unknown; clipboard?: unknown }) => {
  Object.defineProperty(navigator, 'share', { value: o.share, configurable: true })
  Object.defineProperty(navigator, 'clipboard', { value: o.clipboard, configurable: true })
}
afterEach(() => setNav({}))

describe('shareText', () => {
  it('hands the text to the share sheet when there is one', async () => {
    const share = vi.fn().mockResolvedValue(undefined)
    setNav({ share })
    expect(await shareText('hello')).toBe('shared')
    expect(share).toHaveBeenCalledWith({ text: 'hello' })
  })
  it('treats closing the share sheet as cancelling, not as an error, and does not copy behind the user’s back', async () => {
    const writeText = vi.fn()
    setNav({ share: vi.fn().mockRejectedValue(new DOMException('closed', 'AbortError')), clipboard: { writeText } })
    expect(await shareText('hello')).toBe('cancelled')
    expect(writeText).not.toHaveBeenCalled()
  })
  it('copies when sharing is missing or breaks for another reason', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    setNav({ clipboard: { writeText } })
    expect(await shareText('hello')).toBe('copied')
    setNav({ share: vi.fn().mockRejectedValue(new Error('boom')), clipboard: { writeText } })
    expect(await shareText('again')).toBe('copied')
    expect(writeText).toHaveBeenLastCalledWith('again')
  })
  it('says so when nothing worked', async () => {
    setNav({ clipboard: { writeText: vi.fn().mockRejectedValue(new Error('denied')) } })
    expect(await shareText('x')).toBe('failed')
  })
})
