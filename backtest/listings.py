"""Find Bybit spot USDT listings from the public trade archive (first monthly file per symbol)."""
import re, json, sys, concurrent.futures as cf, urllib.request

BASE = "https://public.bybit.com/spot/"

def get(url):
    with urllib.request.urlopen(url, timeout=60) as r:
        return r.read().decode()

def first_file(sym):
    try:
        files = sorted(re.findall(r'href="([^"]+\.csv\.gz)"', get(BASE + sym + "/")))
        return sym, files
    except Exception as e:
        return sym, []

if __name__ == "__main__":
    syms = [s.strip("/") for s in re.findall(r'href="([^"]+)"', get(BASE))]
    syms = [s for s in syms if s.endswith("USDT")]
    out = {}
    with cf.ThreadPoolExecutor(16) as ex:
        for sym, files in ex.map(first_file, syms):
            if files:
                out[sym] = files
    json.dump(out, open("symbol_files.json", "w"))
    print(len(syms), "USDT symbols,", len(out), "with files")
