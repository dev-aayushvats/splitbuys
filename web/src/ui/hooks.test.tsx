// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, useState } from 'react'
import { mount, type Mounted } from '../test/dom'
import { useDebounced } from './hooks'

let seen: string
let setValue: (v: string) => void
function Probe() { const [v, set] = useState('a'); setValue = set; seen = useDebounced(v, 300); return null }
let m: Mounted

beforeEach(() => { vi.useFakeTimers(); m = mount(<Probe />) })
afterEach(() => { m.unmount(); vi.useRealTimers() })

const set = (v: string) => act(() => setValue(v))

describe('useDebounced', () => {
  it('starts with the value it is given', () => { expect(seen).toBe('a') })
  it('follows only after the value has stopped changing', () => {
    set('ab'); act(() => { vi.advanceTimersByTime(200) })
    expect(seen).toBe('a')
    set('abc'); act(() => { vi.advanceTimersByTime(200) })
    expect(seen).toBe('a') // the second change restarted the wait
    act(() => { vi.advanceTimersByTime(150) })
    expect(seen).toBe('abc')
  })
})
