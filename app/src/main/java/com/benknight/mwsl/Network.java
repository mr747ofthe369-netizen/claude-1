package com.benknight.mwsl;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONArray;
import org.json.JSONObject;

class Network {
    private volatile String activeRpc;
    private final Context context;
    private final Map<String, Market> cache = new ConcurrentHashMap();
    private final Map<Long, Double> solHistoryCache = new ConcurrentHashMap();

    Network(Context c) {
        this.context = c.getApplicationContext();
    }

    List<String> rpcCandidates() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String k = Prefs.heliusKey(this.context);
        if (!k.isEmpty()) {
            out.add("https://mainnet.helius-rpc.com/?api-key=" + k);
        }
        String custom = Prefs.rpcUrl(this.context);
        if (!custom.isEmpty()) {
            out.add(custom);
        }
        out.add("https://solana-rpc.publicnode.com");
        out.add("https://solana.drpc.org/");
        out.add("https://rpc.ankr.com/solana");
        out.add("https://api.mainnet.solana.com");
        out.add("https://api.mainnet-beta.solana.com");
        return new ArrayList(out);
    }

    JSONObject rpcObject(String method, JSONArray params) throws Exception {
        List<String> eps = new ArrayList<>();
        if (this.activeRpc != null) {
            eps.add(this.activeRpc);
        }
        for (String s : rpcCandidates()) {
            if (!eps.contains(s)) {
                eps.add(s);
            }
        }
        Exception last = null;
        StringBuilder errors = new StringBuilder();
        boolean allDns = true;
        for (String ep : eps) {
            try {
                JSONObject req = new JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", method).put("params", params);
                JSONObject res = post(ep, req, 15000);
                if (res.has("error")) {
                    throw new Exception(res.getJSONObject("error").optString("message", res.get("error").toString()));
                }
                this.activeRpc = ep;
                return res;
            } catch (Exception e) {
                last = e;
                if (ep.equals(this.activeRpc)) {
                    this.activeRpc = null;
                }
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                if (!isDnsError(msg)) {
                    allDns = false;
                }
                if (errors.length() > 0) {
                    errors.append(" | ");
                }
                errors.append(host(ep)).append(": ").append(shortErr(msg));
            }
        }
        if (!allDns || eps.isEmpty()) {
            throw new Exception("All RPC sources failed: " + (errors.length() == 0 ? last == null ? "unknown" : last.getMessage() : errors.toString()));
        }
        throw new Exception("DNS failed for every RPC host. Check Android Private DNS/VPN/network. " + ((Object) errors));
    }

    Object rpcResult(String method, JSONArray params) throws Exception {
        JSONObject r = rpcObject(method, params);
        return r.opt("result");
    }

    JSONArray signatures(String wallet, String until, String before, int limit) throws Exception {
        JSONObject o = new JSONObject().put("limit", limit);
        if (until != null && !until.isEmpty()) {
            o.put("until", until);
        }
        if (before != null && !before.isEmpty()) {
            o.put("before", before);
        }
        boolean historyLookup = until == null || until.isEmpty();
        List<String> eps = new ArrayList<>();
        if (this.activeRpc != null) {
            eps.add(this.activeRpc);
        }
        for (String s : rpcCandidates()) {
            if (!eps.contains(s)) {
                eps.add(s);
            }
        }
        StringBuilder errors = new StringBuilder();
        boolean allDns = true;
        boolean hadHealthyEmpty = false;
        Exception last = null;
        for (String ep : eps) {
            try {
                JSONObject req = new JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", "getSignaturesForAddress").put("params", new JSONArray().put(wallet).put(o));
                JSONObject res = post(ep, req, 15000);
                if (res.has("error")) {
                    throw new Exception(res.getJSONObject("error").optString("message", res.get("error").toString()));
                }
                Object v = res.opt("result");
                JSONArray arr = v instanceof JSONArray ? (JSONArray) v : new JSONArray();
                if (arr.length() > 0) {
                    this.activeRpc = ep;
                    return arr;
                }
                hadHealthyEmpty = true;
                if (!historyLookup) {
                    this.activeRpc = ep;
                    return arr;
                }
            } catch (Exception e) {
                if (ep.equals(this.activeRpc)) {
                    this.activeRpc = null;
                }
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                if (!isDnsError(msg)) {
                    allDns = false;
                }
                if (errors.length() > 0) {
                    errors.append(" | ");
                }
                errors.append(host(ep)).append(": ").append(shortErr(msg));
                last = e;
            }
        }
        if (hadHealthyEmpty) {
            return new JSONArray();
        }
        if (!allDns || eps.isEmpty()) {
            throw new Exception("All RPC signature sources failed: " + (errors.length() == 0 ? last == null ? "unknown" : last.getMessage() : errors.toString()));
        }
        throw new Exception("DNS failed for every RPC host. Check Android Private DNS/VPN/network. " + ((Object) errors));
    }

    JSONObject transaction(String signature) throws Exception {
        JSONObject opts = new JSONObject().put("encoding", "jsonParsed").put("maxSupportedTransactionVersion", 1).put("commitment", "confirmed");
        Object v = rpcResult("getTransaction", new JSONArray().put(signature).put(opts));
        if (v instanceof JSONObject) {
            return (JSONObject) v;
        }
        return null;
    }

    boolean health(String endpoint) throws Exception {
        JSONObject req = new JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", "getHealth").put("params", new JSONArray());
        JSONObject res = post(endpoint, req, 8000);
        return "ok".equalsIgnoreCase(String.valueOf(res.opt("result")));
    }

    Market tokenMarket(String mint, boolean force) {
        Market hit = this.cache.get(mint);
        long now = System.currentTimeMillis();
        if (!force && hit != null && now - hit.at < 12000) {
            return hit;
        }
        Market out = new Market();
        out.at = now;
        try {
            JSONObject j = get("https://api.dexscreener.com/latest/dex/tokens/" + mint, 10000);
            JSONArray pairs = j.optJSONArray("pairs");
            JSONObject best = null;
            double liq = -1.0d;
            if (pairs != null) {
                for (int i = 0; i < pairs.length(); i++) {
                    JSONObject p = pairs.optJSONObject(i);
                    if (p == null || !"solana".equals(p.optString("chainId"))) {
                        continue;
                    }
                    double px = num(p.opt("priceUsd"));
                    if (px <= 0.0d) {
                        continue;
                    }
                    double l = p.optJSONObject("liquidity") != null ? num(p.optJSONObject("liquidity").opt("usd")) : 0.0d;
                    if (best == null || l > liq) {
                        best = p;
                        liq = l;
                    }
                }
            }
            if (best == null) {
                throw new Exception("no Solana pair");
            }
            out.priceUsd = num(best.opt("priceUsd"));
            out.dexId = best.optString("dexId", "unknown");
            out.pairAddress = best.optString("pairAddress", "");
            out.liquidityUsd = Math.max(0.0d, liq);
            JSONObject base = best.optJSONObject("baseToken");
            JSONObject quote = best.optJSONObject("quoteToken");
            if (base != null && mint.equals(base.optString("address"))) {
                out.symbol = base.optString("symbol", Db.shortAddr(mint));
            } else if (quote != null) {
                out.symbol = quote.optString("symbol", Db.shortAddr(mint));
            }
            if (out.symbol == null || out.symbol.isEmpty()) {
                out.symbol = Db.shortAddr(mint);
            }
            this.cache.put(mint, out);
        } catch (Exception e) {
            out.error = e.getMessage();
            out.priceUsd = Double.NaN;
            out.symbol = Db.shortAddr(mint);
        }
        return out;
    }

    JSONArray heliusEnhanced(String wallet, int limit) throws Exception {
        String key = Prefs.heliusKey(this.context);
        if (key.isEmpty()) {
            return new JSONArray();
        }
        String u = "https://api.helius.xyz/v0/addresses/" + wallet + "/transactions?api-key=" + URLEncoder.encode(key, "UTF-8") + "&limit=" + Math.max(1, Math.min(100, limit));
        Object v = getAny(u, 15000, null);
        return v instanceof JSONArray ? (JSONArray) v : new JSONArray();
    }

    JSONArray cieloSwaps(String wallet, int limit) throws Exception {
        String key = Prefs.cieloKey(this.context);
        if (key.isEmpty()) {
            return new JSONArray();
        }
        String u = "https://feed-api.cielo.finance/api/v1/feed?wallet=" + URLEncoder.encode(wallet, "UTF-8") + "&chains=solana&txTypes=swap&limit=" + Math.max(1, Math.min(100, limit));
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-API-KEY", key);
        headers.put("accept", "application/json");
        Object raw = getAny(u, 15000, headers);
        if (!(raw instanceof JSONObject)) {
            return new JSONArray();
        }
        JSONObject root = (JSONObject) raw;
        JSONObject data = root.optJSONObject("data");
        JSONArray items = data == null ? null : data.optJSONArray("items");
        return items == null ? new JSONArray() : items;
    }

    JSONObject heliusParsedSignature(String signature) throws Exception {
        String key = Prefs.heliusKey(this.context);
        if (key.isEmpty()) {
            return null;
        }
        JSONObject body = new JSONObject().put("transactions", new JSONArray().put(signature));
        JSONObject root = post("https://mainnet.helius-rpc.com/v1/parsed-events/transactions?api-key=" + URLEncoder.encode(key, "UTF-8"), body, 15000);
        JSONArray arr = root.optJSONArray("transactions");
        if (arr == null) {
            arr = root.optJSONArray("result");
        }
        if (arr == null || arr.length() <= 0) {
            return null;
        }
        return arr.optJSONObject(0);
    }

    double historicalSolUsd(long timeMs) {
        JSONArray k;
        if (timeMs <= 0) {
            return Double.NaN;
        }
        long minute = (timeMs / 60000) * 60000;
        Double hit = this.solHistoryCache.get(Long.valueOf(minute));
        if (hit != null) {
            return hit.doubleValue();
        }
        try {
            String u = "https://api.binance.com/api/v3/klines?symbol=SOLUSDT&interval=1m&startTime=" + minute + "&endTime=" + (59999 + minute) + "&limit=1";
            Object raw = getAny(u, 10000, null);
            if (raw instanceof JSONArray) {
                JSONArray rows = (JSONArray) raw;
                if (rows.length() > 0 && (k = rows.optJSONArray(0)) != null && k.length() > 4) {
                    double px = num(k.opt(4));
                    if (px > 0.0d) {
                        this.solHistoryCache.put(Long.valueOf(minute), Double.valueOf(px));
                        return px;
                    }
                }
            }
        } catch (Exception e) {
        }
        return Double.NaN;
    }

    double deriveUsdFromSignal(TradeSignal e) {
        if (e == null) {
            return Double.NaN;
        }
        if (Double.isFinite(e.signalPriceUsd) && e.signalPriceUsd > 0.0d) {
            return e.signalPriceUsd;
        }
        if ("SOL".equalsIgnoreCase(e.quoteUnit) && Double.isFinite(e.quoteAmount) && e.quoteAmount > 0.0d && e.tokenQty > 0.0d) {
            Market sol = tokenMarket("So11111111111111111111111111111111111111112", false);
            if (Double.isFinite(sol.priceUsd) && sol.priceUsd > 0.0d) {
                return (e.quoteAmount * sol.priceUsd) / e.tokenQty;
            }
        }
        return Double.NaN;
    }

    private static Object getAny(String endpoint, int timeout, Map<String, String> headers) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
        c.setConnectTimeout(timeout);
        c.setReadTimeout(timeout);
        c.setRequestMethod("GET");
        c.setRequestProperty("Accept", "application/json");
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                c.setRequestProperty(e.getKey(), e.getValue());
            }
        }
        int code = c.getResponseCode();
        InputStream is = (code < 200 || code >= 300) ? c.getErrorStream() : c.getInputStream();
        String txt = read(is);
        if (code < 200 || code >= 300) {
            throw new Exception("HTTP " + code + " " + txt);
        }
        String t = txt.trim();
        return t.startsWith("[") ? new JSONArray(t) : new JSONObject(t);
    }

    private static JSONObject post(String endpoint, JSONObject body, int timeout) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
        c.setConnectTimeout(timeout);
        c.setReadTimeout(timeout);
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setDoOutput(true);
        byte[] b = body.toString().getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(b.length);
        OutputStream os = c.getOutputStream();
        try {
            os.write(b);
            if (os != null) {
                os.close();
            }
            int code = c.getResponseCode();
            InputStream is = (code < 200 || code >= 300) ? c.getErrorStream() : c.getInputStream();
            String txt = read(is);
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

    private static JSONObject get(String endpoint, int timeout) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
        c.setConnectTimeout(timeout);
        c.setReadTimeout(timeout);
        c.setRequestMethod("GET");
        c.setRequestProperty("Accept", "application/json");
        int code = c.getResponseCode();
        InputStream is = (code < 200 || code >= 300) ? c.getErrorStream() : c.getInputStream();
        String txt = read(is);
        if (code < 200 || code >= 300) {
            throw new Exception("HTTP " + code + " " + txt);
        }
        return new JSONObject(txt);
    }

    private static boolean isDnsError(String msg) {
        if (msg == null) {
            return false;
        }
        String s = msg.toLowerCase(Locale.US);
        return s.contains("unable to resolve host") || s.contains("unknownhost") || s.contains("no address associated with hostname");
    }

    private static String host(String endpoint) {
        try {
            return new URL(endpoint).getHost();
        } catch (Exception e) {
            return endpoint;
        }
    }

    private static String shortErr(String msg) {
        if (msg == null) {
            return "unknown";
        }
        String msg2 = msg.replace('\n', ' ');
        return msg2.length() > 90 ? msg2.substring(0, 87) + "…" : msg2;
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        while (true) {
            try {
                String line = r.readLine();
                if (line == null) {
                    r.close();
                    return b.toString();
                }
                b.append(line);
            } catch (Throwable th) {
                try {
                    r.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
                throw th;
            }
        }
    }

    static double num(Object v) {
        if (v != null && v != JSONObject.NULL) {
            try {
                return v instanceof Number ? ((Number) v).doubleValue() : Double.parseDouble(String.valueOf(v));
            } catch (Exception e) {
            }
        }
        return 0.0d;
    }
}
