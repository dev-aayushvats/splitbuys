import { label, type User } from '../../lib/types'

export const who = (u: User, me: User) => (u.id === me.id ? 'You' : label(u))
