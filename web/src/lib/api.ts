import type {
  Balances, Expense, ExpenseBody, GroupData, GroupDetail, GroupSummary, InvitePreview, Settlement, User,
} from './types'

export const API_URL: string = import.meta.env.VITE_API_URL || 'http://localhost:8080'

export class ApiError extends Error {
  status?: number
  constructor(message: string, status?: number) {
    super(message)
    this.status = status
  }
}

const TOKEN_KEY = 'token'
let token: string | null = null
let onUnauthorized: (() => void) | null = null

export const setUnauthorizedHandler = (fn: () => void) => { onUnauthorized = fn }
export const getSavedToken = () => { try { return localStorage.getItem(TOKEN_KEY) } catch { return null } }
export function setToken(t: string | null) {
  token = t
  try { t ? localStorage.setItem(TOKEN_KEY, t) : localStorage.removeItem(TOKEN_KEY) } catch { /* private mode */ }
}

async function send<T = unknown>(method: string, path: string, body?: unknown): Promise<T> {
  let res: Response
  try {
    res = await fetch(`${API_URL}/api/v1${path}`, {
      method,
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError('Cannot reach the server. Check your connection.')
  }
  const text = await res.text()
  const data = text ? JSON.parse(text) : null
  if (res.ok) return data as T
  if (res.status === 401 && token) onUnauthorized?.()
  // RFC 9457 problem document: `detail` is the user-facing message (`error` is kept for older servers)
  const message = [data?.detail, data?.error].find((m) => typeof m === 'string')
  throw new ApiError(message ?? 'Something went wrong', res.status)
}

export const api = {
  requestOtp: (email: string) => send<void>('POST', '/auth/request-otp', { email }),
  verifyOtp: (email: string, code: string) =>
    send<{ token: string; user: User }>('POST', '/auth/verify-otp', { email, code }),
  me: () => send<User>('GET', '/me'),
  setName: (name: string) => send<User>('PUT', '/me', { name }),

  groups: () => send<GroupSummary[]>('GET', '/groups'),
  createGroup: (name: string) => send<GroupDetail>('POST', '/groups', { name }),
  group: (id: number) => send<GroupDetail>('GET', `/groups/${id}`),
  regenerateInvite: (id: number) => send<GroupDetail>('POST', `/groups/${id}/invite/regenerate`),
  renameGroup: (id: number, name: string) => send<GroupDetail>('PUT', `/groups/${id}`, { name }),
  deleteGroup: (id: number) => send<void>('DELETE', `/groups/${id}`),
  leaveGroup: (id: number) => send<void>('DELETE', `/groups/${id}/members/me`),
  removeMember: (id: number, memberId: number) => send<void>('DELETE', `/groups/${id}/members/${memberId}`),

  previewInvite: (t: string) => send<InvitePreview>('GET', `/invites/${t}`),
  joinInvite: (t: string) => send<GroupDetail>('POST', `/invites/${t}/join`),

  expenses: (g: number) => send<Expense[]>('GET', `/groups/${g}/expenses`),
  addExpense: (g: number, b: ExpenseBody) => send<void>('POST', `/groups/${g}/expenses`, b),
  updateExpense: (g: number, e: number, b: ExpenseBody) => send<void>('PUT', `/groups/${g}/expenses/${e}`, b),
  deleteExpense: (g: number, e: number) => send<void>('DELETE', `/groups/${g}/expenses/${e}`),
  settlements: (g: number) => send<Settlement[]>('GET', `/groups/${g}/settlements`),
  addSettlement: (g: number, fromUserId: number, toUserId: number, amountPaise: number) =>
    send<void>('POST', `/groups/${g}/settlements`, { fromUserId, toUserId, amountPaise }),
  deleteSettlement: (g: number, s: number) => send<void>('DELETE', `/groups/${g}/settlements/${s}`),
  balances: (g: number) => send<Balances>('GET', `/groups/${g}/balances`),

  async groupData(id: number): Promise<GroupData> {
    const [group, expenses, settlements, balances] = await Promise.all([
      api.group(id), api.expenses(id), api.settlements(id), api.balances(id),
    ])
    return { group, expenses, settlements, balances }
  },
}
