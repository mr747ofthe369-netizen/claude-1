"""Which listings ran big, and did a given entry rule catch them?

A runner is a listing whose price reached >= RUN_X times its price at 5 minutes
within the next 48h. For each rule we report, per runner, whether it entered,
the entry age, and the trade's result.
"""
import numpy as np
import tune, activity as A
from search import SPLIT

RUN_X = 3.0


def runners(toks):
    out = []
    for i, t in enumerate(toks):
        k = 29
        fwd = t["px"][k + 1: k + 1 + tune.HORIZON_BARS]
        fwd = fwd[~np.isnan(fwd)]
        if len(fwd) and fwd.max() / t["px"][k] >= RUN_X:
            out.append((i, fwd.max() / t["px"][k]))
    return out


if __name__ == "__main__":
    toks = tune.load()
    AA = [A.arrays(t, [5, 15]) for t in toks]
    import search
    S = search.Searcher(toks)
    act = dict(W=15, min_trade_growth=20.0, min_vol_growth=20.0, min_buy_share=0.52,
               min_window_usd=20e3, min_momentum=0.0, max_from_high=1e9, min_age=30.0, max_age=60.0)
    R = runners(toks)
    print(len(R), "runners (>= %.0fx from 5-min price within 48h)" % RUN_X)
    print("%-14s %-7s %6s | %-24s | %-24s" % ("token", "period", "max", "app default entry", "rising-activity entry"))
    for i, mx in sorted(R, key=lambda r: -r[1]):
        t = toks[i]
        per = "train" if t["t0"] < SPLIT else "test"
        k1 = search.entry_bars(S.F[i], tune.APP)
        k2 = A.entry(AA[i], act)
        def desc(k):
            if k is None:
                return "not entered"
            r = tune.exit_sim(t, k, tune.APP)
            return "@%4.0fm %+7.2f %s" % ((k + 1) / 6, r["pnl"], r["exit"])
        print("%-14s %-7s %5.1fx | %-24s | %-24s" % (t["sym"], per, mx, desc(k1), desc(k2)))
