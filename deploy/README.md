# Deploying the backend

How the backend runs in production: a Spring Boot jar under systemd on a small droplet, nginx in front with Let's Encrypt,
PostgreSQL on Neon, and the web app on Cloudflare Workers/Pages calling the API directly (cross-origin).

```
browser ──https──> api.<your domain>  (DNS goes straight to the droplet)
                      │ nginx :443  (TLS, overwrites X-Forwarded-For)
                      ▼
                  127.0.0.1:8080  myapp.service (user springapp)  ──>  Neon PostgreSQL
```

| File | What it is | Lives at |
|---|---|---|
| `myapp.service` | systemd unit | `/etc/systemd/system/myapp.service` |
| `application.env.example` | secrets, CORS and JVM flags (copy, fill in, `chmod 600`) | `/var/opt/splitbuys-back/application.env` |
| `nginx-api.conf` | reverse proxy for the API host | `/etc/nginx/sites-available/splitbuys` |
| `nginx-tuning.conf` | hide the version, gzip JSON | `/etc/nginx/conf.d/tuning.conf` |
| `server-setup.sh` | one-time hardening (firewall, swappiness, journal) | run on the server |
| `deploy.sh` | build, upload, swap in, health-check, roll back | run from your machine |

## First-time setup (commands are the standard ones; they were not re-run when this folder was written)
1. User and folder: `useradd --system --home /var/opt/splitbuys-back --shell /usr/sbin/nologin springapp`, then
   `install -d -o springapp -g springapp /var/opt/splitbuys-back`.
2. Java 17: `apt install openjdk-17-jre-headless`.
3. Swap (a 512 MB droplet needs it as a safety net): `fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile`
   and add `/swapfile none swap sw 0 0` to `/etc/fstab`.
4. Copy `application.env.example` to `/var/opt/splitbuys-back/application.env`, fill it in, `chown springapp:springapp`, `chmod 600`.
5. Install the unit: copy `myapp.service`, then `systemctl daemon-reload && systemctl enable myapp`.
6. nginx: copy `nginx-api.conf` (edit the host name) and `nginx-tuning.conf`, `nginx -t && systemctl reload nginx`, then
   `certbot --nginx -d api.<your domain>`.
7. Hardening: `ssh root@host 'bash -s' < deploy/server-setup.sh`, and **follow its last instruction** (confirm a second SSH session, then cancel the dead-man switch).
8. First deploy: see below. On the Cloudflare side set the web build variable `VITE_API_URL=https://api.<your domain>`.

## Deploying
```
SSH_TARGET=root@your.host SSH_KEY=~/.ssh/your_key API_URL=https://api.<your domain> deploy/deploy.sh
```
- Refuses to run unless the checkout is **clean and exactly `origin/main`** (it fetches first). A jar is built from whatever is
  checked out, so a stale local `main` would otherwise quietly ship old code. If you are behind it tells you to
  `git checkout main && git pull`; if the commit is not on `origin/main` it tells you to merge first.
  `ALLOW_NON_MAIN=1` deploys another commit on purpose (a hotfix); `ALLOW_DIRTY=1` allows uncommitted changes. If the fetch
  fails (offline) it refuses, because it cannot tell whether the checkout is current.
- Builds and tests (`./mvnw verify`), uploads, verifies the checksum on the server, installs the jar, restarts, and polls
  `/actuator/health/liveness` for up to 150 seconds. If the app never turns healthy it restores the previous jar by itself.
- Expect about 30 seconds of downtime on a 512 MB droplet.
- `--dry-run` prints the plan without touching anything. `--skip-build` reuses the jar already in `backend/target`.
- A migration runs when the new jar starts (Flyway). Migrations here are additive, so rolling the jar back is safe; it is not a
  database rollback. Take a backup first when a release adds one: `POST /api/v1/admin/backups` (see `backend/ARCHITECTURE.md`).

## Deploying from GitHub Actions

`.github/workflows/deploy-backend.yml` runs `deploy.sh` when a code change under `backend/` reaches `main` (markdown and
`backend/loadtest/` are ignored), or by hand from the Actions tab. It uses the same script, so the build, tests, checksum,
health check and automatic rollback are identical to a manual deploy, and the script still refuses to ship anything that is not
exactly `origin/main`.

