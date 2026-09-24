"""Backtest the Meme Tail Lab paper strategy on Bybit spot new listings.

Replays the app's default PaperEngine rules (PaperEngine.evaluateWatches,
processPending, markOpen) against Bybit's public trade archive
(https://public.bybit.com/spot/). Each token is replayed from its first trade.

Differences from the app (Bybit is an exchange order book, not a DEX pool):
  * No liquidity filter / AMM price-impact model (no historical book depth).
  * "Buyers" uses the buy count, like the app's DexScreener fallback.
  * Prices are last-trade prices instead of DexScreener quotes.
"""
import csv, gzip, io, json, sys, urllib.request, concurrent.futures as cf
from datetime import datetime, timezone

BASE = "https://public.bybit.com/spot/"

# App defaults (Prefs.java)
MIN_AGE_MIN, MAX_AGE_MIN = 5.0, 25.0
MIN_TRADES, MIN_BUYERS, MIN_RATIO, MIN_MOMENTUM = 20, 20, 1.1, 0.0
DELAY_SEC = 15                      # "HUMAN SIM 15s"
ENTRY_BPS, EXIT_BPS, FIXED_FEE = 150, 200, 0.03
STAKE = 1.0 * 1000.0 / 100.0        # sizePct 1% of the $1,000 book
EVAL_EVERY_SEC, MARK_EVERY_SEC = 10, 20
HORIZON_MS = 172800000              # 48h
WINDOW_MS = int((MAX_AGE_MIN * 60 + DELAY_SEC + 60) * 1000) + HORIZON_MS + 60000


def trades(sym, files):
    """Yield (ts_ms, price, qty, side) from the first files until the replay window is covered."""
    t0 = None
    for f in files[:2]:
        with urllib.request.urlopen(BASE + sym + "/" + f, timeout=120) as r:
            rd = csv.reader(io.TextIOWrapper(gzip.GzipFile(fileobj=r), "utf-8"))
            next(rd, None)
            for row in rd:
                ts = int(float(row[1]))
                if t0 is None:
                    t0 = ts
                if ts > t0 + WINDOW_MS:
                    return
                yield ts, float(row[2]), float(row[3]), row[4]
    # window not covered by two files -> caller sees the stream end


def last_price_at(tr, idx, t):
    """Advance idx so tr[idx] is the last trade with ts <= t. Returns (idx, price)."""
    while idx + 1 < len(tr) and tr[idx + 1][0] <= t:
        idx += 1
    return idx, tr[idx][1]


