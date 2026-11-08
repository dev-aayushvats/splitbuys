// Transport rules for talking to the API: every request has a deadline, and a request that is safe to repeat is retried once
// when the failure looks temporary. Kept free of app state so it can be tested on its own.

export class RequestTimeoutError extends Error {
  constructor(readonly method: string) {
    super('request timed out')
  }
}
export class NetworkError extends Error {
  constructor() {
    super('network error')
  }
}

export interface FetchPlan {
  /** Per attempt. A sleeping Neon database can take several seconds to wake, so this is generous. */
  timeoutMs?: number
  /** Extra attempts after the first; only used for GET, HEAD and requests that carry an Idempotency-Key. */
  retries?: number
  retryDelayMs?: number
}

export const IDEMPOTENCY_HEADER = 'Idempotency-Key'

// Reads are always safe to repeat. A write that timed out may already have been applied by the server, so it is only
// repeated when it carries an Idempotency-Key: the server then answers a repeat with the original result instead of
// creating a second copy.
const REPEATABLE = new Set(['GET', 'HEAD'])
const isRepeatable = (method: string, init: RequestInit) =>
  REPEATABLE.has(method) || new Headers(init.headers).has(IDEMPOTENCY_HEADER)
// "bad gateway", "unavailable", "gateway timeout": the proxy could not reach the app, typically while it restarts
const TEMPORARY_STATUS = new Set([502, 503, 504])

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms))

export async function fetchWithRetry(
  fetchFn: typeof fetch,
  url: string,
  init: RequestInit = {},
  { timeoutMs = 20_000, retries = 1, retryDelayMs = 1_000 }: FetchPlan = {},
): Promise<Response> {
  const method = (init.method ?? 'GET').toUpperCase()
  const attempts = isRepeatable(method, init) ? retries + 1 : 1

  for (let attempt = 1; ; attempt++) {
    const controller = new AbortController()
    let timedOut = false
    const timer = setTimeout(() => {
      timedOut = true
      controller.abort()
    }, timeoutMs)
    try {
      const res = await fetchFn(url, { ...init, signal: controller.signal })
      if (!TEMPORARY_STATUS.has(res.status) || attempt >= attempts) return res
    } catch {
      if (attempt >= attempts) throw timedOut ? new RequestTimeoutError(method) : new NetworkError()
    } finally {
      clearTimeout(timer)
    }
    await sleep(retryDelayMs)
  }
}

/** Parses a response body without throwing: an HTML error page from a proxy is not JSON and must not crash the caller. */
export function parseBody(text: string): unknown {
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}
