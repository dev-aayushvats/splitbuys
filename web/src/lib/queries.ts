import { useQuery } from '@tanstack/react-query'
import { api } from './api'
import { useAuth } from './auth'

// Keys start with the user's id, so one person's cached data can never be shown to another, even if a sign-out were missed.
export const keys = {
  groups: (uid: number) => ['u', uid, 'groups'] as const,
  friends: (uid: number) => ['u', uid, 'friends'] as const,
  group: (uid: number, gid: number) => ['u', uid, 'group', gid] as const,
}

export function useGroups() {
  const uid = useAuth().user!.id
  return useQuery({ queryKey: keys.groups(uid), queryFn: () => api.groups(), meta: { persist: true } })
}

export function useGroupData(gid: number) {
  const uid = useAuth().user!.id
  return useQuery({ queryKey: keys.group(uid, gid), queryFn: () => api.groupData(gid), meta: { persist: true } })
}

export function useFriends() {
  const uid = useAuth().user!.id
  return useQuery({ queryKey: keys.friends(uid), queryFn: () => api.friends(), meta: { persist: true } })
}
