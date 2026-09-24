package com.benknight.mwsl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

class SolanaParser {
    SolanaParser() {
    }

    static List<TradeSignal> parseTrades(String wallet, JSONObject sigInfo, JSONObject tx) {
        List<TradeSignal> out = new ArrayList<>();
        if (tx == null) {
            return out;
        }
        JSONObject meta = tx.optJSONObject("meta");
        if (meta == null) {
            return out;
        }
        if (!meta.isNull("err") && meta.opt("err") != JSONObject.NULL) {
            return out;
        }
        Map<String, Double> pre = new HashMap<>();
        Map<String, Double> post = new HashMap<>();
        accTokenBalances(meta.optJSONArray("preTokenBalances"), wallet, pre);
        accTokenBalances(meta.optJSONArray("postTokenBalances"), wallet, post);
        Set<String> mints = new LinkedHashSet<>();
        mints.addAll(pre.keySet());
        mints.addAll(post.keySet());
        List<Delta> deltas = new ArrayList<>();
        for (String mint : mints) {
            double a = pre.containsKey(mint) ? pre.get(mint).doubleValue() : 0.0d;
            double b = post.containsKey(mint) ? post.get(mint).doubleValue() : 0.0d;
            double d = b - a;
            if (Math.abs(d) > 1.0E-12d) {
                deltas.add(new Delta(mint, d, a, b));
            }
        }
        if (deltas.isEmpty()) {
            return out;
        }
        List<String> keys = accountKeys(tx);
        int wi = keys.indexOf(wallet);
        double solDelta = 0.0d;
        JSONArray preBal = meta.optJSONArray("preBalances");
        JSONArray postBal = meta.optJSONArray("postBalances");
        if (wi >= 0 && preBal != null && postBal != null && wi < preBal.length() && wi < postBal.length()) {
            solDelta = (postBal.optDouble(wi) - preBal.optDouble(wi)) / 1.0E9d;
        }
        Delta quote = find(deltas, "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v");
        if (quote == null) {
            quote = find(deltas, "Es9vMFrzaCERmJfrF4H2FYDkgFdmHqkV6P5dY1Y9Fgr");
        }
        if (quote == null) {
            quote = find(deltas, "So11111111111111111111111111111111111111112");
        }
        List<Delta> non = new ArrayList<>();
        for (Delta d2 : deltas) {
            if (!"EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v".equals(d2.mint) && !"Es9vMFrzaCERmJfrF4H2FYDkgFdmHqkV6P5dY1Y9Fgr".equals(d2.mint) && !"So11111111111111111111111111111111111111112".equals(d2.mint)) {
                non.add(d2);
            }
        }
        if (non.size() != 1) {
            return out;
        }
        Venue venue = detectVenue(programIds(tx));
        if (venue.dex.isEmpty() && venue.router.isEmpty()) {
            return out;
        }
        long blockTime = tx.optLong("blockTime", sigInfo != null ? sigInfo.optLong("blockTime", 0L) : 0L);
        if (blockTime <= 0) {
            blockTime = System.currentTimeMillis() / 1000;
        }
        long t = blockTime * 1000;
        String signature = sigInfo != null ? sigInfo.optString("signature", "") : "";
        long slot = tx.optLong("slot", sigInfo != null ? sigInfo.optLong("slot", 0L) : 0L);
        for (Delta x : non) {
            String side = x.delta > 0.0d ? "BUY" : "SELL";
            String quoteMint = "";
            double quoteAmount = Double.NaN;
            String quoteUnit;
            double signalPrice = Double.NaN;
            if (quote != null && Math.signum(quote.delta) != Math.signum(x.delta)) {
                quoteMint = quote.mint;
                quoteAmount = Math.abs(quote.delta);
                if ("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v".equals(quote.mint)) {
                    quoteUnit = "USDC";
                    signalPrice = quoteAmount / Math.abs(x.delta);
                } else if ("Es9vMFrzaCERmJfrF4H2FYDkgFdmHqkV6P5dY1Y9Fgr".equals(quote.mint)) {
                    quoteUnit = "USDT";
                    signalPrice = quoteAmount / Math.abs(x.delta);
                } else {
                    quoteUnit = "SOL";
                }
            } else if (solDelta != 0.0d && Math.signum(solDelta) != Math.signum(x.delta)) {
                quoteMint = "SOL";
                quoteAmount = Math.abs(solDelta);
                quoteUnit = "SOL";
            } else {
                continue;
            }
            TradeSignal e = new TradeSignal();
            e.id = "sig:" + signature + ":" + x.mint + ":" + side;
            e.signature = signature;
            e.time = t;
            e.side = side;
            e.mint = x.mint;
            e.tokenQty = Math.abs(x.delta);
            e.preTokenQty = x.pre;
            e.postTokenQty = x.post;
            e.sellFraction = ("SELL".equals(side) && x.pre > 0.0d) ? Math.min(1.0d, Math.abs(x.delta) / x.pre) : Double.NaN;
            e.quoteMint = quoteMint;
            e.quoteAmount = quoteAmount;
            e.quoteUnit = quoteUnit;
            e.signalPriceUsd = signalPrice;
            e.slot = slot;
            e.dex.addAll(venue.dex);
            e.router.addAll(venue.router);
            out.add(e);
        }
        return out;
    }

