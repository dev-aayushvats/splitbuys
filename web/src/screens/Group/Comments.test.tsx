// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act } from 'react'
import { byLabel, click, mount, settle, type, type Mounted } from '../../test/dom'

const { toast, comments, addComment, deleteComment } = vi.hoisted(() => ({ toast: vi.fn(), comments: vi.fn(), addComment: vi.fn(), deleteComment: vi.fn() }))
vi.mock('../../lib/api', () => ({ api: { comments: (...a: unknown[]) => comments(...a), addComment: (...a: unknown[]) => addComment(...a), deleteComment: (...a: unknown[]) => deleteComment(...a) } }))
vi.mock('../../lib/auth', () => ({ useAuth: () => ({ user: { id: 1, email: 'a@x.in', name: 'Asha' } }) }))
vi.mock('../../ui', async (orig) => ({ ...(await orig<typeof import('../../ui')>()), useFeedback: () => ({ toast, confirm: async () => true }) }))

import { Comments } from './Comments'

const a = { id: 1, email: 'a@x.in', name: 'Asha' }
const b = { id: 2, email: 'b@x.in', name: 'Bo' }
let m: Mounted
const note = (id: number, author: typeof a, text: string) => ({ id, author, text, createdAt: '2026-11-11T06:00:00Z' })

beforeEach(() => { toast.mockReset(); comments.mockReset(); addComment.mockReset(); deleteComment.mockReset() })
afterEach(() => m.unmount())

// the query resolves a few ticks after mount: wait until the spinner is gone instead of guessing a count
const open = async () => {
  m = mount(<Comments gid={9} expenseId={3} />)
  for (let i = 0; i < 10 && m.host.querySelector('svg.animate-spin, [class*=animate-spin]'); i++) await settle()
  await settle()
}
const box = () => byLabel(m.host, 'Add a note') as HTMLInputElement

describe('notes on an expense', () => {
  it('lists them oldest first with who wrote each, and says "You" for the viewer', async () => {
    comments.mockResolvedValue([note(1, b, 'why so much?'), note(2, a, 'tasting menu')])
    await open()
    const text = m.host.textContent!
    expect(text.indexOf('why so much?')).toBeLessThan(text.indexOf('tasting menu'))
    expect(text).toContain('Bo')
    expect(text).toContain('You')
  })

  it('invites the first note when there are none', async () => {
    comments.mockResolvedValue([])
    await open()
    expect(m.host.textContent).toContain('No notes yet')
  })

  it('cannot send an empty or whitespace-only note', async () => {
    comments.mockResolvedValue([])
    await open()
    expect((byLabel(m.host, 'Send note') as HTMLButtonElement).disabled).toBe(true)
    type(box(), '   ')
    expect((byLabel(m.host, 'Send note') as HTMLButtonElement).disabled).toBe(true)
  })

  it('sends the trimmed text, clears the box and reloads the list', async () => {
    comments.mockResolvedValueOnce([]).mockResolvedValue([note(5, a, 'hello')])
    addComment.mockResolvedValue(note(5, a, 'hello'))
    await open()
    type(box(), '  hello  ')
    await act(async () => { (byLabel(m.host, 'Send note') as HTMLButtonElement).form!.requestSubmit() }); await settle()
    expect(addComment).toHaveBeenCalledWith(9, 3, 'hello')
    expect(box().value).toBe('')
    expect(m.host.textContent).toContain('hello')
  })

  it('keeps what was typed and shows the reason when sending fails', async () => {
    comments.mockResolvedValue([])
    addComment.mockRejectedValue(new Error('You have reached today’s limit of 100 comments'))
    await open()
    type(box(), 'hello')
    await act(async () => { (byLabel(m.host, 'Send note') as HTMLButtonElement).form!.requestSubmit() }); await settle()
    expect(toast).toHaveBeenCalledWith('You have reached today’s limit of 100 comments')
    expect(box().value).toBe('hello')
  })

  it('offers delete only on the viewer’s own notes', async () => {
    comments.mockResolvedValue([note(1, b, 'theirs'), note(2, a, 'mine')])
    await open()
    expect(m.host.querySelectorAll('[aria-label="Delete note"]')).toHaveLength(1)
    deleteComment.mockResolvedValue(undefined)
    click(byLabel(m.host, 'Delete note')); await settle()
    expect(deleteComment).toHaveBeenCalledWith(9, 2)
  })

  it('says so when the notes cannot be loaded', async () => {
    comments.mockRejectedValue(new Error('offline'))
    await open()
    expect(m.host.textContent).toContain('Could not load notes')
  })
})
