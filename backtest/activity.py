"""Test a "rising activity" entry: only trade when activity is building.

Bybit has no wallet data, so wallet growth is approximated by growth in trade
count, and money flow by growth in USD volume. At each 10s check we compare the
last W minutes with the W minutes before (needs age >= 2W), e.g.
"trades up >= 20% and volume up >= 50% versus the previous W minutes".

Entries may come up to 6h after listing (a second wave can start well after the
opening rush). Exits are the app's defaults. Settings are chosen on listings
before 2025 and checked on 2025-26 listings.
"""
import itertools, json, sys
import numpy as np
import tune
from search import SPLIT, fmt

MAX_BARS = 6 * 360  # 6h of 10s bars


def window_sum(c, k, w):
    """Sum over bars (k-w, k] from a cumulative array c."""
    prev = np.where(k - w >= 0, c[np.maximum(k - w, 0)], 0.0)
    return c[k] - prev


def arrays(t, W_list):
    n = min(MAX_BARS, len(t["px"]))
    k = np.arange(n)
    px = t["px"][:n]
    ctr = t["cnb"][:n] + t["cns"][:n]
    cusd = t["cvb"][:n] + t["cvs"][:n]
    A = dict(valid=~np.isnan(px), age=(k + 1) * tune.BAR_S / 60.0,
             momentum=(px / t["open"] - 1) * 100,
             from_high=(t["runhi"][:n] / px - 1) * 100)
    for W in W_list:
        w = W * 6
        tr_now, tr_prev = window_sum(ctr, k, w), window_sum(ctr, k - w, w)
        v_now, v_prev = window_sum(cusd, k, w), window_sum(cusd, k - w, w)
        vb_now = window_sum(t["cvb"][:n], k, w)
        with np.errstate(divide="ignore", invalid="ignore"):
            A[f"tr_g{W}"] = np.where(tr_prev > 0, tr_now / tr_prev - 1, np.inf) * 100
            A[f"v_g{W}"] = np.where(v_prev > 0, v_now / v_prev - 1, np.inf) * 100
            A[f"bs{W}"] = np.where(v_now > 0, vb_now / v_now, 0.0)
            A[f"v_now{W}"] = v_now
            A[f"ok{W}"] = k >= 2 * w
    return A


def entry(A, p):
    W = p["W"]
    ok = (A["valid"] & A[f"ok{W}"] & (A["age"] >= p["min_age"]) & (A["age"] <= p["max_age"])
          & (A[f"tr_g{W}"] >= p["min_trade_growth"]) & (A[f"v_g{W}"] >= p["min_vol_growth"])
          & (A[f"bs{W}"] >= p["min_buy_share"]) & (A[f"v_now{W}"] >= p["min_window_usd"])
          & (A["momentum"] >= p["min_momentum"]) & (A["from_high"] <= p["max_from_high"]))
    return int(np.argmax(ok)) if ok.any() else None


SPACE = dict(
    W=[5, 15, 30],
    min_trade_growth=[0.0, 20.0, 50.0],
    min_vol_growth=[20.0, 50.0, 100.0],
    min_buy_share=[0.0, 0.52, 0.56],
    min_window_usd=[0.0, 20e3],
    min_momentum=[-100.0, 0.0],
    max_from_high=[1e9, 15.0],
    min_age=[10.0, 30.0],
    max_age=[60.0, 180.0, 360.0],
)


def run(toks, AA, memo, p, which):
    out = []
    for i, (t, A) in enumerate(zip(toks, AA)):
        if (which == "train") != (t["t0"] < SPLIT):
            continue
        k = entry(A, p)
        if k is None:
            continue
        if (i, k) not in memo:
            memo[(i, k)] = tune.exit_sim(t, k, tune.APP)
        r = dict(memo[(i, k)]); r["sym"] = t["sym"]; r["age"] = float(A["age"][k])
        out.append(r)
    return out


if __name__ == "__main__":
    toks = tune.load()
    AA = [arrays(t, SPACE["W"]) for t in toks]
    memo = {}
    rows = []
    keys = list(SPACE)
    for vals in itertools.product(*(SPACE[k] for k in keys)):
        p = dict(zip(keys, vals))
        tr = tune.summary(run(toks, AA, memo, p, "train"))
        if tr["n"] >= 25:
            rows.append((tr["pnl"], p, tr))
    rows.sort(key=lambda r: -r[0])
    print(len(rows), "settings with >= 25 train trades")
    for pnl, p, tr in rows[:12]:
        te = tune.summary(run(toks, AA, memo, p, "test"))
        print(json.dumps(p)); print("   train", fmt(tr)); print("   test ", fmt(te))
    # how the best train setting behaves across the whole grid on test (robustness)
    tests = [tune.summary(run(toks, AA, memo, p, "test"))["pnl"] for _, p, _ in rows[:50]]
    print("test P/L of top-50 train settings: median %.2f, min %.2f, max %.2f"
          % (np.median(tests), min(tests), max(tests)))
