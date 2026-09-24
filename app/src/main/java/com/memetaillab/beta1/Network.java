package com.memetaillab.beta1;

import android.content.Context;
import android.text.TextUtils;
import com.memetaillab.beta1.ApiBudget;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

class Network {
    private static final Object GECKO_LOCK = new Object();
    private static long lastGeckoRequestMs = 0;
    private final ApiBudget budget;
    private final Context c;
    private volatile String lastPoolBatchError = "";
    private volatile String lastMarketSource = "DEX Screener";

    Network(Context c) {
        this.c = c.getApplicationContext();
        this.budget = new ApiBudget(this.c);
    }

    String lastPoolBatchError() {
        return this.lastPoolBatchError == null ? "" : this.lastPoolBatchError;
    }

    String lastMarketSource() {
        return Prefs.marketSource(this.c);
    }

    List<PoolSnap> discover() throws Exception {
        return discoverPages(5);
    }

    List<PoolSnap> discoverFast() throws Exception {
        return discoverPages(1);
    }

    private List<PoolSnap> discoverPages(int pages) throws Exception {
        ArrayList<PoolSnap> out = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (int page = 1; page <= Math.max(1, pages); page++) {
            String u = "https://api.geckoterminal.com/api/v2/networks/solana/new_pools?page=" + page + "&include=dex,base_token,quote_token";
            try {
                JSONObject j = getJson(u, 12000, null);
                Map<String, JSONObject> inc = included(j.optJSONArray("included"));
                JSONArray a = j.optJSONArray("data");
                if (a != null) {
                    for (int i = 0; i < a.length(); i++) {
                        PoolSnap p = parsePool(a.optJSONObject(i), inc);
                        if (p != null && p.ok() && seen.add(p.pool) && !p.book.isEmpty()) {
                            out.add(p);
                        }
                    }
                }
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("429")) {
                    break;
                }
                if (page == 1) {
                    throw e;
                }
            }
        }
        Prefs.discoverySource(this.c, "GeckoTerminal legacy discovery");
        return out;
    }

    List<PoolSnap> discoverFromSignature(String book, String signature) throws Exception {
        if (signature == null || signature.isEmpty()) {
            return Collections.emptyList();
        }
        JSONObject tx = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            JSONObject opts = new JSONObject().put("encoding", "jsonParsed").put("commitment", "confirmed").put("maxSupportedTransactionVersion", 0);
            JSONObject req = new JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", "getTransaction").put("params", new JSONArray().put(signature).put(opts));
            if (!this.budget.allow("HELIUS", 1)) {
                throw new Exception("Helius API budget throttled getTransaction");
            }
            JSONObject rsp = postJson(rpcEndpoint(), req, 12000, null);
            tx = rsp.optJSONObject("result");
            if (tx != null) {
                break;
            }
            try {
                Thread.sleep(650L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (tx == null) {
            throw new Exception("Helius transaction not confirmed yet");
        }
        LinkedHashSet<String> candidates = accountKeys(tx);
        candidates.remove("So11111111111111111111111111111111111111112");
        candidates.remove("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v");
        candidates.remove("Es9vMFrzaCERmJfrF4H2FYDkgFdmHqkV6P5dY1Y9Fgr");
        candidates.remove("675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8");
        candidates.remove("CPMMoo8L3F4NbTegBCKVNunggL7H1ZpdTHKxQB5qKP1C");
        candidates.remove("CAMMCzo5YL8w4VFF8KVHrK22GGUsp5VTaW7grrKgrWqK");
        candidates.remove("LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj");
        candidates.remove("pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA");
        candidates.remove("6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P");
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashMap<String, PoolSnap> found = new LinkedHashMap<>();
        ArrayList<String> keys = new ArrayList<>(candidates);
        passes:
        for (int pass = 0; pass < 4 && found.isEmpty(); pass++) {
            for (int off = 0; off < keys.size(); off += 30) {
                List<String> part = keys.subList(off, Math.min(keys.size(), off + 30));
                String joined = TextUtils.join(",", part);
                Object raw = getAny("https://api.dexscreener.com/tokens/v1/solana/" + enc(joined), 12000, null);
                JSONArray a2 = raw instanceof JSONArray ? (JSONArray) raw : null;
                if (a2 == null) {
                    continue;
                }
                long now = System.currentTimeMillis();
                for (int i = 0; i < a2.length(); i++) {
                    PoolSnap p2 = parseDexPair(a2.optJSONObject(i));
                    if (p2 != null && p2.ok() && book.equals(p2.book) && (p2.createdMs <= 0 || Math.abs(now - p2.createdMs) <= 3600000)) {
                        found.put(p2.pool, p2);
                    }
                }
            }
            if (found.isEmpty() && pass < 3) {
                try {
                    Thread.sleep(850L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break passes;
                }
            }
        }
        Prefs.discoverySource(this.c, "Helius program stream → DEX Screener");
        return new ArrayList<>(found.values());
    }

    List<PoolSnap> recoverRecentPools(int perProgram) {
        int i;
        LinkedHashMap<String, PoolSnap> out = new LinkedHashMap<>();
        char c = 0;
        int i2 = 1;
        int i3 = 5;
        String[][] targets = {new String[]{"675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8", "RAYDIUM"}, new String[]{"CPMMoo8L3F4NbTegBCKVNunggL7H1ZpdTHKxQB5qKP1C", "RAYDIUM"}, new String[]{"CAMMCzo5YL8w4VFF8KVHrK22GGUsp5VTaW7grrKgrWqK", "RAYDIUM"}, new String[]{"LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj", "RAYDIUM"}, new String[]{"pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA", "PUMPSWAP"}, new String[]{"6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P", "PUMPFUN"}};
        int length = targets.length;
        int i4 = 0;
        while (i4 < length) {
            String[] t = targets[i4];
            if (Prefs.venueEnabled(this.c, t[i2])) {
                try {
                    JSONObject opt = new JSONObject().put("limit", Math.max(i2, Math.min(i3, perProgram))).put("commitment", "confirmed");
                    JSONObject req = new JSONObject().put("jsonrpc", "2.0").put("id", i2).put("method", "getSignaturesForAddress").put("params", new JSONArray().put(t[c]).put(opt));
                    if (this.budget.allow("HELIUS", i2)) {
                        JSONObject rsp = postJson(rpcEndpoint(), req, 12000, null);
                        JSONArray a = rsp.optJSONArray("result");
                        if (a == null) {
                            i = length;
                        } else {
                            int i5 = 0;
                            while (i5 < a.length()) {
                                JSONObject x = a.optJSONObject(i5);
                                if (x == null) {
                                    i = length;
                                } else {
                                    i = length;
                                    try {
                                        long bt = x.optLong("blockTime", 0L) * 1000;
                                        if (bt <= 0 || System.currentTimeMillis() - bt <= 2700000) {
                                            String sig = x.optString("signature", "");
                                            try {
                                                for (PoolSnap p : discoverFromSignature(t[i2], sig)) {
                                                    out.put(p.pool, p);
                                                }
                                            } catch (Exception e) {
                                            }
                                        }
                                    } catch (Exception e2) {
                                    }
                                }
                                i5++;
                                length = i;
                                i2 = 1;
                            }
                            i = length;
                        }
                    } else {
                        i = length;
                    }
                } catch (Exception e3) {
                    i = length;
                    i4++;
                    length = i;
                    i3 = 5;
                    i2 = 1;
                    c = 0;
                }
            } else {
                i = length;
            }
            i4++;
            length = i;
            i3 = 5;
            i2 = 1;
            c = 0;
        }
        if (!out.isEmpty()) {
            Prefs.discoverySource(this.c, "Helius recent-signature recovery → DEX Screener");
        }
        return new ArrayList(out.values());
    }

    PoolSnap pool(String address) throws Exception {
        Map<String, PoolSnap> m = pools(Collections.singletonList(address));
        PoolSnap p = m.get(address);
        if (p == null) {
            throw new Exception("DEX Screener did not return pool");
        }
        return p;
    }

    Map<String, PoolSnap> pools(List<String> addresses) {
        StringBuilder append;
        String str;
        LinkedHashMap<String, PoolSnap> out = new LinkedHashMap<>();
        this.lastPoolBatchError = "";
        this.lastMarketSource = "DEX Screener";
        if (addresses == null || addresses.isEmpty()) {
            Prefs.marketSource(this.c, "DEX Screener");
            return out;
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>(addresses);
        ArrayList<String> all = new ArrayList<>(unique);
        if (Prefs.coinGeckoEnabled(this.c) && !Prefs.coinGeckoKey(this.c).trim().isEmpty()) {
            try {
                out.putAll(coinGeckoPools(all));
                if (!out.isEmpty()) {
                    this.lastMarketSource = "CoinGecko Onchain";
                }
            } catch (Exception e) {
                this.lastPoolBatchError = "CoinGecko: " + shortErr(e);
            }
        }
        ArrayList<String> needDex = new ArrayList<>();
        Iterator<String> it = all.iterator();
        while (it.hasNext()) {
            String x = it.next();
            if (!out.containsKey(x)) {
                needDex.add(x);
            }
        }
        if (!needDex.isEmpty()) {
            try {
                out.putAll(dexScreenerPools(needDex));
                if (!out.isEmpty()) {
                    this.lastMarketSource = this.lastMarketSource.startsWith("CoinGecko") ? "CoinGecko + DEX Screener fallback" : "DEX Screener";
                }
            } catch (Exception e2) {
                if (this.lastPoolBatchError.isEmpty()) {
                    append = new StringBuilder();
                    str = "DEX Screener: ";
                } else {
                    append = new StringBuilder().append(this.lastPoolBatchError);
                    str = " · DEX: ";
                }
                this.lastPoolBatchError = append.append(str).append(shortErr(e2)).toString();
            }
        }
        ArrayList<String> missing = new ArrayList<>();
        Iterator<String> it2 = all.iterator();
        while (it2.hasNext()) {
            String x2 = it2.next();
            if (!out.containsKey(x2)) {
                missing.add(x2);
            }
        }
        if (!missing.isEmpty() && Prefs.helius(this.c).trim().isEmpty()) {
            for (int off = 0; off < missing.size(); off += 25) {
                List<String> part = missing.subList(off, Math.min(missing.size(), off + 25));
                try {
                    String joined = TextUtils.join(",", part);
                    JSONObject j = getJson("https://api.geckoterminal.com/api/v2/networks/solana/pools/multi/" + enc(joined) + "?include=dex,base_token,quote_token", 20000, null);
                    Map<String, JSONObject> inc = included(j.optJSONArray("included"));
                    JSONArray a = j.optJSONArray("data");
                    if (a != null) {
                        for (int i = 0; i < a.length(); i++) {
                            PoolSnap p = parsePool(a.optJSONObject(i), inc);
                            if (p != null && p.ok()) {
                                out.put(p.pool, p);
                            }
                        }
                    }
                    this.lastMarketSource = "DEX Screener + Gecko legacy fallback";
                } catch (Exception e3) {
                    if (this.lastPoolBatchError.isEmpty()) {
                        this.lastPoolBatchError = "Gecko legacy fallback: " + shortErr(e3);
                    }
                }
            }
        }
        if (!missing.isEmpty() && Prefs.helius(this.c).trim().length() > 0 && this.lastPoolBatchError.isEmpty()) {
            this.lastPoolBatchError = "DEX_MISSING " + missing.size() + "/" + all.size();
        }
        Prefs.marketSource(this.c, this.lastMarketSource);
        return out;
    }

    private Map<String, PoolSnap> coinGeckoPools(List<String> addresses) throws Exception {
        LinkedHashMap<String, PoolSnap> out = new LinkedHashMap<>();
        String key = Prefs.coinGeckoKey(this.c).trim();
        if (key.isEmpty()) {
            return out;
        }
        String host = Prefs.coinGeckoPro(this.c) ? "https://pro-api.coingecko.com/api/v3" : "https://api.coingecko.com/api/v3";
        String header = Prefs.coinGeckoPro(this.c) ? "x-cg-pro-api-key" : "x-cg-demo-api-key";
        HashMap<String, String> headers = new HashMap<>();
        headers.put(header, key);
        for (int off = 0; off < addresses.size(); off += 30) {
            if (!this.budget.allow("COINGECKO", 1)) {
                throw new Exception("monthly API budget throttled request");
            }
            List<String> part = addresses.subList(off, Math.min(addresses.size(), off + 30));
            String joined = TextUtils.join(",", part);
            JSONObject j = getJson(host + "/onchain/networks/solana/pools/multi/" + enc(joined) + "?include=base_token,quote_token,dex", 12000, headers);
            Map<String, JSONObject> inc = included(j.optJSONArray("included"));
            JSONArray a = j.optJSONArray("data");
            if (a != null) {
                for (int i = 0; i < a.length(); i++) {
                    PoolSnap p = parsePool(a.optJSONObject(i), inc);
                    if (p != null && p.ok()) {
                        out.put(p.pool, p);
                    }
                }
            }
        }
        return out;
    }

    private Map<String, PoolSnap> dexScreenerPools(List<String> addresses) throws Exception {
        LinkedHashMap<String, PoolSnap> out = new LinkedHashMap<>();
        for (int off = 0; off < addresses.size(); off += 30) {
            List<String> part = addresses.subList(off, Math.min(addresses.size(), off + 30));
            String joined = TextUtils.join(",", part);
            JSONObject j = getJson("https://api.dexscreener.com/latest/dex/pairs/solana/" + enc(joined), 12000, null);
            JSONArray a = j.optJSONArray("pairs");
            if (a != null) {
                for (int i = 0; i < a.length(); i++) {
                    PoolSnap p = parseDexPair(a.optJSONObject(i));
                    if (p != null && p.ok()) {
                        out.put(p.pool, p);
                    }
                }
            }
        }
        return out;
    }

    String syncCoinGeckoPlan() {
        String key = Prefs.coinGeckoKey(this.c).trim();
        if (key.isEmpty()) {
            return "CoinGecko key is empty";
        }
        if (!Prefs.coinGeckoPro(this.c)) {
            return "Automatic /key usage sync requires a CoinGecko Pro key; Demo can be entered manually.";
        }
        try {
            HashMap<String, String> h = new HashMap<>();
            h.put("x-cg-pro-api-key", key);
            JSONObject j = getJson("https://pro-api.coingecko.com/api/v3/key", 10000, h);
            long monthly = j.optLong("api_key_monthly_call_credit", j.optLong("monthly_call_credit", 0L));
            int rpm = j.optInt("api_key_rate_limit_request_per_minute", j.optInt("rate_limit_request_per_minute", 0));
            long used = j.optLong("current_total_monthly_calls", 0L);
            ApiBudget.Snapshot old = this.budget.snapshot("COINGECKO");
            int reset = old.resetDay > 0 ? old.resetDay : 1;
            int reserve = old.reservePct > 0 ? old.reservePct : 10;
            this.budget.configure("COINGECKO", monthly, rpm, reset, reserve, used);
            return String.format(Locale.US, "CoinGecko plan synced: %,d monthly credits · %d/min · %,d used", Long.valueOf(monthly), Integer.valueOf(rpm), Long.valueOf(used));
        } catch (Exception e) {
            return "CoinGecko plan sync failed: " + shortErr(e);
        }
    }

    String testSources() {
        String str = "✓ ";
        StringBuilder b = new StringBuilder();
        try {
            JSONObject req = new JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", "getHealth").put("params", new JSONArray());
            JSONObject r = postJson(rpcEndpoint(), req, 10000, null);
            b.append("ok".equalsIgnoreCase(r.optString("result")) ? "✓ " : "! ").append("Helius / Solana RPC ").append(host(rpcEndpoint())).append("\n");
        } catch (Exception e) {
            b.append("✗ Helius / Solana RPC: ").append(shortErr(e)).append("\n");
        }
        try {
            Object o = getAny("https://api.dexscreener.com/token-pairs/v1/solana/So11111111111111111111111111111111111111112", 10000, null);
            b.append(o instanceof JSONArray ? "✓ " : "! ").append("DEX Screener market data\n");
        } catch (Exception e2) {
            b.append("✗ DEX Screener: ").append(shortErr(e2)).append("\n");
        }
        if (Prefs.coinGeckoEnabled(this.c) && !Prefs.coinGeckoKey(this.c).trim().isEmpty()) {
            try {
                HashMap<String, String> h = new HashMap<>();
                h.put(Prefs.coinGeckoPro(this.c) ? "x-cg-pro-api-key" : "x-cg-demo-api-key", Prefs.coinGeckoKey(this.c).trim());
                String host = Prefs.coinGeckoPro(this.c) ? "https://pro-api.coingecko.com/api/v3" : "https://api.coingecko.com/api/v3";
                getJson(host + "/ping", 8000, h);
                b.append("✓ CoinGecko API authenticated\n");
            } catch (Exception e3) {
                b.append("✗ CoinGecko API: ").append(shortErr(e3)).append("\n");
            }
        }
        try {
            JSONObject q = getJson(Prefs.rayApi(this.c) + "/main/auto-fee", 10000, null);
            if (!q.optBoolean("success", true)) {
                str = "! ";
            }
            b.append(str).append("Raydium API / priority fee\n");
        } catch (Exception e4) {
            b.append("✗ Raydium API: ").append(shortErr(e4)).append("\n");
        }
        if (Prefs.helius(this.c).trim().isEmpty()) {
            try {
                List<PoolSnap> x = discoverFast();
                b.append("✓ GeckoTerminal legacy discovery: ").append(x.size()).append(" supported pools\n");
            } catch (Exception e5) {
                b.append("✗ Gecko legacy discovery: ").append(shortErr(e5)).append("\n");
            }
        } else {
            b.append("✓ Discovery mode: Helius program stream → DEX Screener\n");
        }
        return b.toString();
    }

    String rpcEndpoint() {
        String h = Prefs.helius(this.c).trim();
        if (!h.isEmpty()) {
            return "https://mainnet.helius-rpc.com/?api-key=" + url(h);
        }
        String r = Prefs.rpc(this.c).trim();
        return r.isEmpty() ? "https://api.mainnet-beta.solana.com" : r;
    }

    private LinkedHashSet<String> accountKeys(JSONObject tx) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        try {
            JSONObject transaction = tx.optJSONObject("transaction");
            JSONObject message = transaction == null ? null : transaction.optJSONObject("message");
            JSONArray keys = message == null ? null : message.optJSONArray("accountKeys");
            if (keys != null) {
                for (int i = 0; i < keys.length(); i++) {
                    Object x = keys.opt(i);
                    String k = "";
                    if (x instanceof JSONObject) {
                        k = ((JSONObject) x).optString("pubkey", "");
                    } else if (x != null) {
                        k = String.valueOf(x);
                    }
                    if (k.length() >= 32) {
                        out.add(k);
                    }
                }
            }
            JSONObject meta = tx.optJSONObject("meta");
            JSONObject loaded = meta == null ? null : meta.optJSONObject("loadedAddresses");
            int i2 = 0;
            String[] strArr = {"writable", "readonly"};
            for (int i3 = 2; i2 < i3; i3 = 2) {
                String n = strArr[i2];
                JSONArray a = loaded == null ? null : loaded.optJSONArray(n);
                if (a != null) {
                    for (int i4 = 0; i4 < a.length(); i4++) {
                        String k2 = a.optString(i4, "");
                        if (k2.length() >= 32) {
                            out.add(k2);
                        }
                    }
                }
                i2++;
            }
        } catch (Exception e) {
        }
        return out;
    }

    private PoolSnap parseDexPair(JSONObject x) {
        if (x == null) {
            return null;
        }
        PoolSnap p = new PoolSnap();
        p.fetchedMs = System.currentTimeMillis();
        String str = "";
        p.pool = x.optString("pairAddress", "");
        p.dexId = x.optString("dexId", "");
        p.book = Config.bookForDex(p.dexId);
        p.venue = Config.venueLabel(p.dexId);
        JSONObject base = x.optJSONObject("baseToken");
        JSONObject quote = x.optJSONObject("quoteToken");
        String ba = base == null ? "" : base.optString("address", "");
        String qa = quote == null ? "" : quote.optString("address", "");
        boolean baseIsQuote = Config.quoteMint(ba);
        boolean quoteIsQuote = Config.quoteMint(qa);
        boolean chooseQuote = baseIsQuote && !quoteIsQuote;
        JSONObject token = chooseQuote ? quote : base;
        p.mint = token == null ? "" : token.optString("address", "");
        p.quoteMint = chooseQuote ? ba : qa;
        p.symbol = token == null ? "" : token.optString("symbol", "");
        if (token != null) {
            str = token.optString("name", "");
        }
        p.name = str;
        p.priceUsd = num(x.opt("priceUsd"));
        JSONObject liq = x.optJSONObject("liquidity");
        p.liquidityUsd = liq == null ? 0.0d : num(liq.opt("usd"));
        p.createdMs = x.optLong("pairCreatedAt", 0L);
        JSONObject vol = x.optJSONObject("volume");
        p.volumeH1 = vol == null ? 0.0d : num(vol.opt("h1"));
        JSONObject pc = x.optJSONObject("priceChange");
        p.momentumH1 = pc == null ? 0.0d : num(pc.opt("h1"));
        JSONObject tx = x.optJSONObject("txns");
        JSONObject h1 = tx != null ? tx.optJSONObject("h1") : null;
        if (h1 != null) {
            p.buysH1 = h1.optInt("buys", 0);
            p.sellsH1 = h1.optInt("sells", 0);
            p.buyersH1 = p.buysH1;
        }
        return p;
    }

    private PoolSnap parsePool(JSONObject d, Map<String, JSONObject> inc) {
        if (d == null) {
            return null;
        }
        JSONObject a = d.optJSONObject("attributes");
        JSONObject rel = d.optJSONObject("relationships");
        if (a == null || rel == null) {
            return null;
        }
        PoolSnap p = new PoolSnap();
        p.fetchedMs = System.currentTimeMillis();
        p.pool = a.optString("address", "");
        if (p.pool.isEmpty()) {
            p.pool = stripId(d.optString("id", ""));
        }
        String dexId = relId(rel, "dex");
        p.dexId = dexId;
        p.book = Config.bookForDex(dexId);
        p.venue = Config.venueLabel(dexId);
        String baseId = relId(rel, "base_token");
        String quoteId = relId(rel, "quote_token");
        String base = stripId(baseId);
        String quote = stripId(quoteId);
        JSONObject bm = inc.get(baseId);
        JSONObject qm = inc.get(quoteId);
        boolean baseIsQuote = Config.quoteMint(base);
        boolean quoteIsQuote = Config.quoteMint(quote);
        boolean chooseQuote = baseIsQuote && !quoteIsQuote;
        p.mint = chooseQuote ? quote : base;
        p.quoteMint = chooseQuote ? base : quote;
        JSONObject tm = chooseQuote ? qm : bm;
        JSONObject ta = tm == null ? null : tm.optJSONObject("attributes");
        p.symbol = ta == null ? "" : ta.optString("symbol", "");
        p.name = ta == null ? "" : ta.optString("name", "");
        if (p.symbol.isEmpty()) {
            String n = a.optString("name", "");
            p.symbol = n.contains("/") ? n.substring(0, n.indexOf(47)).trim() : shortAddr(p.mint);
        }
        p.priceUsd = num(a.opt(chooseQuote ? "quote_token_price_usd" : "base_token_price_usd"));
        p.liquidityUsd = num(a.opt("reserve_in_usd"));
        p.createdMs = parseTime(a.optString("pool_created_at", ""));
        JSONObject vol = a.optJSONObject("volume_usd");
        p.volumeH1 = vol == null ? 0.0d : num(vol.opt("h1"));
        JSONObject pc = a.optJSONObject("price_change_percentage");
        p.momentumH1 = pc == null ? 0.0d : num(pc.opt("h1"));
        JSONObject tx = a.optJSONObject("transactions");
        JSONObject h1 = tx == null ? null : tx.optJSONObject("h1");
        if (h1 != null) {
            p.buysH1 = h1.optInt("buys", 0);
            p.sellsH1 = h1.optInt("sells", 0);
            p.buyersH1 = h1.optInt("buyers", 0);
            p.sellersH1 = h1.optInt("sellers", 0);
        }
        return p;
    }

    private static String relId(JSONObject r, String k) {
        JSONObject d;
        JSONObject x = r.optJSONObject(k);
        return (x == null || (d = x.optJSONObject("data")) == null) ? "" : d.optString("id", "");
    }

    private static Map<String, JSONObject> included(JSONArray a) {
        HashMap<String, JSONObject> m = new HashMap<>();
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                JSONObject x = a.optJSONObject(i);
                if (x != null) {
                    m.put(x.optString("id", ""), x);
                }
            }
        }
        return m;
    }

    private static String stripId(String id) {
        if (id == null) {
            return "";
        }
        int i = id.indexOf(95);
        return (i < 0 || i + 1 >= id.length()) ? id : id.substring(i + 1);
    }

    private static long parseTime(String s) {
        try {
            return Instant.parse(s).toEpochMilli();
        } catch (Exception e) {
            return 0L;
        }
    }

    static double num(Object o) {
        if (o != null && o != JSONObject.NULL) {
            try {
                return Double.parseDouble(String.valueOf(o));
            } catch (Exception e) {
            }
        }
        return 0.0d;
    }

    static String shortAddr(String s) {
        return s == null ? "" : s.length() > 12 ? s.substring(0, 6) + "…" + s.substring(s.length() - 5) : s;
    }

    static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8").replace("%2C", ",");
        } catch (Exception e) {
            return s;
        }
    }

    static String url(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    static String host(String s) {
        try {
            return new URL(s).getHost();
        } catch (Exception e) {
            return s;
        }
    }

    static String shortErr(Exception e) {
        String s = e.getMessage();
        if (s == null) {
            s = e.getClass().getSimpleName();
        }
        return s.length() > 180 ? s.substring(0, 180) : s;
    }

    static JSONObject getJson(String endpoint, int timeout, Map<String, String> headers) throws Exception {
        Object o = getAny(endpoint, timeout, headers);
        if (o instanceof JSONObject) {
            return (JSONObject) o;
        }
        throw new Exception("Expected JSON object");
    }

    static Object getAny(String endpoint, int timeout, Map<String, String> headers) throws Exception {
        if (endpoint != null && endpoint.startsWith("https://api.geckoterminal.com/api/v2")) {
            throttleGecko();
        }
        HttpURLConnection h = (HttpURLConnection) new URL(endpoint).openConnection();
        h.setConnectTimeout(timeout);
        h.setReadTimeout(timeout);
        h.setRequestMethod("GET");
        h.setRequestProperty("Accept", "application/json");
        h.setRequestProperty("User-Agent", "MemeTailLabBeta2/2.4");
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                h.setRequestProperty(e.getKey(), e.getValue());
            }
        }
        int code = h.getResponseCode();
        String txt = read((code < 200 || code >= 300) ? h.getErrorStream() : h.getInputStream());
        if (code < 200 || code >= 300) {
            throw new Exception("HTTP " + code + " " + txt);
        }
        String t = txt.trim();
        return t.startsWith("[") ? new JSONArray(t) : new JSONObject(t);
    }

    static JSONObject postJson(String endpoint, JSONObject body, int timeout, Map<String, String> headers) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(endpoint).openConnection();
        h.setConnectTimeout(timeout);
        h.setReadTimeout(timeout);
        h.setRequestMethod("POST");
        h.setRequestProperty("Content-Type", "application/json");
        h.setRequestProperty("Accept", "application/json");
        h.setRequestProperty("User-Agent", "MemeTailLabBeta2/2.4");
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                h.setRequestProperty(e.getKey(), e.getValue());
            }
        }
        h.setDoOutput(true);
        byte[] b = body.toString().getBytes(StandardCharsets.UTF_8);
        OutputStream os = h.getOutputStream();
        try {
            os.write(b);
            if (os != null) {
                os.close();
            }
            int code = h.getResponseCode();
            String txt = read((code < 200 || code >= 300) ? h.getErrorStream() : h.getInputStream());
            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code + " " + txt);
            }
            return new JSONObject(txt);
        } catch (Throwable th) {
            if (os != null) {
                try {
                    os.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
            }
            throw th;
        }
    }

    private static void throttleGecko() throws InterruptedException {
        synchronized (GECKO_LOCK) {
            long now = System.currentTimeMillis();
            long wait = 6200 - (now - lastGeckoRequestMs);
            if (wait > 0) {
                Thread.sleep(wait);
            }
            lastGeckoRequestMs = System.currentTimeMillis();
        }
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) {
            return "";
        }
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder b = new StringBuilder();
        while (true) {
            String s = r.readLine();
            if (s == null) {
                return b.toString();
            }
            b.append(s);
        }
    }
}
