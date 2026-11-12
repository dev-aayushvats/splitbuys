#!/usr/bin/env bash
# Spending / summary / search latency: fresh database -> seed.sql + seed-heavy.sql -> query plans -> k6 (reads.js).
#   ./run-reads.sh              Postgres JIT on (the default)
#   PG_JIT=off ./run-reads.sh   JIT off (what production runs now)
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

# the SQL behind each endpoint for a heavy user (201) and a heavy group (41)
{
echo "=== group search: first page, text 'expense 1' ==="
$dc exec -T db psql -U splitbuys -d splitbuys -c "EXPLAIN (ANALYZE, BUFFERS) select e.id from expense e where e.group_id = 41 and lower(e.description) like '%expense 1%' escape '!' and e.category in ('FOOD','GROCERIES','TRAVEL','STAY','UTILITIES','ENTERTAINMENT','SHOPPING','OTHER') order by e.created_at desc, e.id desc limit 31"
echo "=== activity search: first page, text 'expense 1' ==="
$dc exec -T db psql -U splitbuys -d splitbuys -c "EXPLAIN (ANALYZE, BUFFERS) select e.id, e.created_at from expense e where e.group_id in (select expense_group_id from group_member where members_id = 201) and (e.payer_id = 201 or exists (select 1 from expense_share s where s.expense_id = e.id and s.user_id = 201)) and lower(e.description) like '%expense 1%' escape '!' and e.category in ('FOOD','GROCERIES','TRAVEL','STAY','UTILITIES','ENTERTAINMENT','SHOPPING','OTHER') order by e.created_at desc, e.id desc limit 31"
echo "=== activity search: category FOOD (matches nothing) ==="
$dc exec -T db psql -U splitbuys -d splitbuys -c "EXPLAIN (ANALYZE, BUFFERS) select e.id, e.created_at from expense e where e.group_id in (select expense_group_id from group_member where members_id = 201) and (e.payer_id = 201 or exists (select 1 from expense_share s where s.expense_id = e.id and s.user_id = 201)) and lower(e.description) like '%' escape '!' and e.category in ('FOOD') order by e.created_at desc, e.id desc limit 31"
} > results/explain-reads.txt 2>&1

$dc --profile load run --rm k6 run --summary-export=/results/summary-reads.json /reads.js | tee results/k6-reads-output.txt || true
python3 - <<'PY' | tee results/reads-tables.md
import json
m = json.load(open("results/summary-reads.json"))["metrics"]
d = "http_req_duration"
for w in ("typical", "heavy"):
    print(f"\n{w}, one request at a time (100 visits)\n\n| endpoint | median | p95 | max |\n|---|---|---|---|")
    for e in ("spending", "group_summary", "group_search", "group_search_category", "activity", "activity_search", "activity_search_category"):
        v = m.get(f"{d}{{phase:latency,who:{w},endpoint:{e}}}")
        print(f"| {e} | {v['med']:.1f} ms | {v['p(95)']:.1f} ms | {v['max']:.0f} ms |" if v else f"| {e} | n/a | n/a | n/a |")
print("\nfailed requests:", m.get("http_req_failed", {}).get("value", "n/a"))
PY
