import { describe, expect, it } from 'vitest'
import { canPayViaUpi, isValidUpi, normalizeUpi } from './upi'

describe('upi id', () => {
  it('accepts real-world shapes', () => {
    for (const ok of ['asha@okbank', 'asha.k@okbank', '9876543210@ybl', 'a_b-c.d@paytm', '  Asha@OkBank ']) {
      expect(isValidUpi(ok), ok).toBe(true)
    }
  })
  it('rejects things that are not UPI ids, including anything that could add link parameters', () => {
    for (const bad of ['', 'asha', 'asha@', '@bank', 'a@bank', 'asha@1bank', 'ash a@bank', 'asha&am=1@bank', 'asha?x=1@bank', 'a@b@c']) {
      expect(isValidUpi(bad), bad).toBe(false)
    }
  })
  it('normalises case and whitespace like the server', () => {
    expect(normalizeUpi('  Asha.K@OkBank ')).toBe('asha.k@okbank')
  })
})

describe('canPayViaUpi', () => {
  it('needs a UPI ID and an amount within the one lakh limit', () => {
    expect(canPayViaUpi({ upiId: 'a@upi', amountPaise: 10_000_000 })).toBe(true)
    expect(canPayViaUpi({ upiId: 'a@upi', amountPaise: 10_000_001 })).toBe(false)
    expect(canPayViaUpi({ upiId: null, amountPaise: 100 })).toBe(false)
  })
})
