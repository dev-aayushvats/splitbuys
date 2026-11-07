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
- Refuses to run with uncommitted changes, so the jar always matches a commit. Deploy from `main` after the PR is merged.
- Builds and tests (`./mvnw verify`), uploads, verifies the checksum on the server, installs the jar, restarts, and polls
  `/actuator/health/liveness` for up to 150 seconds. If the app never turns healthy it restores the previous jar by itself.
- Expect about 30 seconds of downtime on a 512 MB droplet.
- `--dry-run` prints the plan without touching anything. `--skip-build` reuses the jar already in `backend/target`.
- A migration runs when the new jar starts (Flyway). Migrations here are additive, so rolling the jar back is safe; it is not a
  database rollback. Take a backup first when a release adds one: `POST /api/v1/admin/backups` (see `backend/ARCHITECTURE.md`).

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
