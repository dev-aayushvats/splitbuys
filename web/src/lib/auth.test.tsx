// @vitest-environment jsdom
import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true

const { ApiErrorMock, me } = vi.hoisted(() => ({
  ApiErrorMock: class extends Error {
    status?: number
    constructor(message: string, status?: number) { super(message); this.status = status }
  },
  me: vi.fn(),
}))
vi.mock('./api', () => ({
  ApiError: ApiErrorMock,
  api: { me: () => me() },
  getSavedToken: () => localStorage.getItem('token'),
  setToken: (t: string | null) => { t ? localStorage.setItem('token', t) : localStorage.removeItem('token') },
  setUnauthorizedHandler: () => {},
}))
vi.mock('./push', () => ({ forgetThisDevice: () => {} }))

import { AuthProvider, useAuth } from './auth'

const profile = { id: 1, email: 'a@x.in', name: 'Asha', upiId: null }
let host: HTMLDivElement
let root: Root
let seen: { user: unknown; ready: boolean }

function Probe() { const a = useAuth(); seen = { user: a.user, ready: a.ready }; return null }
const mount = async () => { await act(async () => { root.render(<AuthProvider><Probe /></AuthProvider>) }) }

beforeEach(() => { localStorage.clear(); me.mockReset(); host = document.createElement('div'); root = createRoot(host) })
afterEach(() => act(() => root.unmount()))

describe('opening the app with a saved sign-in', () => {
  beforeEach(() => { localStorage.setItem('token', 't'); localStorage.setItem('profile', JSON.stringify(profile)) })

  it('stays signed in when there is no connection', async () => {
    me.mockRejectedValue(new ApiErrorMock('Cannot reach the server.')) // no status: nothing answered
    await mount()
    expect(seen).toEqual({ user: profile, ready: true })
    expect(localStorage.getItem('token')).toBe('t')
  })
  it('stays signed in when the server is having a bad moment', async () => {
    me.mockRejectedValue(new ApiErrorMock('Something went wrong', 503))
    await mount()
    expect(seen.user).toEqual(profile)
  })
  it('signs out when the server says the token is no good', async () => {
    me.mockRejectedValue(new ApiErrorMock('Unauthorized', 401))
    await mount()
    expect(seen.user).toBeNull()
    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('profile')).toBeNull()
  })
  it('shows the saved profile at once, then the fresh one when the server answers', async () => {
    me.mockResolvedValue({ ...profile, name: 'Asha V' })
    await mount()
    expect(seen.user).toEqual({ ...profile, name: 'Asha V' })
    expect(JSON.parse(localStorage.getItem('profile')!).name).toBe('Asha V')
  })
})

describe('without a saved sign-in', () => {
  it('shows the sign-in screen, and a stale saved profile alone signs nobody in', async () => {
    localStorage.setItem('profile', JSON.stringify(profile))
    await mount()
    expect(seen).toEqual({ user: null, ready: true })
    expect(me).not.toHaveBeenCalled()
  })
  it('first launch with a connection and a token but no saved profile waits for the server', async () => {
    localStorage.setItem('token', 't')
    me.mockRejectedValue(new ApiErrorMock('Cannot reach the server.'))
    await mount()
    expect(seen).toEqual({ user: null, ready: true }) // nothing to show offline yet; the token is kept for the next launch
    expect(localStorage.getItem('token')).toBe('t')
  })
})
