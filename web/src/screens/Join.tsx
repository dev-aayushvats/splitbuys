import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../lib/api'
import { AuthPage, ConfirmButton, Dashed, Screen, Spinner, useAsync, useFeedback } from '../ui'

export function JoinScreen() {
  const { token = '' } = useParams()
  const nav = useNavigate()
  const { toast } = useFeedback()
  const [busy, setBusy] = useState(false)
  const { data: p, error, loading } = useAsync(() => api.previewInvite(token), [token])

  if (loading) return <Screen><div className="grid flex-1 place-items-center"><Spinner /></div></Screen>
  if (error || !p) {
    return (
      <AuthPage title="Invite not found" subtitle={error instanceof Error ? error.message : undefined}>
        <button onClick={() => nav('/', { replace: true })} className="h-13 w-full rounded-full border border-line py-3.5 font-semibold">Go to my groups</button>
      </AuthPage>
    )
  }

  async function join() {
    setBusy(true)
    try {
      const g = await api.joinInvite(token)
      nav(`/groups/${g.id}`, { replace: true })
    } catch (e) {
      setBusy(false)
      toast(e instanceof Error ? e.message : 'Something went wrong')
    }
  }

  return (
    <AuthPage title="You are invited" subtitle="Join this group to split expenses together">
      <Dashed className="p-5 text-center">
        <div className="text-[22px] font-extrabold">{p.groupName}</div>
        <div className="mt-1.5 text-muted">{p.memberCount} member{p.memberCount === 1 ? '' : 's'}</div>
      </Dashed>
      {p.alreadyMember
        ? <ConfirmButton onClick={() => nav(`/groups/${p.groupId}`, { replace: true })}>Open group</ConfirmButton>
        : <ConfirmButton busy={busy} onClick={join}>Join group</ConfirmButton>}
    </AuthPage>
  )
}