    static String diagnose(String wallet, JSONObject tx) {
        if (tx == null) {
            return "transaction payload unavailable";
        }
        JSONObject meta = tx.optJSONObject("meta");
        if (meta == null) {
            return "missing transaction meta";
        }
        if (!meta.isNull("err") && meta.opt("err") != JSONObject.NULL) {
            return "failed transaction";
        }
        Map<String, Double> pre = new HashMap<>();
        Map<String, Double> post = new HashMap<>();
        accTokenBalances(meta.optJSONArray("preTokenBalances"), wallet, pre);
        accTokenBalances(meta.optJSONArray("postTokenBalances"), wallet, post);
        Set<String> mints = new LinkedHashSet<>();
        mints.addAll(pre.keySet());
        mints.addAll(post.keySet());
        int changed = 0;
        int nonQuote = 0;
        int quoteChanged = 0;
        for (String mint : mints) {
            double b = post.containsKey(mint) ? post.get(mint).doubleValue() : 0.0d;
            double a = pre.containsKey(mint) ? pre.get(mint).doubleValue() : 0.0d;
            if (Math.abs(b - a) <= 1.0E-12d) {
                continue;
            }
            changed++;
            if ("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v".equals(mint) || "Es9vMFrzaCERmJfrF4H2FYDkgFdmHqkV6P5dY1Y9Fgr".equals(mint) || "So11111111111111111111111111111111111111112".equals(mint)) {
                quoteChanged++;
            } else {
                nonQuote++;
            }
        }
        List<String> keys = accountKeys(tx);
        int wi = keys.indexOf(wallet);
        double solDelta = 0.0d;
        JSONArray preBal = meta.optJSONArray("preBalances");
        JSONArray postBal = meta.optJSONArray("postBalances");
        if (wi >= 0 && preBal != null && postBal != null && wi < preBal.length() && wi < postBal.length()) {
            solDelta = (postBal.optDouble(wi) - preBal.optDouble(wi)) / 1.0E9d;
        }
        Set<String> ids = programIds(tx);
        List<String> known = new ArrayList<>();
        for (String id : ids) {
            VenueDef d = Config.DEX_PROGRAMS.get(id);
            if (d != null && !known.contains(d.name)) {
                known.add(d.name);
            }
        }
        String programs = known.isEmpty() ? "no known DEX program" : PaperEngine.join(known);
        String unknown = known.isEmpty() ? " · other programs " + unknownProgramSummary(tx) : "";
        if (changed == 0) {
            return "no owned SPL-token balance change · " + programs + unknown + (Math.abs(solDelta) > 1.0E-9d ? " · SOL Δ " + String.format(Locale.US, "%.6f", Double.valueOf(solDelta)) : "");
        }
        if (nonQuote == 0) {
            return "only quote-token balance changes · " + programs + unknown;
        }
        if (quoteChanged == 0 && Math.abs(solDelta) <= 1.0E-9d) {
            return "token changed but no opposite SOL/USDC/USDT flow · " + programs + unknown;
        }
        if (nonQuote > 1) {
            return "multiple non-quote token deltas; route/LP/batch may be ambiguous · " + programs + unknown;
        }
        if (known.isEmpty()) {
            return "swap-like balance changes but DEX program is not in local registry · other programs " + unknownProgramSummary(tx);
        }
        return "swap-like balances detected but direction/quote pairing was ambiguous · " + programs;
    }

    static List<String> signerKeys(JSONObject tx) {
        List<String> out = new ArrayList<>();
        if (tx == null) {
            return out;
        }
        JSONObject tr = tx.optJSONObject("transaction");
        JSONObject msg = tr == null ? null : tr.optJSONObject("message");
        JSONArray ks = msg != null ? msg.optJSONArray("accountKeys") : null;
        if (ks == null) {
            return out;
        }
        for (int i = 0; i < ks.length(); i++) {
            Object k = ks.opt(i);
            if (k instanceof JSONObject) {
                JSONObject o = (JSONObject) k;
                if (o.optBoolean("signer", false)) {
                    String p = o.optString("pubkey", "");
                    if (!p.isEmpty()) {
                        out.add(p);
                    }
                }
            } else if ((k instanceof String) && i == 0) {
                out.add((String) k);
            }
        }
        return out;
    }

