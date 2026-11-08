// Mirrors the server's rule (UpiId.java) so mistakes show up while typing. The server stays the authority.
const FORMAT = /^[a-z0-9][a-z0-9._-]{1,63}@[a-z][a-z0-9]{1,31}$/

// UPI apps refuse a single payment above Rs 1,00,000, so above it the UI says to pay in parts instead of offering the ID.
export const UPI_LIMIT_PAISE = 1_00_000_00

/** Whether the UPI details should be offered for a suggested transfer: the receiver has an ID and the amount is within the limit. */
export const canPayViaUpi = (t: { upiId: string | null; amountPaise: number }) => !!t.upiId && t.amountPaise <= UPI_LIMIT_PAISE

export const normalizeUpi = (raw: string) => raw.trim().toLowerCase()
export const isValidUpi = (raw: string) => FORMAT.test(normalizeUpi(raw))
