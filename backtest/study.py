"""Which early metrics separate moonshots from the rest?

For every cached listing, take the metrics the app could see at a fixed age
(default 5 min) and the best multiple reached in the next 48h from the price
at that moment. Print, per metric, how often tokens in each quintile went on
to reach 3x / 5x.
"""
import sys
import numpy as np
import tune

AGE_MIN = float(sys.argv[1]) if len(sys.argv) > 1 else 5.0

toks = tune.load()
rows = []
for t in toks:
    k = int(AGE_MIN * 60 / tune.BAR_S) - 1
    if np.isnan(t["px"][k]):
        continue
    f = tune.features(t, k)
    fwd = t["px"][k + 1: k + 1 + tune.HORIZON_BARS]
    fwd = fwd[~np.isnan(fwd)]
    if len(fwd) == 0:
        continue
    f["max48"] = float(fwd.max() / t["px"][k])
    f["end48"] = float(fwd[-1] / t["px"][k])
    rows.append(f)

print(f"{len(rows)} listings, metrics at {AGE_MIN:g} min")
m48 = np.array([r["max48"] for r in rows])
print("reached >=2x: %d  >=3x: %d  >=5x: %d  >=10x: %d" % tuple((m48 >= x).sum() for x in (2, 3, 5, 10)))
print("median 48h end/entry: %.2f" % np.median([r["end48"] for r in rows]))
for key in ["trades", "usd_vol", "avg_trade", "buy_share", "ratio", "momentum",
            "recent_momentum", "from_high", "trades_per_min"]:
    v = np.array([r[key] for r in rows])
    qs = np.quantile(v, [0.2, 0.4, 0.6, 0.8])
    b = np.searchsorted(qs, v, side="right")
    line = []
    for i in range(5):
        sel = b == i
        line.append("%s %3d/%-3d %2d%%" % ("Q%d" % (i + 1), (m48[sel] >= 3).sum(), sel.sum(),
                                          round(100 * (m48[sel] >= 3).mean()) if sel.any() else 0))
    print(f"{key:16s} cuts {np.round(qs, 2)}\n   >=3x rate by quintile: " + " | ".join(line))
