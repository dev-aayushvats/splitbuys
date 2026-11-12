import { useState, type FormEvent } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '../../lib/api'
import { useAuth } from '../../lib/auth'
import { formatDate } from '../../lib/money'
import { keys, useComments } from '../../lib/queries'
import { label } from '../../lib/types'
import { Icon, Spinner, tap, useFeedback } from '../../ui'

const MAX = 500

/** The notes on one expense, oldest first, with a box to add one. Any member can read and write; people delete only their own. */
export function Comments({ gid, expenseId }: { gid: number; expenseId: number }) {
  const me = useAuth().user!
  const { toast, confirm } = useFeedback()
  const qc = useQueryClient()
  const { data, error, isPending } = useComments(gid, expenseId)
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const key = [...keys.group(me.id, gid), 'comments', expenseId] as const
  const ok = text.trim() !== ''

  async function send(e: FormEvent) {
    e.preventDefault()
    if (!ok || busy) return
    setBusy(true)
    try { await api.addComment(gid, expenseId, text.trim()); setText(''); await qc.invalidateQueries({ queryKey: key }) }
    catch (err) { toast(err instanceof Error ? err.message : 'Something went wrong') }
    setBusy(false)
  }

  async function remove(id: number) {
    if (!(await confirm('Delete this comment?'))) return
    try { await api.deleteComment(gid, id); await qc.invalidateQueries({ queryKey: key }) }
    catch (err) { toast(err instanceof Error ? err.message : 'Something went wrong') }
  }

  return (
    <div>
      <div className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted">Notes{data && data.length > 0 ? ` · ${data.length}` : ''}</div>
      <div className="max-h-[26dvh] space-y-2 overflow-y-auto">
        {isPending ? <div className="py-2 text-center"><Spinner className="size-4" /></div>
          : error ? <p className="px-1 text-[13px] text-muted">Could not load notes.</p>
          : data.length === 0 ? <p className="px-1 text-[13px] text-muted">No notes yet. Ask or explain something about this expense.</p>
          : data.map((c) => (
            <div key={c.id} className="rounded-2xl bg-white/[0.07] px-3 py-2 ring-1 ring-white/10">
              <div className="flex items-center gap-2 text-[12px] text-muted">
                <span className="font-semibold">{c.author.id === me.id ? 'You' : label(c.author)}</span>
                <span>{formatDate(c.createdAt)}</span>
                {c.author.id === me.id && (
                  <button type="button" aria-label="Delete note" onClick={() => remove(c.id)} className="-my-1 -mr-1 ml-auto grid size-7 place-items-center rounded-full"><Icon name="trash" size={14} /></button>
                )}
              </div>
              <p className="mt-0.5 whitespace-pre-wrap break-words text-[14.5px]">{c.text}</p>
            </div>
          ))}
      </div>
      <form onSubmit={send} className="mt-2.5 flex items-center gap-2">
        <input aria-label="Add a note" value={text} maxLength={MAX} onChange={(e) => setText(e.target.value)} placeholder="Add a note"
          className="glass-btn min-w-0 flex-1 rounded-full px-4 py-3 text-[15px] outline-none placeholder:text-muted/70 focus:ring-4 focus:ring-accent/15" />
        <button type="submit" aria-label="Send note" disabled={!ok || busy} onClick={tap}
          className="btn-accent grid size-11 shrink-0 place-items-center rounded-full disabled:opacity-45">{busy ? <Spinner className="size-4" /> : <Icon name="arrow" size={18} />}</button>
      </form>
    </div>
  )
}
