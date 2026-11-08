// Mirrors the server's confirmation rule (AccountService.java): the typed email must equal the account's, ignoring case
// and surrounding spaces. The server stays the authority; this only decides when the button turns on.
export const emailMatches = (typed: string, email: string) => typed.trim().toLowerCase() === email.trim().toLowerCase() && typed.trim() !== ''
