"""Cache each listing's first 49h of Bybit spot trades as 10-second bars.

Output: cache/<SYMBOL>.npz with arrays over bars since the first trade:
  last, high, low           price (NaN where the bar has no trades)
  nbuy, nsell               trade counts
  vbuy, vsell               USD volume (price * qty)
plus t0 (first trade, ms).
"""
import csv, gzip, io, json, os, sys, urllib.request, concurrent.futures as cf
import numpy as np

BASE = "https://public.bybit.com/spot/"
BAR_MS = 10_000
SPAN_MS = 49 * 3600 * 1000
NBARS = SPAN_MS // BAR_MS
os.makedirs("cache", exist_ok=True)


def fetch(sym, files):
    out = f"cache/{sym}.npz"
    if os.path.exists(out):
        return sym, "cached"
    ts_l, px_l, q_l, buy_l = [], [], [], []
    t0 = None
    try:
        for f in files[:2]:
            with urllib.request.urlopen(BASE + sym + "/" + f, timeout=180) as r:
                rd = csv.reader(io.TextIOWrapper(gzip.GzipFile(fileobj=r), "utf-8"))
                next(rd, None)
                for row in rd:
                    ts = int(float(row[1]))
                    if t0 is None:
                        t0 = ts
                    if ts - t0 >= SPAN_MS:
                        break
                    ts_l.append(ts); px_l.append(float(row[2])); q_l.append(float(row[3]))
                    buy_l.append(row[4].lower() == "buy")
                else:
                    continue
                break
    except Exception as e:
        return sym, "error " + str(e)[:80]
    if t0 is None:
        return sym, "empty"
    ts = np.array(ts_l, np.int64); px = np.array(px_l); q = np.array(q_l); buy = np.array(buy_l)
    order = np.argsort(ts, kind="stable")
    ts, px, q, buy = ts[order], px[order], q[order], buy[order]
    t0 = int(ts[0]); p0 = float(px[0])
    b = ((ts - t0) // BAR_MS).astype(np.int64)
    ok = (b >= 0) & (b < NBARS)
    b, px, q, buy = b[ok], px[ok], q[ok], buy[ok]
    last = np.full(NBARS, np.nan); high = np.full(NBARS, np.nan); low = np.full(NBARS, np.nan)
    rev = len(b) - 1 - np.unique(b[::-1], return_index=True)[1]
    last[b[rev]] = px[rev]
    high_v = np.full(NBARS, -np.inf); np.maximum.at(high_v, b, px); high[np.isfinite(high_v)] = high_v[np.isfinite(high_v)]
    low_v = np.full(NBARS, np.inf); np.minimum.at(low_v, b, px); low[np.isfinite(low_v)] = low_v[np.isfinite(low_v)]
    usd = px * q
    nbuy = np.bincount(b[buy], minlength=NBARS).astype(np.int32)
    nsell = np.bincount(b[~buy], minlength=NBARS).astype(np.int32)
    vbuy = np.bincount(b[buy], weights=usd[buy], minlength=NBARS)
    vsell = np.bincount(b[~buy], weights=usd[~buy], minlength=NBARS)
    tmp = f"cache/{sym}.part.npz"
    np.savez_compressed(tmp, t0=t0, p0=p0, last=last, high=high, low=low, nbuy=nbuy, nsell=nsell, vbuy=vbuy, vsell=vsell)
    os.replace(tmp, out)
    return sym, "ok"


if __name__ == "__main__":
    start, end = sys.argv[1], sys.argv[2]
    files = json.load(open("symbol_files.json"))
    todo = sorted((s, fl) for s, fl in files.items() if start <= fl[0][-14:-7] <= end)
    print(len(todo), "listings", flush=True)
    with cf.ProcessPoolExecutor(int(os.environ.get("WORKERS", "8"))) as ex:
        for i, (sym, st) in enumerate(ex.map(fetch, [s for s, _ in todo], [fl for _, fl in todo])):
            if st not in ("ok", "cached"):
                print(sym, st, flush=True)
            if (i + 1) % 50 == 0:
                print(i + 1, "done", flush=True)
