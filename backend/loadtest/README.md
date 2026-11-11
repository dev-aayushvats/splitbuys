# Load test

Repeatable load-test rig: Postgres 16 and the backend (each capped at **1 vCPU**, backend 1 GB) plus k6, all in Docker.

```
./run.sh                                  # steps 25,50,75,100,150 req/s
RATES=50,150,200,250 ./run.sh             # custom steps (put a low warm-up step first, see below)
BACKEND_CPUS=2 POOL_SIZE=20 ./run.sh      # change the resource budget
```

Needs only Docker. It builds the backend image, starts a fresh database, seeds it (200 users, 40 groups, 8,000 expenses
with 40,000 shares), then runs k6. Results land in `results/` (`summary.json`, `k6-output.txt`, `stats.txt` with CPU and
memory sampled every 5 s).

## What it does
- **Open model** (`ramping-arrival-rate`): requests arrive at the target rate whether or not the server keeps up, like real
  users. A fixed-VU test slows its own load down when the server slows and hides saturation.
- **Traffic mix**: 35% list groups, 25% balances, 15% list expenses, 5% group detail, 20% add expense.
- **Auth**: k6 signs HS256 tokens with the test secret, so no OTP flow is needed for 200 users.
- Every request is tagged with its load step and endpoint, so latency is read per step.

## Results (backend 1 vCPU / pool 10, Postgres 1 vCPU, same Docker VM as k6: 4 CPUs total)

Warmed-up run, `RATES=50,150,200,250`, 37,162 requests, **0 errors, 0 dropped iterations**:

| Step (req/s) | median | p95 | max |
|---|---|---|---|
| 50 (warm-up) | 6.5 ms | 58 ms | 827 ms |
| 150 | 4.4 ms | 188 ms | 1.6 s |
| 200 | 4.1 ms | 39 ms | 813 ms |
| 250 | 5.3 ms | 80 ms | 370 ms |

By endpoint over the whole run, p95: add expense 69 ms, balances 79 ms, list groups 83 ms, list expenses 102 ms,
group detail 59 ms.

Where it breaks:
- **Targeting 300 req/s** (ramped 30 → 300, first run): saturated. About 145 req/s served, 693 iterations dropped,
  p95 2.2 s. Backend and database were both at about 100% of their 1-vCPU limit.
- **Cold start**: jumping straight to 150 req/s with no warm-up (`RATES=150,200,250,300`) put the backend at 100% CPU
  immediately and p95 at about 5 s. While the JVM is still JIT-compiling, one core cannot keep up. Always put a low
  warm-up step first.

## Before / after cursor pagination (same rig, same `RATES=50,150,200,250`, same 37,162 requests, 0 errors)

| | before | after |
|---|---|---|
| p95, whole run per endpoint: list expenses | 102 ms | 7.7 ms |
| p95, balances / add expense | 79 / 69 ms | 5.9 / 6.4 ms |
| p95 at the 250 req/s step | 80 ms | 5.0 ms |
| median at the 250 req/s step | 5.3 ms | 3.2 ms |
| response data received | 535 MB | 226 MB |
| peak backend CPU (of 100%) | 108% | 69% |
| peak database CPU (of 100%) | 93% | 52% |

`GET /expenses` used to return a group's entire history, so each request got more expensive as writes accumulated during
the run. It now returns one page (default 50) via a keyset cursor. The database CPU still creeps up (4% to 47% over the run)
because the balance aggregates scan the whole history; that is the next thing to fix (cached or materialised balances).
Two separate runs on a laptop, so small differences are noise, but a 10x gap in p95 is not.

## Reading the results honestly
- Everything shares one 4-CPU Docker VM on a laptop (k6 included), so absolute numbers are indicative, not capacity
  planning. The shape (sustained rate, where the knee is, what saturates) is what carries over.
- Before pagination the database CPU climbed from about 5% to 94% over the clean run while the backend stayed flat:
  `GET /expenses` was unpaginated, so per-request cost grew with data. Pagination fixed most of that (see above).
- Not measured: a network between app and database (Neon is remote, so real latencies are higher), the OTP/auth
  endpoints, and long soak tests.

## Friends and Activity (`./run-feeds.sh`)

`seed-heavy.sql` adds 10 heavy users (50 groups, ~209 friends, ~10,000 expenses each) to the standard seed. `feeds.js` mixes
90% typical / 10% heavy visits (friends + activity + next page); steps are visits/s (default 25,50,75,100,150 via compose).
Same caps as above: 1 vCPU each for backend and Postgres, k6 in the same 4-CPU Docker VM. `./summarize.py` builds the tables.

Valid run (Postgres JIT on, the default), 0 failed requests:

| One request at a time | median | p95 |
|---|---|---|
| typical friends / activity | 7.0 / 7.6 ms | 52 / 51 ms |
| heavy friends | 49.6 ms | 63 ms |
| heavy activity (first / next page) | 29.6 / 23.5 ms | 104 / 107 ms |
| heavy feed, pages 101+ | 15.4 ms | 28 ms |

| Under load (visits/s) | typical p95 | heavy p95 |
|---|---|---|
| 25 | 15 ms | 234 ms |
| 50 | 41 ms | 333 ms |
| 75 | 76 ms | 645 ms |
| 100 | 944 ms | 1.9 s |
| 150 | 10.3 s (collapsed, 1,528 dropped) | 10.7 s |

The knee is roughly 75-100 visits/s (about 225-300 requests/s) on 1 vCPU each.

What it showed:
- Typical users are negligible. Heavy users cost ~50 ms (Friends, the balances scan) and ~30 ms (Activity first page).
- Deep pages are cheap; the first page scales with the user's total history (`explain-jit-on.txt`: 10,000 rows read to return 31).
- Postgres JIT compiled that heavy query for ~69 ms of its 267 ms, because its estimated cost (265k) exceeded `jit_above_cost`
  (100k). Neon runs with `jit = on` and the default thresholds (checked read-only), so this applies in production.

JIT off (`PG_JIT=off ./run-feeds.sh`, one run on a quiet machine, 0 failed requests, 95 dropped iterations), against the JIT-on run above:

| | JIT on | JIT off |
|---|---|---|
| heavy first-page query, `EXPLAIN ANALYZE` | 267 ms | 11 ms |
| heavy activity, one at a time (median / p95) | 29.6 / 104 ms | 13.7 / 28 ms |
| heavy p95 at 75 visits/s | 645 ms | 76 ms |
| heavy p95 at 100 visits/s | 1.9 s | 73 ms |
| typical p95 at 100 visits/s | 944 ms | 9 ms |
| at 150 visits/s (p95 heavy / dropped) | 10.7 s / 1,528 | 846 ms / 95 |
| heavy friends (median) | 49.6 ms | 49.9 ms |

The knee moves from about 75-100 to above 100 visits/s. Friends is unchanged (its cost is the balances scan, not JIT).
Caveats: one run each, the JIT-on run was in an earlier session, and the load-test machine is a laptop; treat the size of the gap
as indicative, but a 25x difference at 100 visits/s is well outside noise. Suggested change: `ALTER ROLE <app role> SET jit = off`
(survives restarts, no deploy; session-level `SET` can be lost behind Neon's pooler). Not yet applied.
- Not measured: a network hop to the database, more than 10 heavy users, caching or materialised balances.
