"""Grid search over entry filters and exits, with a time-based train/test split.

Train: listings before SPLIT. Test: listings from SPLIT on (never used to pick
settings). Features are precomputed per token as arrays over the entry window,
and exit simulations are memoised per (token, entry bar, exit settings).
"""
import itertools, json, sys
from datetime import datetime, timezone
import numpy as np
import tune

SPLIT = int(datetime(2025, 1, 1, tzinfo=timezone.utc).timestamp() * 1000)
MAX_AGE_BARS = 60 * 6  # precompute up to 60 min


def feature_arrays(t):
    n = MAX_AGE_BARS
    k = np.arange(n)
    px = t["px"][:n]
    age = (k + 1) * tune.BAR_S / 60.0
    buys, sells = t["cnb"][:n], t["cns"][:n]
    usd = t["cvb"][:n] + t["cvs"][:n]
    k5 = np.maximum(0, k - 30)
    with np.errstate(divide="ignore", invalid="ignore"):
        return dict(
            valid=~np.isnan(px), age=age, trades=buys + sells, buys=buys,
            ratio=buys / np.maximum(1.0, sells),
            momentum=(px / t["open"] - 1) * 100,
            usd_vol=usd, buy_share=np.where(usd > 0, t["cvb"][:n] / usd, 0.0),
            avg_trade=usd / np.maximum(1.0, buys + sells),
            from_high=(t["runhi"][:n] / px - 1) * 100,
            recent_momentum=(px / t["px"][k5] - 1) * 100,
        )


def entry_bars(F, p):
    ok = (F["valid"] & (F["age"] >= p["min_age"]) & (F["age"] <= p["max_age"])
          & (F["trades"] >= p["min_trades"]) & (F["buys"] >= p["min_buys"])
          & (F["ratio"] >= p["min_ratio"]) & (F["momentum"] >= p["min_momentum"])
          & (F["momentum"] <= p["max_momentum"]) & (F["usd_vol"] >= p["min_usd_vol"])
          & (F["buy_share"] >= p["min_buy_share"]) & (F["avg_trade"] >= p["min_avg_trade"])
          & (F["from_high"] <= p["max_from_high"])
          & (F["recent_momentum"] >= p["min_recent_momentum"]))
    return int(np.argmax(ok)) if ok.any() else None


class Searcher:
    def __init__(self, toks):
        self.toks = toks
        self.F = [feature_arrays(t) for t in toks]
        self.memo = {}

    def run(self, p, which):
        out = []
        exit_key = tuple((k, str(p[k])) for k in ("floor0", "ratchets", "partials", "trail_at", "trail_keep",
                                                  "horizon_bars", "time_stop_min", "time_stop_x",
                                                  "delay_s", "entry_bps", "exit_bps"))
        for i, (t, F) in enumerate(zip(self.toks, self.F)):
            if which == "train" and t["t0"] >= SPLIT or which == "test" and t["t0"] < SPLIT:
                continue
            k = entry_bars(F, p)
            if k is None:
                continue
            key = (i, k, exit_key)
            if key not in self.memo:
                self.memo[key] = tune.exit_sim(t, k, p)
            r = dict(self.memo[key]); r["sym"] = t["sym"]; r["t0"] = t["t0"]
            out.append(r)
        return out


def fmt(s):
    return ("n=%(n)3d pnl=%(pnl)8.2f win=%(win)5.1f%% pf=%(pf)5.2f medROI=%(med_roi)6.1f%% "
            "avgLoss=%(avg_loss)6.2f exTop3=%(ex_top3)8.2f moons=%(moons)d" % s) if s["n"] else "n=0"


if __name__ == "__main__":
    toks = tune.load()
    S = Searcher(toks)
    ntrain = sum(t["t0"] < SPLIT for t in toks)
    print(len(toks), "listings;", ntrain, "train (before 2025) /", len(toks) - ntrain, "test")
    base = dict(tune.APP)
    for which in ("train", "test"):
        print("APP DEFAULTS", which, fmt(tune.summary(S.run(base, which))))
