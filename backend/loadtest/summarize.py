#!/usr/bin/env python3
"""Prints the per-phase latency tables from results/summary-feeds.json (k6 --summary-export)."""
import json, sys
path = sys.argv[1] if len(sys.argv) > 1 else "results/summary-feeds.json"
m = json.load(open(path))["metrics"]

def row(label, key):
    v = m.get(key)
    if not v: return f"| {label} | n/a | n/a | n/a |"
    return f"| {label} | {v['med']:.1f} ms | {v['p(95)']:.1f} ms | {v['max']:.0f} ms |"

def table(title, rows):
    print(f"\n{title}\n\n| | median | p95 | max |\n|---|---|---|---|")
    for label, key in rows: print(row(label, key))

d = "http_req_duration"
table("One request at a time (150 visits each)", [
    (f"{w} {e}", f"{d}{{phase:latency,who:{w},endpoint:{e}}}")
    for w in ("typical", "heavy") for e in ("friends", "activity", "activity_next")])
table("Paging through a heavy user's whole feed (page = 30 items)", [
    (f"pages {x}", f"{d}{{phase:deep,depth:{x}}}") for x in ("p1-10", "p11-100", "p101+")])
steps = sorted({int(k.split("step:")[1].split(",")[0]) for k in m if "phase:load,step:" in k})
table("Under load (open model, visits per second; 90% typical / 10% heavy users)", [
    (f"step {i} {w}", f"{d}{{phase:load,step:{i},who:{w}}}") for i in steps for w in ("typical", "heavy")])
table("Under load, by endpoint (whole run)", [(e, f"{d}{{phase:load,endpoint:{e}}}") for e in ("friends", "activity", "activity_next")])
f = m.get("http_req_failed", {})
print(f"\nfailed requests: {f.get('value', 'n/a')}  (rate)   dropped iterations: {m.get('dropped_iterations', {}).get('count', 0)}")
