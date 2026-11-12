# Backend architecture

Spring Boot 4 · Java 17 · Spring Data JPA · Flyway · Spring Security (JWT resource server) · SQLite by default.

## Shape: package-by-feature, layered inside each feature

```
com.splitbuys.backend
├── common/          cross-cutting, depends on no feature
│   ├── error/         DomainException + NotFound / InvalidRequest / Conflict / Unauthorized / RateLimited / DependencyFailure
│   ├── web/           GlobalExceptionHandler (RFC 9457 problems), RequestIdFilter, ProblemJson
│   ├── idempotency/   Idempotency-Key replay for create requests (same transaction as the work), 24 h purge
│   └── security/      SecurityConfig, rate limiting, @CurrentUser, prod safety check
├── user/            accounts           UserService is the only door in
├── auth/            email OTP + access tokens
├── group/           groups, membership, invites          declares LedgerPort
├── ledger/          expenses, payments, balances         implements group.LedgerPort
├── account/         deleting an account (sits on user, group, auth; nothing depends on it)
├── backup/          database backups (PostgreSQL) and restore, behind the admin token
└── split/           pure split / debt-simplification maths (no framework imports)
```

Inside a feature: `Controller → Service → Repository → Entity`, with request/response records beside them.
Controllers only authenticate, validate and delegate; services own transactions and business rules; repositories are
package-private, so another feature cannot reach a table except through the owning service.

## Rules that keep it honest (`ArchitectureTest`, enforced on every build)

* No cycles between features. `group` never imports `ledger`: it asks for balances through `group.LedgerPort`,
  which `ledger` implements (dependency inversion), so the arrow only points `ledger → group`.
* `split` and `common.error` depend on the JDK only. `common` depends on no feature. `user` on no other feature.
* Controllers never touch repositories. Services never use HTTP or servlet types. Constructor injection only.
* Entities have no public fields.

## Errors

Services throw `DomainException` subclasses (no HTTP knowledge). `GlobalExceptionHandler` is the single place they
become HTTP, as `application/problem+json` with a stable `code` and the `requestId`; `detail` is the user-facing
message (`error` repeats it for older clients). Unexpected exceptions return a generic 500 and are logged with the
request id; internals are never returned.

## Security

Stateless bearer JWT (HS256) validated by Spring Security's resource server; everything except `/api/v1/auth/**` and
health needs a token. OTP codes are stored only as HMACs under a key *derived from* (not equal to) the signing key.
Unauthenticated endpoints are rate limited per client IP. CORS origins come from `CORS_ALLOWED_ORIGINS`.
The client address is only trustworthy if the proxy in front is: nginx overwrites `X-Forwarded-For` with the address it saw,
and Tomcat (`server.forward-headers-strategy=native`) reads that header from the right, skipping its own trusted proxies
(private ranges by default; widen with `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` if the proxy has a public address). Reading
it from the left would let a client spoof its identity and bypass the limiter; `ClientAddressApiTest` runs a real server to
prove it cannot.
With `SPRING_PROFILES_ACTIVE=prod` the app refuses to start unless real email is enabled and CORS is not `*`.

## Data

Flyway owns the schema, with one migration folder per database (`db/migration/sqlite`, `db/migration/postgresql`); Hibernate never alters it. `V1` baselines the original
schema (idempotent, so existing databases are adopted), `V2` adds foreign keys, indexes and `@Version` columns. Later migrations add columns (`upi_id`: SQLite `V3`, PostgreSQL `V2`). `idempotency_key` (SQLite `V4`, PostgreSQL `V3`) holds replayable responses for 24 hours. `deleted_at` on `app_user` (SQLite `V5`, PostgreSQL `V4`) marks a deleted account.
Concurrent edits to a group or expense are rejected with 409 instead of silently overwriting. Balances are computed
with four aggregate queries however many groups are listed. SQLite is the local default. PostgreSQL (Neon) is selected with
`SPRING_PROFILES_ACTIVE=postgres` (see `.env.example`); that profile turns on Hibernate schema validation (SQLite's
`INTEGER` keys trip the validator, so it is off there), tunes the pool for Neon's suspend/resume, and can migrate over a
direct connection while the app uses the pooled one. The auth, group and ledger API tests also run against a real
in-process PostgreSQL (`Postgres*ApiTest`), so both migration sets and the queries are exercised on every build.

## Backups

`POST /api/v1/admin/backups` writes a gzip'd, `psql`-loadable data dump to `BACKUP_DIR` (`GET` lists them). It uses
PostgreSQL's COPY protocol inside one read-only `REPEATABLE READ` transaction (a consistent snapshot, no `pg_dump`
binary needed), keeps the newest `BACKUP_KEEP`, and writes files owner-only. Sign-in codes are not included. The endpoint
requires `X-Admin-Token` = `ADMIN_API_TOKEN` (a separate shared secret; unset means the endpoint returns 404).

Restore is deliberately **not** an HTTP endpoint: it needs shell access. Stop other instances, then:

