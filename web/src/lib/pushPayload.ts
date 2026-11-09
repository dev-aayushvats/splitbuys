// What the server puts in a push message, read defensively: the service worker must show *something* sensible even if a
// field is missing or odd, and must never navigate anywhere outside this app because of a message's contents.

export interface PushPayload { title: string; body: string; tag: string | undefined; url: string; groupId: number | null }

/** Only paths inside this app: "/groups/3" yes; "https://elsewhere", "//elsewhere" and "javascript:" no. */
export const safeAppPath = (value: unknown): string => (typeof value === 'string' && /^\/(?!\/)[^\s\\]*$/.test(value) ? value : '/')

export function parsePayload(raw: unknown): PushPayload {
  const d = raw && typeof raw === 'object' ? (raw as Record<string, unknown>) : {}
  const text = (v: unknown, fallback: string, max: number) => (typeof v === 'string' && v.trim() ? v.slice(0, max) : fallback)
  return {
    title: text(d.title, 'SplitBuys', 80),
    body: text(d.body, 'Something changed in one of your groups', 200),
    tag: typeof d.tag === 'string' && d.tag ? d.tag.slice(0, 64) : undefined,
    url: safeAppPath(d.url),
    groupId: typeof d.groupId === 'number' && Number.isSafeInteger(d.groupId) ? d.groupId : null,
  }
}
