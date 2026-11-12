import type { Profile } from './types'

const KEY = 'profile'

/**
 * The signed-in person's profile, kept on this device so the app can open without the server. The sign-in token is kept the
 * same way; this is not more sensitive than that. Signing out clears it with everything else (`wipeLocalData`).
 */
export function saveProfile(p: Profile) {
  try { localStorage.setItem(KEY, JSON.stringify(p)) } catch { /* private mode: the app still works, it just cannot open offline */ }
}

/** Null when nothing was saved or what is there is not a profile (an old or damaged entry must never sign anyone in as nobody). */
export function savedProfile(): Profile | null {
  try {
    const v: unknown = JSON.parse(localStorage.getItem(KEY) ?? 'null')
    const p = v as Partial<Profile> | null
    return p && typeof p.id === 'number' && typeof p.email === 'string' ? (p as Profile) : null
  } catch { return null }
}
