# SplitBuys

Splitwise-style expense splitting. `backend/` is Spring Boot; `web/` is a phone-first React + Tailwind PWA.

## Run locally

**Backend**
```
cd backend
cp .env.example .env     # then set APP_JWT_SECRET (openssl rand -base64 48)
./mvnw spring-boot:run   # http://localhost:8080
```
With `MAIL_ENABLED=false` (the default) sign-in codes are printed in the server console.

**Real email codes (Gmail)**: turn on 2-Step Verification, create an App Password at
https://myaccount.google.com/apppasswords, then in `.env` set `MAIL_ENABLED=true`, `MAIL_USERNAME`
(your Gmail address) and `MAIL_PASSWORD` (the 16-letter app password). Other SMTP providers: also set
`MAIL_HOST`, `MAIL_PORT` and `MAIL_FROM`.

**Web app** (see below): `cd web && npm install && npm run dev` → http://localhost:5555

**PostgreSQL / Neon:** the backend uses a local SQLite file by default. To use Neon, set `SPRING_PROFILES_ACTIVE=postgres` and
the `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` values described in `backend/.env.example`; the schema is
created automatically on first start.

## Backend architecture
Package-by-feature (`user`, `auth`, `group`, `ledger`, `common`) with the layering and rules enforced by tests — see
[backend/ARCHITECTURE.md](backend/ARCHITECTURE.md). API is under `/api/v1`; run `./mvnw test` in `backend/`.

## Config
All backend settings are environment variables, listed in `backend/.env.example`. Real env vars override `.env`.
The web app's only setting is `VITE_API_URL` (build-time, not secret); see `web/.env.example`.

## Web app (React PWA) — `web/`
Phone-first React + Tailwind app, installable as a PWA. (It replaced the original Flutter client, which was removed; that code is in git history before the commit "remove the Flutter client".)
```
cd web
npm install
npm run dev        # http://localhost:5555, API defaults to http://localhost:8080 (set VITE_API_URL to change)
npm run build      # production build in dist/ (service worker + manifest included)
npm test
```