```
java -jar app.jar --app.restore.file=data/backups/splitbuys-<timestamp>.sql.gz --app.restore.confirm=true --server.port=0
```

It refuses incomplete files and a schema-version mismatch, takes a `pre-restore` safety backup if there is data to
replace, and runs in one transaction (all or nothing). Backups on the app server's disk do not survive losing that disk;
for real disaster recovery copy them off-box (and Neon's own point-in-time restore covers the database itself).

## Deployment

Production runs the jar under systemd on a small droplet behind nginx, with PostgreSQL on Neon. The unit, env template, nginx
config, one-time hardening script and a deploy script that rolls back by itself are in [`/deploy`](../deploy/README.md), together
with what was learned about memory sizing and client-address handling.

## Pagination

`GET .../expenses` and `GET .../settlements` return `{items, nextCursor}`, newest first (`?limit=` 1-100, default 50;
`?cursor=` from the previous page). Keyset ("seek") pagination on `(created_at, id)`, not offsets: rows added while someone
is paging cannot repeat or skip anything, and the cost of a page does not grow with how far back it is. `id` breaks ties
between rows with the same timestamp. It is done in two steps (page the ids in SQL, then load the rows with their shares),
because a SQL `LIMIT` combined with a collection fetch makes Hibernate page in memory, which loads the whole history.
Group totals come from the balances endpoint (`totalSpentPaise`), so clients need not download everything to add it up.

## UPI IDs

A user can store a UPI ID (`PUT/DELETE /api/v1/me/upi`, validated and lower-cased server side). It is private: it is
returned only to its owner (`ProfileDto`, never the shared `UserDto`) and in the settle-up plan only to the person who has to
pay it. Nobody else in the group sees it. The app shows it with a copy button; the payer pays in their own UPI app and then
records the settlement, because the app cannot confirm a payment. There is deliberately no `upi://pay` link: Kotak, Samsung
Money and GPay refuse payments started from a link to a personal UPI ID, so it only produced error screens.

## Push notifications

Opt-in, per device. A browser subscribes with the server's public VAPID key and registers the subscription
(`POST /api/v1/push/subscriptions`); `GET /api/v1/push/config` tells the app whether the feature is on. With no
`VAPID_*` settings it is off, and with a partial set the app refuses to start.

- **Trigger.** `LedgerService` publishes `ExpenseAdded` and `PaymentRecorded` (plain records in `common.event`) inside its
  transaction. `push.PushNotifier` handles them `AFTER_COMMIT`, so a rolled-back or idempotently replayed request notifies
  nobody, and hands the sending to a 2-thread pool with a 200-slot queue so a slow push service never slows a request.
  `push` depends on `common` only; the ledger does not know it exists.
- **Who is told.** Everyone in the group except the person who made the change; a payment goes only to the two people
  in it. The text is built per recipient ("you owe ₹250").
- **Delivery.** Best effort: no retries, failures are logged. A 404/410 from the push service means the device is gone and
  its row is deleted. Messages are encrypted to the device (RFC 8291, `WebPushCrypto`, JDK only, checked against the RFC's test
  vector) and authenticated with a VAPID token (RFC 8292, `Vapid`). TTL is a day.
- **SSRF.** The server POSTs to a URL the client supplies, so `PushEndpoints` accepts only https URLs on the real push
  services' hosts (Google, Apple, Mozilla, Windows), on the default port, with no credentials; redirects are never followed;
  the check runs again at send time.
- **Devices.** The endpoint is unique, so a phone used by someone else moves to them on subscribe. At most 10 per user. Account
  deletion removes them. They are not backed up: the app re-registers on every launch.
- **Not done.** Edits and deletions do not notify; there is no per-group mute; one malicious member could spam others (messages
  collapse per group on the device, but the server does not rate limit them).

## Friends

`GET /api/v1/me/friends` lists everyone who shares at least one group with the caller (`group.FriendService`), each with a
balance and the shared groups behind it (positive: they owe the caller). Balances here are per person per group, not per pair,
so the pairwise figure is taken from the settle-up plan: in each shared group, the payment the plan asks for between the two.
That is what they would really pay each other, it matches the Balances tab, and it adds up across groups; it can differ from a
naive "who paid for whom" tally because the plan routes debts through third people to need fewer payments. Only groups the caller
is in are used, so nothing about a friend's other groups is visible. Two queries' worth of work: the caller's groups with
members, then the four aggregate balance queries (the same ones the groups list uses).

## Activity

`GET /api/v1/me/activity` (`ledger.ActivityService`) is one newest-first feed of the expenses the caller paid for or has a share
in, and the payments they made or received, across the groups they are in now (leaving a group takes its history out of the
feed). Each item carries its group and, for expenses, `myNetPaise`: what it did to the caller's balance.

Two tables feed one list, so keyset pagination needs a position that says which table the last row came from:
`ActivityCursor` is `(time, type, id)`, ordered descending in that order. A page is built in three steps: take `limit + 1`
`(id, time)` pairs from each table (that is enough, since the merged page can take at most `limit` from either, and the extra
row says whether there is a next page), merge and keep `limit`, then load full rows for just those. Rows with the same time are
ordered by type, so neither table repeats or skips a row at a page boundary; the cursor turns that into an id bound per table
(everything, nothing, or "after this id"). `ActivityApiTest` forces identical timestamps to prove it, and a mutation of the
bound makes it fail.

Measured cost (`loadtest/README.md`, one laptop VM): a deep page is cheap, but the *first* page still scales with the user's total
history, because the plan reads all of the caller's expenses before taking `limit + 1`. A user in 50 groups with 10k expenses
pays about 30 ms for it; a typical user pays about 7 ms. Friends costs about 50 ms for that heavy user (the balances scan).

## Expense history and undo

Every create, edit and delete of an expense writes a row to `expense_history` in the same transaction (`ledger.HistoryLog`): who,
when, and JSON snapshots of the expense before and after (user ids only, so a later rename shows through, and nothing about a
deleted account's owner is copied). `expense_id` has no foreign key, so the history of a deleted expense outlives it; rows go
with the group. `GET /groups/{id}/history` pages the group's changes (keyset, like the other lists) and
`GET /groups/{id}/expenses/{id}/history` shows one expense's (newest 50). Saving an edit that changes nothing writes no row.

`POST /groups/{id}/history/{id}/undo` handles two cases. An **edit** can be undone only while it is still the newest change to
that expense, otherwise the undo would silently throw away a later edit (409 `CHANGED_SINCE`); it restores the earlier state and
records a `REVERTED` row. A **deletion** can be undone once (409 `ALREADY_RESTORED`): it creates the expense again, with a new
id, and the deletion row remembers which. If someone in the expense has since left the group it cannot be restored (409
`MEMBER_LEFT`). Creations cannot be undone (delete the expense instead). `undoable` on each entry tells the client whether undo
would be accepted now. History is included in database backups.

## Expense categories and spending summary

Each expense has a `category` from a short fixed list (`ExpenseCategory`: food, groceries, travel, stay, utilities, entertainment,
shopping, other), stored as a `varchar` with default `OTHER` so existing rows are valid. It is optional on a request: a new expense
without one is `OTHER`, and an edit that omits it keeps the current one, so older clients cannot wipe it. It is part of the
history snapshot, so a category change shows in the history and can be undone (snapshots written before categories existed read
back as `OTHER`). `GET /groups/{id}/summary` (`ledger.SpendingService`) returns the group's spending by category, biggest first,
with the caller's own share of each: two aggregate queries (totals, and the caller's shares), however many expenses there are.
A fixed list is deliberate: free text fragments ("food", "Food ", "dinner") and cannot be summed.

Suggesting a category is done in the web app only (`web/src/lib/suggestCategory.ts`), not on the server: while someone types a
description on a new expense the matching chip is selected for them until they tap one themselves. It trusts, in order, what the
group already filed under the same description (from the cached group data, no extra request), then keywords and short phrases,
with a one- or two-letter typo allowance for longer words. A tie or no match suggests nothing, and the server still files an
expense with no category as `OTHER`, so API clients and older app versions are unaffected.

## Payment reminders

`POST /api/v1/groups/{id}/reminders` with `{toUserId}` nudges a member who owes the caller (`ledger.ReminderService`). It is allowed
only for a payment the settle-up plan really asks for (the same plan the Balances tab shows; otherwise 409 `NOTHING_OWED`), at
most once a day per person and group, and at most 20 a day per sender (429), so it cannot be used to pester anyone. The server
works out the amount itself; the client sends nobody's money. It publishes `ReminderSent`, which `push.PushNotifier` turns into a
notification for the debtor only, after commit.

Whether the person can be reached is asked through `common.notify.DeviceReach` (implemented by `PushService`), so the ledger does
not depend on push. If they cannot (notifications off, or no registered device) nothing is recorded or sent and the response says
`delivered: false` without using up the cooldown; the app then offers a message to send by hand. The `payment_reminder` table
exists only for the cooldown and the cap (no message text) and is included in backups so restores stay consistent.

## API

Versioned under `/api/v1`. Money is integer paise. All errors are problem documents. `GET /actuator/health` is public.

## Not done yet (known gaps)

* The rate limiter is in-memory (single instance); use a gateway or Redis to scale out.
* Token revocation / refresh tokens.

## Deleting an account
`DELETE /api/v1/me` with the person's own email in the body (`account/AccountService`, one transaction): refuse while they
owe or are owed money in any group (the same rule as leaving a group, so other members' balances keep adding up), leave
every group, replace email/name/UPI ID on the user row with placeholders (the row stays because shared expenses point at
it; the real email becomes free to sign up with again), and drop pending sign-in codes and remembered requests.
Their tokens stop working through `ActiveAccountValidator`: a JWT proves who issued it, not that the account still
exists, so each request also checks the account is active. That answer is cached for 15 s (`app.auth.active-cache-seconds`)
and dropped at once on this instance when an account is closed; another instance would take up to the cache lifetime to
notice. Backups taken before a deletion still contain the old data until they are rotated out.
