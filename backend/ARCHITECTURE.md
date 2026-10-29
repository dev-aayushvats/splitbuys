# Backend architecture

Spring Boot 4 · Java 17 · Spring Data JPA · Flyway · Spring Security (JWT resource server) · SQLite by default.

## Shape: package-by-feature, layered inside each feature

```
com.splitbuys.backend
├── common/          cross-cutting, depends on no feature
│   ├── error/         DomainException + NotFound / InvalidRequest / Conflict / Unauthorized / RateLimited / DependencyFailure
│   ├── web/           GlobalExceptionHandler (RFC 9457 problems), RequestIdFilter, ProblemJson
│   └── security/      SecurityConfig, rate limiting, @CurrentUser, prod safety check
├── user/            accounts           UserService is the only door in
├── auth/            email OTP + access tokens
├── group/           groups, membership, invites          declares LedgerPort
├── ledger/          expenses, payments, balances         implements group.LedgerPort
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
With `SPRING_PROFILES_ACTIVE=prod` the app refuses to start unless real email is enabled and CORS is not `*`.

## Data

Flyway owns the schema (`src/main/resources/db/migration`); Hibernate never alters it. `V1` baselines the original
schema (idempotent, so existing databases are adopted), `V2` adds foreign keys, indexes and `@Version` columns.
Concurrent edits to a group or expense are rejected with 409 instead of silently overwriting. Balances are computed
with four aggregate queries however many groups are listed. Set `JPA_DDL_AUTO=validate` on PostgreSQL (SQLite's
`INTEGER` keys trip Hibernate's validator).

## API

Versioned under `/api/v1`. Money is integer paise. All errors are problem documents. `GET /actuator/health` is public.

## Not done yet (known gaps)

* List endpoints are not paginated.
* The rate limiter is in-memory (single instance); use a gateway or Redis to scale out.
* Token revocation / refresh tokens.
