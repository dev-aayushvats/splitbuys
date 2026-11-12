export type ShareResult = 'shared' | 'copied' | 'cancelled' | 'failed'

/** Opens the phone's share sheet when there is one (so the text goes straight to WhatsApp or any chat); otherwise copies it. */
export async function shareText(text: string): Promise<ShareResult> {
  if (navigator.share) {
    try { await navigator.share({ text }); return 'shared' } catch (e) {
      // closing the share sheet is not an error; anything else falls through to copying
      if (e instanceof DOMException && e.name === 'AbortError') return 'cancelled'
    }
  }
  try { await navigator.clipboard.writeText(text); return 'copied' } catch { return 'failed' }
}
