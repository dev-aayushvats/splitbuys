// In-memory stale-while-revalidate store behind useAsync: a screen revisited shows its last data at once while a fresh
// copy loads in the background. Memory only, so it never outlives the tab, and it is emptied on sign-in/out and after any
// successful write, so a screen never shows data that predates the user's own change.
const store = new Map<string, unknown>()

export const peekCache = <T>(key: string): T | undefined => store.get(key) as T | undefined
export const putCache = (key: string, value: unknown) => { store.set(key, value) }
export const clearCache = () => store.clear()
