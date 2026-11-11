#!/usr/bin/env bash
# Friends / Activity load test: fresh database -> seed.sql + seed-heavy.sql -> query plans -> k6 (feeds.js).
#   ./run-feeds.sh                     steps 10,25,50 visits/s
#   RATES=25,50,100 ./run-feeds.sh
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p results
dc="docker compose -f docker-compose.yml"

$dc down -v --remove-orphans >/dev/null 2>&1 || true
$dc up -d --build db backend
echo "waiting for the backend to be healthy..."
until [ "$($dc ps backend --format '{{.Health}}')" = "healthy" ]; do sleep 3; done

for f in seed.sql seed-heavy.sql; do $dc exec -T db psql -q -U splitbuys -d splitbuys -v ON_ERROR_STOP=1 < $f; done
echo "seeded."
$dc exec -T db psql -U splitbuys -d splitbuys -At -c "select 'users '||count(*) from app_user union all select 'groups '||count(*) from expense_group union all select 'expenses '||count(*) from expense union all select 'shares '||count(*) from expense_share union all select 'payments '||count(*) from settlement"

# the SQL the activity feed runs (first page, 30 rows + 1), for a heavy and a typical user
{
for u in 201 1; do
  echo "=== expenses involving user $u (first page) ==="
  $dc exec -T db psql -U splitbuys -d splitbuys -c "EXPLAIN (ANALYZE, BUFFERS) select e.id, e.created_at from expense e where e.group_id in (select expense_group_id from group_member where members_id = $u) and (e.payer_id = $u or exists (select 1 from expense_share s where s.expense_id = e.id and s.user_id = $u)) order by e.created_at desc, e.id desc limit 31"
done
} > results/explain.txt 2>&1

( while true; do date +%T; docker stats --no-stream --format '{{.Name}} cpu={{.CPUPerc}} mem={{.MemUsage}}' \
    splitbuys-loadtest-backend-1 splitbuys-loadtest-db-1; sleep 5; done ) > results/stats-feeds.txt 2>&1 &
sampler=$!
trap 'kill $sampler 2>/dev/null || true' EXIT

# k6 exits 99 when a threshold fails; the tables below are still wanted, so do not stop here
$dc --profile load run --rm k6 run --summary-export=/results/summary-feeds.json /feeds.js | tee results/k6-feeds-output.txt || true
python3 summarize.py results/summary-feeds.json | tee results/feeds-tables.md