    static List<String> knownPrograms(JSONObject tx) {
        List<String> out = new ArrayList<>();
        for (String id : programIds(tx)) {
            VenueDef d = Config.DEX_PROGRAMS.get(id);
            if (d != null && !out.contains(d.name)) {
                out.add(d.name);
            }
        }
        return out;
    }

    private static void accTokenBalances(JSONArray a, String wallet, Map<String, Double> out) {
        if (a == null) {
            return;
        }
        for (int i = 0; i < a.length(); i++) {
            JSONObject x = a.optJSONObject(i);
            if (x != null && wallet.equals(x.optString("owner"))) {
                String mint = x.optString("mint", "");
                JSONObject ui = x.optJSONObject("uiTokenAmount");
                double amount = 0.0d;
                if (ui != null) {
                    String s = ui.optString("uiAmountString", "");
                    if (s.isEmpty()) {
                        amount = ui.optDouble("uiAmount", 0.0d);
                    } else {
                        try {
                            amount = Double.parseDouble(s);
                        } catch (Exception e) {
                        }
                    }
                }
                out.put(mint, Double.valueOf((out.containsKey(mint) ? out.get(mint).doubleValue() : 0.0d) + amount));
            }
        }
    }

    private static Delta find(List<Delta> ds, String mint) {
        for (Delta d : ds) {
            if (mint.equals(d.mint)) {
                return d;
            }
        }
        return null;
    }

    private static List<String> accountKeys(JSONObject tx) {
        List<String> out = new ArrayList<>();
        JSONObject tr = tx.optJSONObject("transaction");
        JSONObject msg = tr == null ? null : tr.optJSONObject("message");
        JSONArray ks = msg != null ? msg.optJSONArray("accountKeys") : null;
        if (ks != null) {
            for (int i = 0; i < ks.length(); i++) {
                Object k = ks.opt(i);
                if (k instanceof String) {
                    out.add((String) k);
                } else if (k instanceof JSONObject) {
                    out.add(((JSONObject) k).optString("pubkey", ""));
                }
            }
        }
        return out;
    }

    static Set<String> invokedProgramIds(JSONObject tx) {
        Set<String> ids = new LinkedHashSet<>();
        if (tx == null) {
            return ids;
        }
        List<String> keys = accountKeys(tx);
        JSONObject tr = tx.optJSONObject("transaction");
        JSONObject msg = tr == null ? null : tr.optJSONObject("message");
        collectPrograms(msg == null ? null : msg.optJSONArray("instructions"), ids, keys);
        JSONObject meta = tx.optJSONObject("meta");
        JSONArray inn = meta == null ? null : meta.optJSONArray("innerInstructions");
        if (inn != null) {
            for (int i = 0; i < inn.length(); i++) {
                JSONObject g = inn.optJSONObject(i);
                if (g != null) {
                    collectPrograms(g.optJSONArray("instructions"), ids, keys);
                }
            }
        }
        JSONArray logs = meta != null ? meta.optJSONArray("logMessages") : null;
        if (logs != null) {
            for (int i2 = 0; i2 < logs.length(); i2++) {
                String line = logs.optString(i2, "");
                if (line.startsWith("Program ")) {
                    int end = line.indexOf(32, 8);
                    if (end < 0) {
                        end = line.length();
                    }
                    String id = line.substring(8, end).trim();
                    if (id.length() >= 32 && id.length() <= 50 && !"log:".equals(id)) {
                        ids.add(id);
                    }
                }
            }
        }
        return ids;
    }

    private static Set<String> programIds(JSONObject tx) {
        return invokedProgramIds(tx);
    }

    private static void collectPrograms(JSONArray arr, Set<String> ids, List<String> keys) {
        int idx;
        if (arr == null) {
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject ix = arr.optJSONObject(i);
            if (ix != null) {
                String p = ix.optString("programId", "");
                if (p.isEmpty() && ix.has("programIdIndex") && (idx = ix.optInt("programIdIndex", -1)) >= 0 && idx < keys.size()) {
                    p = keys.get(idx);
                }
                if (!p.isEmpty()) {
                    ids.add(p);
                }
            }
        }
    }

    private static boolean commonProgram(String id) {
        return "11111111111111111111111111111111".equals(id) || "ComputeBudget111111111111111111111111111111".equals(id) || "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA".equals(id) || "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb".equals(id) || "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL".equals(id) || "MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr".equals(id) || "Ed25519SigVerify111111111111111111111111111".equals(id) || "KeccakSecp256k11111111111111111111111111111".equals(id) || "BGUMAp9Gq7iTEuizy4pqaxsTyUCBK68MDfK752saRPUY".equals(id) || "cmtDvXumGCrqC1Age74AVPhSRVXJMd8PJS91L8KbNCK".equals(id) || "noopb9bkMVfRPU8AsbpTUg8AQkHtKwMYZiFUjNRtMmV".equals(id);
    }

