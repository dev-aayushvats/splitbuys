import { useState, type FormEvent } from 'react'
import { useAuth } from '../lib/auth'
import { label } from '../lib/types'
import { isValidUpi, normalizeUpi } from '../lib/upi'
import { DeleteAccountSheet } from './DeleteAccount'
import { InsightsCard } from './InsightsCard'
import { NotificationsRow } from './Notifications'
import { Avatar, Icon, ProfileHeader, Screen, useFeedback } from '../ui'

export function ProfileScreen() {
  const { user, setName, setUpi, clearUpi, signOut } = useAuth()
  const { toast, confirm } = useFeedback()
  const me = user!
  const [deleting, setDeleting] = useState(false)
  const [name, setNameText] = useState(me.name ?? '')
  const [busy, setBusy] = useState(false)
  const changed = name.trim() !== (me.name ?? '') && name.trim() !== ''
  const [upi, setUpiText] = useState(me.upiId ?? '')
  const [upiBusy, setUpiBusy] = useState(false)
  const upiChanged = normalizeUpi(upi) !== (me.upiId ?? '')
  const upiInvalid = upi.trim() !== '' && !isValidUpi(upi)

  async function save(e: FormEvent) {
    e.preventDefault()
    if (!changed) return
    setBusy(true)
    try { await setName(name.trim()); toast('Name updated') } catch (err) { toast(err instanceof Error ? err.message : 'Something went wrong') }
    setBusy(false)
  }

  async function saveUpi(e: FormEvent) {
    e.preventDefault()
    if (!upiChanged || upiInvalid || upi.trim() === '') return
    setUpiBusy(true)
    try { await setUpi(normalizeUpi(upi)); setUpiText(normalizeUpi(upi)); toast('UPI ID saved') } catch (err) { toast(err instanceof Error ? err.message : 'Something went wrong') }
    setUpiBusy(false)
  }

  async function removeUpi() {
    if (!(await confirm('Remove your UPI ID?\nPeople will no longer see it when they settle up with you.', 'Remove'))) return
    setUpiBusy(true)
    try { await clearUpi(); setUpiText(''); toast('UPI ID removed') } catch (err) { toast(err instanceof Error ? err.message : 'Something went wrong') }
    setUpiBusy(false)
  }

  return (
    <Screen className="pt-safe pb-safe">
      <div className="px-5 pt-6">
        <ProfileHeader avatar={<Avatar user={me} size={88} />} smallAvatar={<Avatar user={me} size={32} />} name={label(me)} detail={me.email} />
      </div>

      <div className="mt-6 space-y-4 px-5">
        <InsightsCard />
        <div className="space-y-2">
        <div className="glass-lite divide-y divide-white/10 overflow-hidden rounded-[24px]">
          <form onSubmit={save} className="flex items-center gap-3 px-4 py-2.5">
            <label className="flex min-w-0 flex-1 items-center gap-3">
              <span className="w-16 shrink-0 text-[13px] text-muted">Name</span>
              <input className="min-w-0 flex-1 bg-transparent py-1.5 outline-none" autoCapitalize="words" value={name} onChange={(e) => setNameText(e.target.value)} />
            </label>
            {changed && <SaveButton busy={busy} />}
          </form>

          <form onSubmit={saveUpi} className="flex items-center gap-3 px-4 py-2.5">
            <label className="flex min-w-0 flex-1 items-center gap-3">
              <span className="w-16 shrink-0 text-[13px] text-muted">UPI ID</span>
              <input className="min-w-0 flex-1 bg-transparent py-1.5 outline-none placeholder:text-muted/60" inputMode="email" autoCapitalize="none" autoCorrect="off"
                spellCheck={false} placeholder="name@bank" value={upi} onChange={(e) => setUpiText(e.target.value)} />
            </label>
            {upiChanged && upi.trim() !== '' && !upiInvalid ? <SaveButton busy={upiBusy} />
              : me.upiId && !upiChanged && <button type="button" onClick={removeUpi} disabled={upiBusy} className="shrink-0 px-1 text-[13px] font-semibold text-neg">Remove</button>}
          </form>

          <NotificationsRow />
        </div>
        {upiInvalid
          ? <p className="px-2 text-[13px] text-neg">That doesn't look like a UPI ID, like name@bank</p>
          : <p className="px-2 text-[12px] text-muted">Your UPI ID is shown only to people who owe you.</p>}
        </div>
      </div>

      <div className="mt-auto px-5 pb-28 pt-8">
        <button type="button" onClick={async () => { if (await confirm('Sign out of SplitBuys?', 'Sign out')) signOut() }}
          className="glass-lite flex w-full items-center justify-center gap-2 rounded-full py-3 font-semibold text-neg">
          <Icon name="logout" size={18} /> Sign out
        </button>
        <button type="button" onClick={() => setDeleting(true)} className="mt-2 w-full py-2 text-[13px] font-semibold text-muted underline-offset-2 hover:underline">
          Delete account
        </button>
      </div>
      <DeleteAccountSheet open={deleting} onClose={() => setDeleting(false)} email={me.email} />
    </Screen>
  )
}

/** Appears only when there is something to save, so a form with nothing to do has no button to ignore. */
function SaveButton({ busy }: { busy: boolean }) {
  return <button type="submit" disabled={busy} className="btn-accent h-9 shrink-0 rounded-full px-4 text-[13px] font-bold disabled:opacity-60">{busy ? '…' : 'Save'}</button>
}
