import { describe, expect, it } from 'vitest'
import { keyToBytes } from './push'

describe('keyToBytes', () => {
  it('decodes the URL-safe base64 key the server publishes', () => {
    // "-" and "_" stand for "+" and "/"; no padding is sent
    expect([...keyToBytes('-_8')]).toEqual([0xfb, 0xff])
    expect([...keyToBytes('AQID')]).toEqual([1, 2, 3])
    expect([...keyToBytes('AQI')]).toEqual([1, 2])
  })
  it('turns a 65-byte uncompressed P-256 key into 65 bytes', () => {
    const point = new Uint8Array(65)
    point[0] = 4
    const b64url = btoa(String.fromCharCode(...point)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
    expect(keyToBytes(b64url)).toHaveLength(65)
    expect(keyToBytes(b64url)[0]).toBe(4)
  })
})
