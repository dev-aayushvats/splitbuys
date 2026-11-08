import { useRemoveMember } from '../../lib/mutations'
import { label, type GroupData, type User } from '../../lib/types'
import { Avatar, AvatarStack, Dashed, Icon, PillButton, SectionTitle, Tile, useFeedback } from '../../ui'

export function MembersTab({ data, me }: { data: GroupData; me: User }) {
  const { toast, confirm } = useFeedback()
  const { group } = data
  const removeMember = useRemoveMember(group.id)
  const link = `${window.location.origin}/join/${group.inviteToken}`

  async function share() {
    try {
      if (navigator.share) { await navigator.share({ title: group.name, text: `Join ${group.name} on SplitBuys`, url: link }); return }
      await navigator.clipboard.writeText(link)
      toast('Invite link copied')
    } catch (e) {
      if ((e as Error)?.name !== 'AbortError') toast('Could not share the link')
    }
  }

  return (
    <div className="space-y-6">
      <Dashed className="p-4 text-center">
        <div className="font-semibold">Splitting with</div>
        <div className="mt-2.5 flex justify-center"><AvatarStack users={group.members} size={44} max={5} /></div>
        <div className="mt-2 text-[12.5px] text-muted">{group.members.length} {group.members.length === 1 ? 'person' : 'people'}</div>
        <div className="mt-4 flex items-center gap-2 rounded-[20px] bg-black/25 py-1.5 pl-3.5 pr-1.5 ring-1 ring-white/10">
          <span className="min-w-0 flex-1 select-all truncate text-left text-[12.5px] text-muted">{link}</span>
          <PillButton onClick={share} className="h-10 px-3.5"><Icon name="copy" size={16} />{'share' in navigator ? 'Share' : 'Copy'}</PillButton>
        </div>
      </Dashed>

      <section>
        <SectionTitle>Members</SectionTitle>
        <div className="stagger space-y-3">
          {group.members.map((m) => (
            <Tile key={m.id} leading={<Avatar user={m} size={46} />} title={m.id === me.id ? `${label(m)} (you)` : label(m)}
              subtitle={m.name ? m.email : undefined}
              onClick={m.id === me.id ? undefined : async () => {
                if (!(await confirm(`Remove ${label(m)} from this group?\nThey must be settled up first.`, 'Remove'))) return
                removeMember.mutate(m.id, { onSuccess: () => toast(`${label(m)} removed`) })
              }} />
          ))}
        </div>
      </section>
    </div>
  )
}
