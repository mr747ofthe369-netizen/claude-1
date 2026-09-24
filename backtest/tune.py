"""Fast, parameterised replay of the app's strategy on cached 10s bars.

Entry: first 10s check between min_age and max_age minutes where every filter
passes. Exit rules follow PaperEngine.markOpen (checked every 20s), with
optional extra exits (time stop for non-movers) that the app does not have.
"""
import glob, os
import numpy as np

BAR_S = 10
MARK_BARS = 2                      # markOpen every 20s
HORIZON_BARS = 48 * 360            # 48h

APP = dict(
    # entry filters (app defaults)
    min_age=5.0, max_age=25.0, min_trades=20, min_buys=20, min_ratio=1.1,
    min_momentum=0.0,
    # extra entry filters (off by default)
    min_usd_vol=0.0, min_buy_share=0.0, min_avg_trade=0.0, max_momentum=1e9,
    max_from_high=1e9, min_recent_momentum=-1e9, min_trades_per_min=0.0,
    # fill
    delay_s=15, entry_bps=150, exit_bps=200, fixed_fee=0.03, stake=10.0,
    # exits (app defaults)
    floor0=0.675, ratchets=((2.0, 1.5), (3.0, 2.0), (5.0, 2.5)),
    partials=((7.0, 0.10), (20.0, 0.025)), trail_at=7.0, trail_keep=0.675,
    horizon_bars=HORIZON_BARS,
    # extra exit: leave if not reached time_stop_x within time_stop_min
    time_stop_min=None, time_stop_x=1.0,
)


def load(cache="cache"):
    toks = []
    for f in sorted(glob.glob(os.path.join(cache, "*.npz"))):
        if f.endswith(".part.npz"):
            continue
        try:
            z = np.load(f)
            last = z["last"].copy()
        except Exception:
            continue  # incomplete file
        valid = ~np.isnan(last)
        if valid.sum() < 50:
            continue
        # forward-fill last trade price
        idx = np.where(valid, np.arange(len(last)), 0)
        np.maximum.accumulate(idx, out=idx)
        px = last[idx]
        px[: np.argmax(valid)] = np.nan
        nb, ns = z["nbuy"].astype(float), z["nsell"].astype(float)
        vb, vs = z["vbuy"], z["vsell"]
        hi = np.where(np.isnan(z["high"]), -np.inf, z["high"])
        toks.append(dict(sym=os.path.basename(f)[:-4], t0=int(z["t0"]), px=px,
                         open=float(z["p0"]),
                         cnb=np.cumsum(nb), cns=np.cumsum(ns), cvb=np.cumsum(vb), cvs=np.cumsum(vs),
                         runhi=np.maximum.accumulate(hi), end=int(np.where(valid)[0][-1])))
    return toks


def features(t, k):
    """What the app could know at bar k (age = (k+1)*10s)."""
    age_min = (k + 1) * BAR_S / 60.0
    buys, sells = t["cnb"][k], t["cns"][k]
    usd = t["cvb"][k] + t["cvs"][k]
    px = t["px"][k]
    k5 = max(0, k - 30)  # 5 minutes earlier
    return dict(
        age_min=age_min, trades=buys + sells, buys=buys, sells=sells,
        ratio=buys / max(1.0, sells),
        momentum=(px / t["open"] - 1) * 100,
        usd_vol=usd, buy_share=t["cvb"][k] / usd if usd > 0 else 0.0,
        avg_trade=usd / max(1.0, buys + sells),
        from_high=(t["runhi"][k] / px - 1) * 100,
        recent_momentum=(px / t["px"][k5] - 1) * 100,
        trades_per_min=(buys + sells) / age_min,
    )


def passes(f, p):
    return (f["trades"] >= p["min_trades"] and f["buys"] >= p["min_buys"]
            and f["ratio"] >= p["min_ratio"] and f["momentum"] >= p["min_momentum"]
            and f["momentum"] <= p["max_momentum"] and f["usd_vol"] >= p["min_usd_vol"]
            and f["buy_share"] >= p["min_buy_share"] and f["avg_trade"] >= p["min_avg_trade"]
            and f["from_high"] <= p["max_from_high"]
            and f["recent_momentum"] >= p["min_recent_momentum"]
            and f["trades_per_min"] >= p["min_trades_per_min"])


