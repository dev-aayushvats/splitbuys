import type {
  ActivityItem, Balances, Expense, ExpenseBody, Friend, GroupData, GroupDetail, GroupSummary, HistoryEntry, InvitePreview, Page, Profile, Settlement,
} from './types'
import { fetchWithRetry, IDEMPOTENCY_HEADER, parseBody, RequestTimeoutError } from './http'

// Unset (dev): the local backend. Set to an empty string (the Docker image does this): same origin, i.e. calls go to /api on
// whichever host served the app, where a reverse proxy forwards them to the backend.
export const API_URL: string = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

const PAGE_SIZE = 30

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

async function send<T = unknown>(method: string, path: string, body?: unknown, idempotencyKey?: string): Promise<T> {
  let res: Response
  try {
    res = await fetchWithRetry(fetch, `${API_URL}/api/v1${path}`, {
      method,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(idempotencyKey ? { [IDEMPOTENCY_HEADER]: idempotencyKey } : {}),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch (e) {
    if (e instanceof RequestTimeoutError) {
      throw new ApiError(method === 'GET' || idempotencyKey
        ? 'The server is taking too long to respond. Please try again.'
        // a write without a key may have gone through before the connection gave up
        : 'The server took too long to respond. Check whether your change was saved before trying again.')
    }
    throw new ApiError('Cannot reach the server. Check your connection.')
  }
  // a proxy error page (HTML) is not JSON: treat it as "no details", never as a crash
  const data = parseBody(await res.text()) as { detail?: unknown; error?: unknown } | null
  if (res.ok) return data as T
  if (res.status === 401 && token) onUnauthorized?.()
  // RFC 9457 problem document: `detail` is the user-facing message (`error` is kept for older servers)
  const message = [data?.detail, data?.error].find((m) => typeof m === 'string')
  throw new ApiError(message ?? 'Something went wrong', res.status)
}

export const api = {
  requestOtp: (email: string) => send<void>('POST', '/auth/request-otp', { email }),
  verifyOtp: (email: string, code: string) =>
    send<{ token: string; user: Profile }>('POST', '/auth/verify-otp', { email, code }),
  me: () => send<Profile>('GET', '/me'),
  setName: (name: string) => send<Profile>('PUT', '/me', { name }),
  setUpi: (upiId: string) => send<Profile>('PUT', '/me/upi', { upiId }),
  clearUpi: () => send<Profile>('DELETE', '/me/upi'),
  deleteAccount: (email: string) => send<void>('DELETE', '/me', { email }),

  pushConfig: () => send<{ enabled: boolean; publicKey: string | null }>('GET', '/push/config'),
  subscribePush: (sub: { endpoint: string; keys: { p256dh: string; auth: string } }) => send<void>('POST', '/push/subscriptions', sub),
  unsubscribePush: (endpoint: string) => send<void>('DELETE', '/push/subscriptions', { endpoint }),

  history: (g: number, cursor?: string | null) =>
    send<Page<HistoryEntry>>('GET', `/groups/${g}/history?limit=${PAGE_SIZE}${cursor ? `&cursor=${cursor}` : ''}`),
  undoChange: (g: number, historyId: number) => send<HistoryEntry>('POST', `/groups/${g}/history/${historyId}/undo`),
  activity: (cursor?: string | null) =>
    send<Page<ActivityItem>>('GET', `/me/activity?limit=${PAGE_SIZE}${cursor ? `&cursor=${cursor}` : ''}`),
  friends: () => send<Friend[]>('GET', '/me/friends'),
  groups: () => send<GroupSummary[]>('GET', '/groups'),
  createGroup: (name: string, key: string) => send<GroupDetail>('POST', '/groups', { name }, key),
  group: (id: number) => send<GroupDetail>('GET', `/groups/${id}`),
  regenerateInvite: (id: number) => send<GroupDetail>('POST', `/groups/${id}/invite/regenerate`),
  renameGroup: (id: number, name: string) => send<GroupDetail>('PUT', `/groups/${id}`, { name }),
  deleteGroup: (id: number) => send<void>('DELETE', `/groups/${id}`),
  leaveGroup: (id: number) => send<void>('DELETE', `/groups/${id}/members/me`),
  removeMember: (id: number, memberId: number) => send<void>('DELETE', `/groups/${id}/members/${memberId}`),

  previewInvite: (t: string) => send<InvitePreview>('GET', `/invites/${t}`),
  joinInvite: (t: string) => send<GroupDetail>('POST', `/invites/${t}/join`),

  expenses: (g: number, cursor?: string | null) =>
    send<Page<Expense>>('GET', `/groups/${g}/expenses?limit=${PAGE_SIZE}${cursor ? `&cursor=${cursor}` : ''}`),
  expense: (g: number, e: number) => send<Expense>('GET', `/groups/${g}/expenses/${e}`),
  addExpense: (g: number, b: ExpenseBody, key: string) => send<void>('POST', `/groups/${g}/expenses`, b, key),
  updateExpense: (g: number, e: number, b: ExpenseBody) => send<void>('PUT', `/groups/${g}/expenses/${e}`, b),
  deleteExpense: (g: number, e: number) => send<void>('DELETE', `/groups/${g}/expenses/${e}`),
  settlements: (g: number, cursor?: string | null) =>
    send<Page<Settlement>>('GET', `/groups/${g}/settlements?limit=${PAGE_SIZE}${cursor ? `&cursor=${cursor}` : ''}`),
  addSettlement: (g: number, fromUserId: number, toUserId: number, amountPaise: number, key: string) =>
    send<void>('POST', `/groups/${g}/settlements`, { fromUserId, toUserId, amountPaise }, key),
  deleteSettlement: (g: number, s: number) => send<void>('DELETE', `/groups/${g}/settlements/${s}`),
  balances: (g: number) => send<Balances>('GET', `/groups/${g}/balances`),

  async groupData(id: number): Promise<GroupData> {
    const [group, expenses, settlements, balances] = await Promise.all([
      api.group(id), api.expenses(id), api.settlements(id), api.balances(id),
    ])
    return {
      group, balances,
      expenses: expenses.items, expensesCursor: expenses.nextCursor,
      settlements: settlements.items, settlementsCursor: settlements.nextCursor,
    }
  },
}
