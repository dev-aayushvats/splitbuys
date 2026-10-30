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

**PostgreSQL / Neon:** the backend uses a local SQLite file by default. To use Neon, set `spring.profiles.active=postgres` (in `.env`; `SPRING_PROFILES_ACTIVE` if it is a real environment variable) and
the `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` values described in `backend/.env.example`; the schema is
created automatically on first start.

**Database backups (PostgreSQL):** set `ADMIN_API_TOKEN` in `backend/.env`, then
`curl -X POST -H "X-Admin-Token: $TOKEN" http://localhost:8080/api/v1/admin/backups` writes a dump to `backend/data/backups/`.
Restore is a command-line operation; see [backend/ARCHITECTURE.md](backend/ARCHITECTURE.md#backups).

## Docker
Both services have their own image, and `docker-compose.yml` at the root brings them up together:
```
docker compose up --build        # then open http://localhost:5555
```
| Service | Image | Notes |
|---|---|---|
| `web` | nginx (non-root) serving the built PWA | Proxies `/api` to the backend, so the browser sees one origin (no CORS). Blocks `/api/v1/admin/` entirely. |
| `backend` | Spring Boot on a JRE (non-root) | Reads `backend/.env` (Neon, mail, JWT secret, admin token). Published on `127.0.0.1:8080` only. |

Build or run them separately:
```
docker build -t splitbuys-backend ./backend      # standalone: SQLite on the /data volume unless DATABASE_* are set
docker build -t splitbuys-web ./web              # expects a host named "backend" on port 8080 (see web/nginx/default.conf)
```
- **Config:** compose knobs (`WEB_PORT`, `PUBLIC_URL`, `SPRING_PROFILES_ACTIVE`) are in the root `.env.example`; the backend's own settings stay in `backend/.env`.
- **Admin / backups:** call the backend on localhost, not through the web container:
  `curl -X POST -H "X-Admin-Token: $TOKEN" http://127.0.0.1:8080/api/v1/admin/backups`. Backups are written to the `backend-data`
  volume (`docker compose cp backend:/data/backups ./backups` copies them out). Restore:
  `docker compose run --rm backend --app.restore.file=/data/backups/<file>.sql.gz --app.restore.confirm=true --server.port=0`.
- **Production:** use `SPRING_PROFILES_ACTIVE=prod,postgres` with real mail and a real `PUBLIC_URL`, and put TLS in front (the PWA only installs over HTTPS).

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