    static Set<String> unknownProgramIds(JSONObject tx) {
        Set<String> out = new LinkedHashSet<>();
        for (String id : invokedProgramIds(tx)) {
            if (!Config.DEX_PROGRAMS.containsKey(id) && !commonProgram(id)) {
                out.add(id);
            }
        }
        return out;
    }

    static Map<String, Integer> changedTokenOwners(JSONObject tx, String watchedWallet) {
        Map<String, Map<String, Double>> pre = new HashMap<>();
        Map<String, Map<String, Double>> post = new HashMap<>();
        JSONObject meta = tx == null ? null : tx.optJSONObject("meta");
        if (meta == null) {
            return new HashMap();
        }
        accAllTokenBalances(meta.optJSONArray("preTokenBalances"), pre);
        accAllTokenBalances(meta.optJSONArray("postTokenBalances"), post);
        Set<String> owners = new LinkedHashSet<>();
        owners.addAll(pre.keySet());
        owners.addAll(post.keySet());
        Map<String, Integer> out = new HashMap<>();
        for (String owner : owners) {
            if (owner != null && !owner.isEmpty()) {
                if (!owner.equals(watchedWallet)) {
                    Map<String, Double> a = pre.get(owner);
                    Map<String, Double> b = post.get(owner);
                    Set<String> mints = new LinkedHashSet<>();
                    if (a != null) {
                        mints.addAll(a.keySet());
                    }
                    if (b != null) {
                        mints.addAll(b.keySet());
                    }
                    int changed = 0;
                    for (String mint : mints) {
                        double y = 0.0d;
                        double x = (a == null || !a.containsKey(mint)) ? 0.0d : a.get(mint).doubleValue();
                        if (b != null && b.containsKey(mint)) {
                            y = b.get(mint).doubleValue();
                        }
                        if (Math.abs(y - x) > 1.0E-12d) {
                            changed++;
                        }
                    }
                    if (changed > 0) {
                        out.put(owner, Integer.valueOf(changed));
                    }
                }
            }
        }
        return out;
    }

    private static void accAllTokenBalances(JSONArray a, Map<String, Map<String, Double>> out) {
        if (a == null) {
            return;
        }
        for (int i = 0; i < a.length(); i++) {
            JSONObject x = a.optJSONObject(i);
            if (x != null) {
                String owner = x.optString("owner", "");
                String mint = x.optString("mint", "");
                if (!owner.isEmpty() && !mint.isEmpty()) {
                    JSONObject ui = x.optJSONObject("uiTokenAmount");
                    double amount = 0.0d;
                    if (ui != null) {
                        String s = ui.optString("uiAmountString", "");
                        if (s.isEmpty()) {
                            amount = ui.optDouble("uiAmount", 0.0d);
                        } else {
                            try {
                                amount = Double.parseDouble(s);
                            } catch (Exception e) {
                            }
                        }
                    }
                    Map<String, Double> m = out.get(owner);
                    if (m == null) {
                        m = new HashMap();
                        out.put(owner, m);
                    }
                    m.put(mint, Double.valueOf((m.containsKey(mint) ? m.get(mint).doubleValue() : 0.0d) + amount));
                }
            }
        }
    }

    static String unknownProgramSummary(JSONObject tx) {
        StringBuilder b = new StringBuilder();
        int n = 0;
        Iterator<String> it = unknownProgramIds(tx).iterator();
        while (true) {
            if (!it.hasNext()) {
                break;
            }
            String id = it.next();
            int n2 = n + 1;
            if (n >= 5) {
                b.append(" …");
                break;
            }
            if (b.length() > 0) {
                b.append(", ");
            }
            b.append(Db.shortAddr(id));
            n = n2;
        }
        return b.length() == 0 ? "none" : b.toString();
    }

    private static class Delta {
        final double delta;
        final String mint;
        final double post;
        final double pre;

        Delta(String m, double d, double p, double q) {
            this.mint = m;
            this.delta = d;
            this.pre = p;
            this.post = q;
        }
    }

    private static Venue detectVenue(Set<String> ids) {
        List<String> list;
        Venue v = new Venue();
        for (String id : ids) {
            VenueDef d = Config.DEX_PROGRAMS.get(id);
            if (d != null) {
                if ("router".equals(d.kind)) {
                    if (!v.router.contains(d.name)) {
                        list = v.router;
                        list.add(d.name);
                    }
                } else if (!v.dex.contains(d.name)) {
                    list = v.dex;
                    list.add(d.name);
                }
            }
        }
        return v;
    }

    private static class Venue {
        final List<String> dex;
        final List<String> router;

        private Venue() {
            this.dex = new ArrayList();
            this.router = new ArrayList();
        }
    }
}
