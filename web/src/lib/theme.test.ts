// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from 'vitest'
import { applyTheme, isChoice, resolve, savedChoice, setThemeChoice } from './theme'

describe('theme', () => {
  beforeEach(() => { localStorage.clear(); document.documentElement.removeAttribute('data-theme') })

  it('follows the device only when the choice is "system"', () => {
    expect(resolve('system', true)).toBe('dark')
    expect(resolve('system', false)).toBe('light')
    expect(resolve('light', true)).toBe('light')
    expect(resolve('dark', false)).toBe('dark')
  })
  it('ignores a stored value that is not a choice', () => {
    localStorage.setItem('theme', 'purple')
    expect(savedChoice()).toBe('system')
    expect(isChoice('light')).toBe(true)
    expect(isChoice('purple')).toBe(false)
  })
  it('stores an explicit choice, forgets "system", and puts the result on the page', () => {
    setThemeChoice('light')
    expect(localStorage.getItem('theme')).toBe('light')
    expect(document.documentElement.dataset.theme).toBe('light')
    setThemeChoice('dark')
    expect(document.documentElement.dataset.theme).toBe('dark')
    setThemeChoice('system')
    expect(localStorage.getItem('theme')).toBeNull()
  })
  it('keeps the browser chrome colour in step with the page', () => {
    document.head.innerHTML = '<meta name="theme-color" content="#000"><meta name="color-scheme" content="dark">'
    applyTheme('light')
    expect(document.querySelector('meta[name="theme-color"]')?.getAttribute('content')).toBe('#eceef3')
    applyTheme('dark')
    expect(document.querySelector('meta[name="theme-color"]')?.getAttribute('content')).toBe('#09090a')
  })
})
