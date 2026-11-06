// Mirrors the server's rule (UpiId.java) so mistakes show up while typing. The server stays the authority.
const FORMAT = /^[a-z0-9][a-z0-9._-]{1,63}@[a-z][a-z0-9]{1,31}$/

export const normalizeUpi = (raw: string) => raw.trim().toLowerCase()
export const isValidUpi = (raw: string) => FORMAT.test(normalizeUpi(raw))