One-time setup (nothing here is done by the workflow itself):

1. **A dedicated key.** `ssh-keygen -t ed25519 -f deploy_key -N '' -C github-actions-deploy`. Add the public half to the
   droplet's `~/.ssh/authorized_keys` prefixed with `no-port-forwarding,no-agent-forwarding,no-pty,no-X11-forwarding ` so it can
   only run commands. Keep it separate from your own key so it can be revoked on its own. (The deploy needs root to restart the
   service, so this key is root-equivalent: treat the secret accordingly.)
2. **Repository secrets** (Settings, Secrets and variables, Actions), ideally as *environment* secrets of `production`:
   `DEPLOY_SSH_KEY` (the private half), `DEPLOY_SSH_TARGET` (`root@68.183.84.234`), `DEPLOY_KNOWN_HOSTS` (output of
   `ssh-keyscan -t ed25519 68.183.84.234`, checked against the host's real fingerprint, so the runner cannot be sent to an impostor).
3. **Environment.** Settings, Environments, create `production`, add yourself as a required reviewer, and limit it to the `main`
   branch. Every deploy then waits for a click.

Concurrent runs queue (never cancel), because cancelling mid-swap is the one dangerous moment. Rotate the key by replacing the
`authorized_keys` line and the secret.

## The web app on Cloudflare
Cloudflare builds the `web/` folder from git. `web/public/_headers` ships into the build and sets the security headers, the
cache rules and a strict Content-Security-Policy. Two things must stay in step:
- `VITE_API_URL` (the Cloudflare build variable) and the `connect-src` host in `web/public/_headers`. If the API host changes,
  change both, or the browser blocks every API call. A build without `VITE_API_URL` falls back to `http://localhost:8080`,
  which the CSP also blocks.
- The API's `CORS_ALLOWED_ORIGINS` must list the web origin(s).

After changing the CSP, test it in a real browser: a policy that is too strict breaks the page silently.

## Rolling back by hand
```
mv /var/opt/splitbuys-back/app.jar.prev /var/opt/splitbuys-back/app.jar && systemctl restart myapp
```
Back up `myapp.service`, `application.env` and the nginx files before editing them, and keep the copy off the box too.

## Checking on it
```
systemctl status myapp --no-pager          # state, memory, recent log lines
journalctl -u myapp -f                     # follow the (JSON) log
free -m; swapon --show                     # RAM and swap
grep -E 'VmRSS|VmSwap' /proc/$(systemctl show -p MainPID --value myapp)/status
dmesg | grep -i 'killed process'           # the kernel's OOM killer
ss -tlnp                                   # only 22, 80, 443 public; 8080 on 127.0.0.1 only
```

## Things learned the hard way
- **Memory.** On a 512 MB droplet the app measured roughly 250-290 MB resident under load. With a 400 MB limit and a
  100 MB metaspace cap it crashed (`OutOfMemoryError: Metaspace`); Spring plus Hibernate need more class space than that.
  Do not copy tight JVM caps without running them. If you see sustained swapping or restarts, move to a 1 GB droplet.
- **Client address and rate limiting.** nginx overwrites `X-Forwarded-For` and the app reads it from the right
  (`server.forward-headers-strategy=native`). With nginx appending instead, a client could choose its own identity and
  bypass the per-IP limit on sign-in. Do not set `CLIENT_IP_HEADER` unless the API is only reachable through Cloudflare.
- **Never expose 8080.** The app is bound to localhost and the firewall does not allow it. Reaching the app directly skips
  nginx and TLS.
- **`EnvironmentFile` has no trailing comments.** Everything after `=` is the value.
- **CORS in production** lists only real web origins (not `*`, not `localhost`).

## Push notifications (optional)

Off until the three `VAPID_*` settings exist in `application.env`.

1. On your own machine: `java deploy/GenerateVapidKeys.java` prints a public key, a private key and a subject line.
2. Put all three in `/var/opt/splitbuys-back/application.env` (mode 600; the private key is a secret; use a real contact in
   `VAPID_SUBJECT`), then `systemctl restart myapp`.
3. Deploy the web app (the service worker is part of it). People then turn notifications on from Profile.

Changing the key pair later invalidates every subscription, and people must turn notifications on again. iPhones only get
notifications from the Home Screen app (iOS 16.4 or later).
