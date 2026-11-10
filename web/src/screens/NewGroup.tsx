import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../lib/api'
import { useIdempotencyKey } from '../lib/idempotency'
import { useRefreshGroups } from '../lib/mutations'
import { Field, Sheet, inputCls, useFeedback } from '../ui'

/** Create a group, then open it. Shared by the Groups page and the bottom bar's Add button. */
export function NewGroupSheet({ open, onClose }: { open: boolean; onClose: () => void }) {
  const nav = useNavigate()
  const { toast } = useFeedback()
  const refresh = useRefreshGroups()
  const createKey = useIdempotencyKey()
  const [name, setName] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    const trimmed = name.trim()
    if (!trimmed) return
    setBusy(true)
    try {
      const g = await api.createGroup(trimmed, createKey.keyFor(trimmed))
      createKey.done()
      setName('')
      onClose()
      void refresh()
      nav(`/groups/${g.id}`)
    } catch (err) { toast(err instanceof Error ? err.message : 'Something went wrong') }
    setBusy(false)
  }

  return (
    <Sheet open={open} onClose={onClose} title="New group">
      <form onSubmit={submit} className="space-y-4">
        <Field label="Group name">
          <input className={inputCls} autoFocus autoCapitalize="words" value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <button type="submit" disabled={!name.trim() || busy}
          className="btn-accent h-13 w-full rounded-full py-3.5 font-bold disabled:opacity-45">Create</button>
      </form>
    </Sheet>
  )
}
