# SplitBuys

Splitwise-style expense splitting. `backend/` is Spring Boot, `frontend/` is Flutter (Android + web).

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

**Frontend**
```
cd frontend
cp env/dev.json.example env/dev.json     # optional, defaults work for local web
flutter run -d chrome --web-port 5555 --dart-define-from-file=env/dev.json
```
Android emulator: the API defaults to `http://10.0.2.2:8080`.

## Backend architecture
Package-by-feature (`user`, `auth`, `group`, `ledger`, `common`) with the layering and rules enforced by tests — see
[backend/ARCHITECTURE.md](backend/ARCHITECTURE.md). API is under `/api/v1`; run `./mvnw test` in `backend/`.

## Config
All backend settings are environment variables, listed in `backend/.env.example`. Real env vars override `.env`.
Frontend values (`API_URL`, `APP_URL`) are build-time `--dart-define`s and are not secret.

## Web app (React PWA) — `web/`
Phone-first React + Tailwind rewrite of the Flutter frontend, installable as a PWA.
```
cd web
npm install
npm run dev        # http://localhost:5555, API defaults to http://localhost:8080 (set VITE_API_URL to change)
npm run build      # production build in dist/ (service worker + manifest included)
npm test
```