def entry_bar(t, p):
    k0 = int(p["min_age"] * 60 / BAR_S) - 1
    k1 = int(p["max_age"] * 60 / BAR_S) - 1
    for k in range(k0, k1 + 1):
        if np.isnan(t["px"][k]):
            continue
        f = features(t, k)
        if passes(f, p):
            return k, f
    return None, None


def exit_sim(t, k, p):
    fill = k + int(np.ceil(p["delay_s"] / BAR_S))
    px = t["px"]
    entry = px[fill]
    eff = entry * (1 + p["entry_bps"] / 1e4)
    qty0 = p["stake"] / eff
    marks = np.arange(fill + MARK_BARS, min(len(px), t["end"] + 1), MARK_BARS)
    x = px[marks] / entry
    el = marks - fill
    runmax = np.maximum.accumulate(np.maximum(x, 1.0))
    prevmax = np.concatenate(([1.0], runmax[:-1]))
    floor = np.full(len(x), p["floor0"])
    for reach, fl in p["ratchets"]:
        floor = np.where(prevmax >= reach, np.maximum(floor, fl), floor)
    stop = x <= floor
    horizon = el >= p["horizon_bars"]
    trail = (runmax >= p["trail_at"]) & (x <= runmax * p["trail_keep"])
    ev = stop | horizon | trail
    if p["time_stop_min"] is not None:
        ts = (el * BAR_S / 60.0 >= p["time_stop_min"]) & (runmax < p["time_stop_x"])
        ev = ev | ts
    xb = 1 - p["exit_bps"] / 1e4
    if ev.any():
        j = int(np.argmax(ev))
        why = ("STOP" if stop[j] else "48H" if horizon[j] else "TRAIL" if trail[j] else "TIME")
    else:
        j = len(x) - 1
        why = "DATA_END"
    rem, proceeds = qty0, 0.0
    for reach, frac in p["partials"]:
        hit = np.where(x[: j + 1] >= reach)[0]
        if len(hit) and not (why in ("STOP", "48H", "TIME") and hit[0] == j):
            q = min(rem, qty0 * frac)
            proceeds += q * px[marks[hit[0]]] * xb
            rem -= q
    proceeds += max(0.0, rem * px[marks[j]] * xb - p["fixed_fee"])
    return dict(pnl=proceeds - p["stake"], roi=(proceeds / p["stake"] - 1) * 100,
                high_x=float(runmax[j]), max48=float(runmax[-1]) if len(runmax) else 1.0,
                exit=why, hold_min=el[j] * BAR_S / 60.0)


def run(toks, **over):
    p = dict(APP, **over)
    out = []
    for t in toks:
        k, f = entry_bar(t, p)
        if k is None:
            continue
        r = exit_sim(t, k, p)
        r.update(sym=t["sym"], t0=t["t0"], **{"f_" + a: b for a, b in f.items()})
        out.append(r)
    return out


def summary(rs, stake=10.0):
    if not rs:
        return dict(n=0, pnl=0.0)
    pnl = np.array([r["pnl"] for r in rs])
    s = np.sort(pnl)[::-1]
    wins = pnl[pnl > 0].sum(); losses = -pnl[pnl <= 0].sum()
    return dict(n=len(rs), pnl=round(pnl.sum(), 2), win=round((pnl > 0).mean() * 100, 1),
                pf=round(wins / losses, 2) if losses > 0 else float("inf"),
                med_roi=round(float(np.median([r["roi"] for r in rs])), 1),
                avg_loss=round(float(pnl[pnl <= 0].mean()) if (pnl <= 0).any() else 0, 2),
                ex_top3=round(s[3:].sum(), 2), moons=int(sum(r["high_x"] >= 5 for r in rs)))
