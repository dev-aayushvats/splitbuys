import { label, type User } from '../lib/types'

const PALETTES = [
  ['#FFBD14', '#FB7803'], ['#FFC933', '#FF9A1F'], ['#FFA31F', '#F26A0A'],
  ['#FFD36B', '#FFA31F'], ['#FF9A44', '#FC6A3C'], ['#FFB347', '#F2840A'],
]

export function Avatar({ user, size = 44 }: { user: User; size?: number }) {
  const [a, b] = PALETTES[user.id % PALETTES.length]
  return (
    <div className="grid shrink-0 place-items-center rounded-full font-extrabold text-on-accent shadow-[0_6px_16px_-4px_rgb(0_0_0/0.5),inset_0_1px_0_rgb(255_255_255/0.45)] ring-1 ring-white/25"
      style={{ width: size, height: size, fontSize: size * 0.42, background: `linear-gradient(135deg, ${a}, ${b})` }}>
      {[...label(user)][0]?.toUpperCase()}
    </div>
  )
}

export function AvatarStack({ users, size = 34, max = 4 }: { users: User[]; size?: number; max?: number }) {
  const shown = users.slice(0, max)
  const extra = users.length - shown.length
  const step = size * 0.62
  const count = shown.length + (extra > 0 ? 1 : 0)
  const ring = 'absolute rounded-full bg-[#101113] p-0.5'
  return (
    <div className="relative shrink-0" style={{ width: size + 4 + step * (count - 1), height: size + 4 }}>
      {shown.map((u, i) => (
        <div key={u.id} className={ring} style={{ left: i * step }}><Avatar user={u} size={size} /></div>
      ))}
      {extra > 0 && (
        <div className={ring} style={{ left: shown.length * step }}>
          <div className="grid place-items-center rounded-full bg-white/15 font-bold ring-1 ring-white/20" style={{ width: size, height: size, fontSize: size * 0.34 }}>+{extra}</div>
        </div>
      )}
    </div>
  )
}
