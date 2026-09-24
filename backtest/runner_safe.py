"""Loosen instead of tighten: keep the early entry, drop losers, keep runners.

Only settings that still catch all but MAX_MISS of the pre-2025 runners are
eligible; among those, rank by pre-2025 P/L. Then check 2025-26 P/L and how
many 2025-26 runners each setting catches.
"""
import json
import numpy as np
import tune, search, runners as RU
from search import SPLIT, fmt, grid

MAX_MISS = 1
SPACE = dict(
    min_age=[1.0, 2.0, 5.0],
    min_ratio=[1.1, 1.2, 1.3],
    min_buy_share=[0.0, 0.5, 0.52, 0.55, 0.58],
    max_usd_vol=[1e18, 2e6, 1e6, 5e5],
    max_from_high=[1e9, 50.0, 25.0, 15.0],
    min_recent_momentum=[-1e9, -20.0, -10.0, 0.0],
)

if __name__ == "__main__":
    toks = tune.load()
    S = search.Searcher(toks)
    run_ids = {toks[i]["sym"] for i, _ in RU.runners(toks)}
    tr_runs = {toks[i]["sym"] for i, _ in RU.runners(toks) if toks[i]["t0"] < SPLIT}
    te_runs = run_ids - tr_runs

    def caught(rs, pool):
        return len({r["sym"] for r in rs} & pool)

    base = dict(tune.APP)
    for w, pool in (("train", tr_runs), ("test", te_runs)):
        rs = S.run(base, w)
        print("APP DEFAULTS", w, fmt(tune.summary(rs)), "runners %d/%d" % (caught(rs, pool), len(pool)))
    rows = []
    for over in grid(SPACE):
        p = dict(base, **over)
        rs = S.run(p, "train")
        if caught(rs, tr_runs) < len(tr_runs) - MAX_MISS:
            continue
        rows.append((tune.summary(rs)["pnl"], over, rs))
    rows.sort(key=lambda r: -r[0])
    print(len(rows), "settings keep >= %d/%d pre-2025 runners" % (len(tr_runs) - MAX_MISS, len(tr_runs)))
    tests = []
    for pnl, over, rs in rows[:10]:
        te = S.run(dict(base, **over), "test")
        print(json.dumps(over))
        print("   train", fmt(tune.summary(rs)), "runners %d/%d" % (caught(rs, tr_runs), len(tr_runs)))
        print("   test ", fmt(tune.summary(te)), "runners %d/%d" % (caught(te, te_runs), len(te_runs)))
    tp = [tune.summary(S.run(dict(base, **o), "test"))["pnl"] for _, o, _ in rows[:50]]
    print("2025-26 P/L of top-50: median %.2f, min %.2f, max %.2f" % (np.median(tp), min(tp), max(tp)))
