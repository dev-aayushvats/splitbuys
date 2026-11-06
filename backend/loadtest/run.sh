#!/usr/bin/env bash
# Fresh database -> seed -> ramped k6 run, sampling container CPU/memory so the bottleneck is visible.
#   ./run.sh                      defaults: backend 1 CPU / 1 GB, pool 10, steps 25..150 req/s
#   RATES=50,100,200 POOL_SIZE=20 BACKEND_CPUS=2 ./run.sh
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p results
dc="docker compose -f docker-compose.yml"

$dc down -v --remove-orphans >/dev/null 2>&1 || true
$dc up -d --build db backend
echo "waiting for the backend to be healthy..."
until [ "$($dc ps backend --format '{{.Health}}')" = "healthy" ]; do sleep 3; done

$dc exec -T db psql -q -U splitbuys -d splitbuys -v ON_ERROR_STOP=1 < seed.sql
echo "seeded."

# resource samples every 5s for the whole run
( while true; do date +%T; docker stats --no-stream --format '{{.Name}} cpu={{.CPUPerc}} mem={{.MemUsage}}' \
    splitbuys-loadtest-backend-1 splitbuys-loadtest-db-1; sleep 5; done ) > results/stats.txt 2>&1 &
sampler=$!
trap 'kill $sampler 2>/dev/null || true' EXIT

$dc --profile load run --rm k6 | tee results/k6-output.txt
