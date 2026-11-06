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