def simulate(sym, files):
    tr = list(trades(sym, files))
    if len(tr) < 2:
        return {"symbol": sym, "status": "no-data"}
    tr.sort(key=lambda x: x[0])
    t0 = tr[0][0]
    res = {"symbol": sym, "listed": datetime.fromtimestamp(t0 / 1000, timezone.utc).strftime("%Y-%m-%d %H:%M"),
           "open_price": tr[0][1]}

    # --- qualification (evaluateWatches every 10s while age is inside [5, 25] min) ---
    sig_t = None
    reason = "no-qualify"
    lo = 0
    for k in range(int(MIN_AGE_MIN * 60), int(MAX_AGE_MIN * 60) + 1, EVAL_EVERY_SEC):
        t = t0 + k * 1000
        buys = sells = 0
        first_px = last_px = None
        for ts, px, q, side in tr:
            if ts < t - 3600000:
                continue
            if ts > t:
                break
            if first_px is None:
                first_px = px
            last_px = px
            if side == "buy":
                buys += 1
            else:
                sells += 1
        if last_px is None:
            continue
        momentum = (last_px / first_px - 1.0) * 100.0
        ratio = buys / max(1, sells)
        if buys + sells < MIN_TRADES:
            reason = "ACTIVITY"
        elif buys < MIN_BUYERS:
            reason = "BUYERS_PROXY"
        elif ratio < MIN_RATIO:
            reason = "BUY_SELL"
        elif momentum < MIN_MOMENTUM:
            reason = "MOMENTUM"
        else:
            sig_t, sig_px = t, last_px
            res.update(signal_age_min=k / 60.0, signal_buys=buys, signal_sells=sells,
                       signal_momentum=round(momentum, 2))
            break
    if sig_t is None:
        res.update(status="skipped", reason=reason)
        return res

    # --- delayed fill (processPending) ---
    fill_t = sig_t + DELAY_SEC * 1000
    idx, entry_market = last_price_at(tr, 0, fill_t)
    entry_eff = entry_market * (1 + ENTRY_BPS / 10000.0)
    qty0 = STAKE / entry_eff
    rem = qty0
    proceeds = 0.0
    floor_x, high_x, trail_peak = 0.675, 1.0, 1.0
    trail, hit7, hit20 = False, False, False
    exit_note, exit_t = None, None

    def sell(q, px, final):
        g = q * px * max(0.0, 1 - EXIT_BPS / 10000.0)
        return max(0.0, g - FIXED_FEE) if final else g

    # --- mark loop (markOpen every 20s) ---
    t = fill_t
    end_t = tr[-1][0]
    while True:
        t += MARK_EVERY_SEC * 1000
        if t > end_t:
            if t - fill_t < HORIZON_MS:
                exit_note = "DATA_END"
            break
        idx, px = last_price_at(tr, idx, t)
        x = px / entry_market
        high_x = max(high_x, x)
        if x <= floor_x:
            proceeds += sell(rem, px, True); rem = 0; exit_note, exit_t = "STOP @ %.2fx" % floor_x, t; break
        if t - fill_t >= HORIZON_MS:
            proceeds += sell(rem, px, True); rem = 0; exit_note, exit_t = "48H HORIZON", t; break
        if not hit7 and x >= 7.0:
            q = min(rem, qty0 * 0.1); proceeds += sell(q, px, False); rem -= q; hit7 = True
        if not hit20 and x >= 20.0:
            q = min(rem, qty0 * 0.025); proceeds += sell(q, px, False); rem -= q; hit20 = True
        if x >= 7.0:
            trail = True
        if trail:
            trail_peak = max(trail_peak, x)
            if x <= trail_peak * 0.675:
                proceeds += sell(rem, px, True); rem = 0; exit_note, exit_t = "32.5% RUNNER TRAIL", t; break
        if x >= 2.0: floor_x = max(floor_x, 1.5)
        if x >= 3.0: floor_x = max(floor_x, 2.0)
        if x >= 5.0: floor_x = max(floor_x, 2.5)
    if rem > 0:  # archive ended before an exit: mark to last price
        _, px = last_price_at(tr, idx, end_t)
        proceeds += sell(rem, px, True)
        exit_t = end_t
    res.update(status="traded", signal_price=sig_px, entry_market=entry_market,
               drift_pct=round((entry_market / sig_px - 1) * 100, 3),
               exit=exit_note, hold_min=round((exit_t - fill_t) / 60000, 1),
               high_x=round(high_x, 3), proceeds=round(proceeds, 4),
               pnl=round(proceeds - STAKE, 4), roi_pct=round((proceeds / STAKE - 1) * 100, 2))
    return res


def run(sym, files):
    try:
        return simulate(sym, files)
    except Exception as e:
        return {"symbol": sym, "status": "error", "reason": str(e)[:120]}


if __name__ == "__main__":
    start, end = sys.argv[1], sys.argv[2]            # e.g. 2024-09 2026-08
    files = json.load(open("symbol_files.json"))
    todo = {}
    for sym, fl in files.items():
        month = fl[0][-14:-7]
        if start <= month <= end:
            todo[sym] = fl
    print(len(todo), "listings between", start, "and", end, flush=True)
    results = []
    with cf.ThreadPoolExecutor(8) as ex:
        for i, r in enumerate(ex.map(lambda kv: run(*kv), sorted(todo.items()))):
            results.append(r)
            if (i + 1) % 20 == 0:
                print(i + 1, "done", flush=True)
    json.dump(results, open("results.json", "w"), indent=1)
    print("saved results.json")
