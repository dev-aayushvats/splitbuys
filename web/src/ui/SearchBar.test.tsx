// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, useState } from 'react'
import { byLabel, click, mount, type, type Mounted } from '../test/dom'
import { NO_FILTER, type SearchFilter } from '../lib/searchFilter'
import { SearchBar } from './SearchBar'

let m: Mounted
let latest: SearchFilter
function Harness({ start = NO_FILTER }: { start?: SearchFilter }) {
  const [f, setF] = useState(start)
  latest = f
  return <SearchBar value={f} onChange={setF} placeholder="Search expenses" />
}
afterEach(() => m.unmount())

const input = () => m.host.querySelector<HTMLInputElement>('input')!
const chip = (name: string) => [...m.host.querySelectorAll('button')].find((b) => b.textContent?.includes(name))
void vi

describe('SearchBar', () => {
  it('shows category chips only once the box is focused or a filter is on', () => {
    m = mount(<Harness />)
    expect(chip('Food')).toBeUndefined()
    act(() => { input().focus() })
    expect(chip('Food')).toBeDefined()
  })

  it('reports what is typed', () => {
    m = mount(<Harness />)
    type(input(), 'taxi')
    expect(latest).toEqual({ text: 'taxi', category: null })
  })

  it('a chip turns a category on, and tapping it again turns it off; text is kept', () => {
    m = mount(<Harness start={{ text: 'taxi', category: null }} />)
    click(chip('Travel')!)
    expect(latest).toEqual({ text: 'taxi', category: 'TRAVEL' })
    expect(chip('Travel')!.getAttribute('aria-pressed')).toBe('true')
    click(chip('Travel')!)
    expect(latest).toEqual({ text: 'taxi', category: null })
  })

  it('Clear empties everything and is offered only while filtering', () => {
    m = mount(<Harness />)
    expect(byLabel(m.host, 'Clear search')).toBeNull()
    type(input(), 'taxi')
    click(chip('Travel')!)
    click(byLabel(m.host, 'Clear search'))
    expect(latest).toEqual(NO_FILTER)
    expect(byLabel(m.host, 'Clear search')).toBeNull()
  })

  it('limits the text to what the server accepts', () => {
    m = mount(<Harness />)
    expect(input().maxLength).toBe(100)
  })
})
