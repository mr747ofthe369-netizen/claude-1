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


def grid(space):
    keys = list(space)
    for vals in itertools.product(*(space[k] for k in keys)):
        yield dict(zip(keys, vals))


def best(S, base, space, min_n=25, top=8, label=""):
    rows = []
    for over in grid(space):
        p = dict(base, **over)
        tr = tune.summary(S.run(p, "train"))
        if tr["n"] < min_n:
            continue
        rows.append((tr["pnl"], over, tr))
    rows.sort(key=lambda r: -r[0])
    print(f"\n== {label}: {len(rows)} settings with >= {min_n} train trades; top {top} by train P/L")
    out = []
    for pnl, over, tr in rows[:top]:
        te = tune.summary(S.run(dict(base, **over), "test"))
        print(json.dumps({k: v for k, v in over.items()}))
        print("   train", fmt(tr)); print("   test ", fmt(te))
        out.append((over, tr, te))
    return out


EXIT_SPACE = dict(
    floor0=[0.675, 0.75, 0.8, 0.85, 0.9],
    time_stop_min=[None, 30, 60, 120, 240, 720],
    time_stop_x=[1.1, 1.3, 1.5],
    horizon_bars=[24 * 360, 48 * 360],
)
ENTRY_SPACE = dict(
    min_age=[1.0, 2.0, 5.0],
    max_age=[25.0, 60.0],
    min_usd_vol=[0.0, 50e3, 250e3, 1e6],
    min_avg_trade=[0.0, 50.0, 150.0],
    min_buy_share=[0.0, 0.52, 0.56],
    min_momentum=[0.0, 10.0, 30.0],
    max_momentum=[1e9, 100.0],
    max_from_high=[1e9, 15.0],
)


if __name__ == "__main__":
    toks = tune.load()
    S = Searcher(toks)
    ntrain = sum(t["t0"] < SPLIT for t in toks)
    print(len(toks), "listings;", ntrain, "train (before 2025) /", len(toks) - ntrain, "test")
    base = dict(tune.APP)
    for which in ("train", "test"):
        print("APP DEFAULTS", which, fmt(tune.summary(S.run(base, which))))
    stage = sys.argv[1] if len(sys.argv) > 1 else "exits"
    if stage == "exits":
        best(S, base, EXIT_SPACE, label="exits (app entry filters)")
    elif stage == "entries":
        ex = json.loads(sys.argv[2]) if len(sys.argv) > 2 else {}
        best(S, dict(base, **ex), ENTRY_SPACE, label="entries with exits " + json.dumps(ex), top=12)
