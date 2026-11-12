export type ThemeChoice = 'system' | 'light' | 'dark'
export type Resolved = 'light' | 'dark'

export const THEME_KEY = 'theme'
/** The browser chrome colour (address bar, status bar) per theme: it must match the page, or a stripe shows. */
export const CHROME: Record<Resolved, string> = { dark: '#09090a', light: '#eceef3' }

export const isChoice = (v: unknown): v is ThemeChoice => v === 'system' || v === 'light' || v === 'dark'

/** What to show: the person's choice, or the device's setting when they chose "system". */
export const resolve = (choice: ThemeChoice, prefersDark: boolean): Resolved => (choice === 'system' ? (prefersDark ? 'dark' : 'light') : choice)

export function savedChoice(): ThemeChoice {
  try { const v = localStorage.getItem(THEME_KEY); return isChoice(v) ? v : 'system' } catch { return 'system' }
}

const dark = () => typeof matchMedia === 'function' && matchMedia('(prefers-color-scheme: dark)').matches

/** Puts the theme on the page. Safe to call any time; the same logic runs inline in index.html before first paint. */
export function applyTheme(choice: ThemeChoice = savedChoice()): Resolved {
  const r = resolve(choice, dark())
  const root = document.documentElement
  root.dataset.theme = r
  root.style.colorScheme = r
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', CHROME[r])
  document.querySelector('meta[name="color-scheme"]')?.setAttribute('content', r)
  // iOS home-screen app: white status-bar text needs a dark page, dark text a light one (iOS may only re-read this on the next launch)
  document.querySelector('meta[name="apple-mobile-web-app-status-bar-style"]')?.setAttribute('content', r === 'dark' ? 'black-translucent' : 'default')
  return r
}

export function setThemeChoice(choice: ThemeChoice) {
  try { choice === 'system' ? localStorage.removeItem(THEME_KEY) : localStorage.setItem(THEME_KEY, choice) } catch { /* private mode: it still applies until the page closes */ }
  applyTheme(choice)
}

/** Apply now, and follow the device while the choice is "system". Returns the cleanup. */
export function startTheme(): () => void {
  applyTheme()
  if (typeof matchMedia !== 'function') return () => {}
  const mq = matchMedia('(prefers-color-scheme: dark)')
  const onChange = () => { if (savedChoice() === 'system') applyTheme('system') }
  mq.addEventListener('change', onChange)
  return () => mq.removeEventListener('change', onChange)
}
