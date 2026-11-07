#!/usr/bin/env bash
# Build the backend jar from committed code, upload it, swap it in, and roll back automatically if it is not healthy.
#
#   SSH_TARGET=root@your.host SSH_KEY=~/.ssh/your_key deploy/deploy.sh [--dry-run] [--skip-build]
#
# Optional: APP_DIR (default /var/opt/splitbuys-back), SERVICE (default myapp), API_URL (public URL to check afterwards),
#           ALLOW_DIRTY=1 (deploy even with uncommitted changes; normally refused so the jar matches a commit).
set -euo pipefail

DRY=0
BUILD=1
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY=1 ;;
    --skip-build) BUILD=0 ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

: "${SSH_TARGET:?set SSH_TARGET, e.g. root@your.host}"
APP_DIR="${APP_DIR:-/var/opt/splitbuys-back}"
SERVICE="${SERVICE:-myapp}"
API_URL="${API_URL:-}"

# an array, not a string, so the options survive word splitting
SSH_OPTS=(-o BatchMode=yes -o ConnectTimeout=15)
if [[ -n "${SSH_KEY:-}" ]]; then
  SSH_OPTS+=(-i "$SSH_KEY" -o IdentitiesOnly=yes)
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$ROOT/backend/target/backend-0.0.1-SNAPSHOT.jar"

run() { if [[ $DRY -eq 1 ]]; then echo "[dry-run] $*"; else "$@"; fi; }

COMMIT="$(git -C "$ROOT" rev-parse --short HEAD)"
if [[ -n "$(git -C "$ROOT" status --porcelain)" && "${ALLOW_DIRTY:-0}" != "1" ]]; then
  echo "Uncommitted changes: refusing to deploy a jar that does not match a commit (ALLOW_DIRTY=1 to override)." >&2
  exit 1
fi
echo "Deploying commit $COMMIT to $SSH_TARGET:$APP_DIR"

if [[ $BUILD -eq 1 ]]; then
  echo "== build and test"
  run bash -c "cd '$ROOT/backend' && ./mvnw -B verify"
fi
if [[ $DRY -eq 0 && ! -f "$JAR" ]]; then
  echo "jar not found: $JAR" >&2
  exit 1
fi

if [[ $DRY -eq 1 ]]; then
  echo "[dry-run] sha256 of $JAR"
  echo "[dry-run] scp jar -> $SSH_TARGET:$APP_DIR/app.jar.new"
  echo "[dry-run] on the server: verify checksum, keep app.jar as app.jar.prev, install the new jar, restart $SERVICE,"
  echo "[dry-run]                poll /actuator/health/liveness for up to 150s, restore app.jar.prev if it never turns healthy"
  [[ -n "$API_URL" ]] && echo "[dry-run] check $API_URL/actuator/health"
  exit 0
fi

SUM="$(shasum -a 256 "$JAR" | awk '{print $1}')"
echo "== upload ($SUM)"
scp -q "${SSH_OPTS[@]}" "$JAR" "$SSH_TARGET:$APP_DIR/app.jar.new"

echo "== swap in and verify (rolls back by itself on failure)"
ssh "${SSH_OPTS[@]}" "$SSH_TARGET" bash -s -- "$APP_DIR" "$SERVICE" "$SUM" <<'REMOTE'
set -u
D="$1"; SVC="$2"; WANT="$3"
GOT="$(sha256sum "$D/app.jar.new" | awk '{print $1}')"
if [ "$GOT" != "$WANT" ]; then
  echo "CHECKSUM MISMATCH: nothing changed"; rm -f "$D/app.jar.new"; exit 1
fi
rollback() {
  echo "!! not healthy: restoring the previous jar"
  mv -f "$D/app.jar.prev" "$D/app.jar"
  systemctl restart "$SVC"
  exit 1
}
[ -f "$D/app.jar" ] && mv "$D/app.jar" "$D/app.jar.prev"
mv "$D/app.jar.new" "$D/app.jar"
chown "$(stat -c %U "$D")":"$(stat -c %G "$D")" "$D/app.jar"
chmod 644 "$D/app.jar"
systemctl restart "$SVC"
ok=0
for i in $(seq 1 50); do
  code="$(curl -s -m 3 -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/actuator/health/liveness)"
  if [ "$code" = "200" ]; then ok=1; echo "healthy after about $((i * 3))s"; break; fi
  sleep 3
done
[ "$ok" = 1 ] || rollback
sleep 10
echo "errors in the log since the restart: $(journalctl -u "$SVC" --since '-1min' --no-pager 2>/dev/null | grep -c '"level":"ERROR"')"
grep -E 'VmRSS|VmSwap' "/proc/$(systemctl show -p MainPID --value "$SVC")/status"
echo "$SVC: $(systemctl is-active "$SVC")"
REMOTE

if [[ -n "$API_URL" ]]; then
  echo "== public check"
  curl -s -m 15 -o /dev/null -w "$API_URL/actuator/health -> HTTP %{http_code}\n" "$API_URL/actuator/health"
fi
echo "Done: $COMMIT is live. The previous jar is kept as $APP_DIR/app.jar.prev for a manual rollback."
