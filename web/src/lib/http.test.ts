import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchWithRetry, NetworkError, parseBody, RequestTimeoutError } from './http'

const ok = (status = 200) => new Response('{}', { status })
const plan = { timeoutMs: 1000, retries: 1, retryDelayMs: 100 }

/** A fetch that never answers on its own but honours the abort signal, like the real one. */
const hanging = () =>
  vi.fn((_url: URL | RequestInfo, init?: RequestInit) =>
    new Promise<Response>((_, reject) => {
      init?.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')))
    }),
  )

beforeEach(() => { vi.useFakeTimers() })
afterEach(() => { vi.useRealTimers() })

describe('fetchWithRetry', () => {
  it('returns the first response when it works', async () => {
    const fetchFn = vi.fn().mockResolvedValue(ok())
    const res = await fetchWithRetry(fetchFn, '/x', {}, plan)
    expect(res.status).toBe(200)
    expect(fetchFn).toHaveBeenCalledTimes(1)
  })

  it('retries a GET once after a network error', async () => {
    const fetchFn = vi.fn().mockRejectedValueOnce(new TypeError('offline')).mockResolvedValueOnce(ok())
    const promise = fetchWithRetry(fetchFn, '/x', { method: 'GET' }, plan)
    await vi.advanceTimersByTimeAsync(100)
    expect((await promise).status).toBe(200)
    expect(fetchFn).toHaveBeenCalledTimes(2)
  })

  it('retries a GET once after 502, 503 or 504', async () => {
    for (const status of [502, 503, 504]) {
      const fetchFn = vi.fn().mockResolvedValueOnce(ok(status)).mockResolvedValueOnce(ok())
      const promise = fetchWithRetry(fetchFn, '/x', {}, plan)
      await vi.advanceTimersByTimeAsync(100)
      expect((await promise).status).toBe(200)
      expect(fetchFn).toHaveBeenCalledTimes(2)
    }
  })

  it('gives up after the retry and hands back the last bad response', async () => {
    const fetchFn = vi.fn().mockResolvedValue(ok(503))
    const promise = fetchWithRetry(fetchFn, '/x', {}, plan)
    await vi.advanceTimersByTimeAsync(100)
    expect((await promise).status).toBe(503)
    expect(fetchFn).toHaveBeenCalledTimes(2)
  })

  it('never retries answers that a retry cannot change', async () => {
    for (const status of [400, 401, 403, 404, 409, 429, 500]) {
      const fetchFn = vi.fn().mockResolvedValue(ok(status))
      expect((await fetchWithRetry(fetchFn, '/x', {}, plan)).status).toBe(status)
      expect(fetchFn).toHaveBeenCalledTimes(1)
    }
  })

  it('never repeats a write, even after a network error or a 503', async () => {
    for (const method of ['POST', 'PUT', 'DELETE']) {
      const failing = vi.fn().mockRejectedValue(new TypeError('offline'))
      await expect(fetchWithRetry(failing, '/x', { method }, plan)).rejects.toBeInstanceOf(NetworkError)
      expect(failing).toHaveBeenCalledTimes(1)

      const unavailable = vi.fn().mockResolvedValue(ok(503))
      expect((await fetchWithRetry(unavailable, '/x', { method }, plan)).status).toBe(503)
      expect(unavailable).toHaveBeenCalledTimes(1)
    }
  })

  it('aborts a request that exceeds the deadline and reports a timeout', async () => {
    const fetchFn = hanging()
    const promise = fetchWithRetry(fetchFn, '/x', { method: 'POST' }, plan)
    const assertion = expect(promise).rejects.toBeInstanceOf(RequestTimeoutError)
    await vi.advanceTimersByTimeAsync(1000)
    await assertion
    expect(fetchFn).toHaveBeenCalledTimes(1)
  })

  it('retries a GET that timed out, then reports the timeout if it times out again', async () => {
    const fetchFn = hanging()
    const promise = fetchWithRetry(fetchFn, '/x', {}, plan)
    const assertion = expect(promise).rejects.toBeInstanceOf(RequestTimeoutError)
    await vi.advanceTimersByTimeAsync(1000 + 100 + 1000)
    await assertion
    expect(fetchFn).toHaveBeenCalledTimes(2)
  })

  it('tells a timeout from other network failures', async () => {
    const fetchFn = vi.fn().mockRejectedValue(new TypeError('offline'))
    const promise = fetchWithRetry(fetchFn, '/x', { method: 'POST' }, plan)
    await expect(promise).rejects.toBeInstanceOf(NetworkError)
  })
})

describe('parseBody', () => {
  it('parses JSON, and treats empty and non-JSON bodies as no data instead of throwing', () => {
    expect(parseBody('{"a":1}')).toEqual({ a: 1 })
    expect(parseBody('')).toBeNull()
    expect(parseBody('<html><body>502 Bad Gateway</body></html>')).toBeNull()
  })
})
