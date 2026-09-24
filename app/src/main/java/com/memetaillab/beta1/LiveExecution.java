package com.memetaillab.beta1;

import android.content.Context;
import java.net.URLEncoder;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

class LiveExecution {
    private final ApiBudget budget;
    private final Context c;
    private final SecureBotWallet wallet;

    LiveExecution(Context c) {
        this.c = c.getApplicationContext();
        this.wallet = new SecureBotWallet(c);
        this.budget = new ApiBudget(c);
    }

    boolean ready() {
        return this.wallet.exists() && !Prefs.swapApi(this.c).trim().isEmpty();
    }

    String readiness() {
        return !this.wallet.exists() ? "Create or import a dedicated bot wallet" : Prefs.swapApi(this.c).trim().isEmpty() ? "Add a QuickNode Swap API endpoint" : "Ready";
    }

    String walletAddress() {
        return this.wallet.publicKey();
    }

    LiveSwap buy(String book, String mint, double stakeUsd) throws Exception {
        if (!ready()) {
            throw new Exception(readiness());
        }
        double solUsd = solPriceUsd();
        if (!Double.isFinite(solUsd) || solUsd <= 0.0d) {
            throw new Exception("SOL/USD unavailable");
        }
        long lamports = Math.max(1L, (long) Math.floor((stakeUsd / solUsd) * 1.0E9d));
        long bal = solBalanceLamports(this.wallet.publicKey());
        long reserve = Math.max(2500000L, Prefs.livePriorityMaxLamports(this.c) + 1500000);
        if (bal < lamports + reserve) {
            throw new Exception("Bot wallet SOL balance too low for stake + fees/rent");
        }
        LiveSwap s = build(book, true, mint, lamports);
        s.estimatedInputUsd = stakeUsd;
        if (s.priceImpactPct > Prefs.liveMaxImpactPct(this.c)) {
            throw new Exception(String.format(Locale.US, "Price impact %.2f%% exceeds %.2f%% limit", Double.valueOf(s.priceImpactPct), Double.valueOf(Prefs.liveMaxImpactPct(this.c))));
        }
        signSendConfirm(s);
        hydrateActualFill(s, mint, true);
        if (s.actualTokenDeltaRaw > 0) {
            s.outAmountRaw = s.actualTokenDeltaRaw;
        }
        if (s.actualSolDeltaLamports > 0) {
            s.inAmountRaw = s.actualSolDeltaLamports;
            s.estimatedInputUsd = (s.actualSolDeltaLamports / 1.0E9d) * solUsd;
        }
        return s;
    }

    LiveSwap previewSell(String book, String mint, long rawAmount) throws Exception {
        LiveSwap s;
        if (!ready()) {
            throw new Exception(readiness());
        }
        try {
            s = build(book, false, mint, rawAmount);
        } catch (Exception first) {
            if (!"PUMPFUN".equals(book)) {
                throw first;
            }
            s = buildStandard("PUMPSWAP", false, mint, rawAmount);
        }
        double solUsd = solPriceUsd();
        s.estimatedOutputUsd = (s.outAmountRaw / 1.0E9d) * solUsd;
        return s;
    }

    LiveSwap sell(String book, String mint, long rawAmount) throws Exception {
        LiveSwap s;
        if (!ready()) {
            throw new Exception(readiness());
        }
        if (rawAmount <= 0) {
            throw new Exception("Sell amount is zero");
        }
        try {
            s = build(book, false, mint, rawAmount);
        } catch (Exception first) {
            if (!"PUMPFUN".equals(book)) {
                throw first;
            }
            s = buildStandard("PUMPSWAP", false, mint, rawAmount);
        }
        if (s.priceImpactPct > Prefs.liveMaxImpactPct(this.c)) {
            throw new Exception(String.format(Locale.US, "Price impact %.2f%% exceeds %.2f%% limit", Double.valueOf(s.priceImpactPct), Double.valueOf(Prefs.liveMaxImpactPct(this.c))));
        }
        signSendConfirm(s);
        hydrateActualFill(s, mint, false);
        if (s.actualTokenDeltaRaw > 0) {
            s.inAmountRaw = s.actualTokenDeltaRaw;
        }
        if (s.actualSolDeltaLamports > 0) {
            s.outAmountRaw = s.actualSolDeltaLamports;
        }
        double solUsd = solPriceUsd();
        s.estimatedOutputUsd = (s.outAmountRaw / 1.0E9d) * solUsd;
        return s;
    }

    private LiveSwap build(String book, boolean buy, String mint, long amount) throws Exception {
        return "PUMPFUN".equals(book) ? buildPumpFun(buy, mint, amount) : buildStandard(book, buy, mint, amount);
    }

    private LiveSwap buildStandard(String book, boolean buy, String mint, long amount) throws Exception {
        String endpoint = base();
        String input = buy ? "So11111111111111111111111111111111111111112" : mint;
        String output = buy ? mint : "So11111111111111111111111111111111111111112";
        String dexes = "RAYDIUM".equals(book) ? "Raydium,Raydium CP,Raydium CLMM,Raydium Launchlab" : "Pump.fun Amm";
        String q = endpoint + "/quote?inputMint=" + enc(input) + "&outputMint=" + enc(output) + "&amount=" + amount + "&slippageBps=" + Prefs.liveSlippageBps(this.c) + "&onlyDirectRoutes=true&dexes=" + enc(dexes);
        JSONObject quote = getTrading(q);
        if (quote.has("error")) {
            throw new Exception("Quote: " + quote.optString("error"));
        }
        String out = quote.optString("outAmount", "0");
        long outRaw = parseLong(out);
        if (outRaw <= 0) {
            throw new Exception("No executable " + book + " quote");
        }
        double impact = Network.num(quote.opt("priceImpactPct")) * 100.0d;
        JSONObject priority = new JSONObject().put("priorityLevelWithMaxLamports", new JSONObject().put("priorityLevel", Prefs.livePriorityLevel(this.c)).put("maxLamports", Prefs.livePriorityMaxLamports(this.c)).put("global", false));
        JSONObject body = new JSONObject().put("userPublicKey", this.wallet.publicKey()).put("quoteResponse", quote).put("wrapAndUnwrapSol", true).put("dynamicComputeUnitLimit", true).put("prioritizationFeeLamports", priority);
        JSONObject tx = postTrading(endpoint + "/swap", body);
        String b64 = tx.optString("swapTransaction", "");
        if (b64.isEmpty()) {
            throw new Exception("Swap API returned no transaction");
        }
        LiveSwap s = new LiveSwap();
        s.ok = true;
        s.txBase64 = b64;
        s.inAmountRaw = amount;
        s.outAmountRaw = outRaw;
        s.priceImpactPct = impact;
        s.routeLabel = routeLabel(quote);
        return s;
    }

    private LiveSwap buildPumpFun(boolean buy, String mint, long amount) throws Exception {
        String endpoint = base();
        String type = buy ? "BUY" : "SELL";
        String q = endpoint + "/pump-fun/quote?mint=" + enc(mint) + "&type=" + type + "&amount=" + amount + "&commitment=confirmed";
        JSONObject qr = getTrading(q);
        JSONObject quote = qr.optJSONObject("quote");
        if (quote == null) {
            quote = qr;
        }
        long outRaw = parseLong(quote.optString("outAmount", "0"));
        JSONObject meta = quote.optJSONObject("meta");
        if (!buy && meta != null && meta.optBoolean("isCompleted", false)) {
            throw new Exception("Pump.fun curve completed; use PumpSwap exit");
        }
        JSONObject body = new JSONObject().put("wallet", this.wallet.publicKey()).put("type", type).put("mint", mint).put("inAmount", String.valueOf(amount)).put("priorityFeeLevel", Prefs.livePriorityLevel(this.c)).put("slippageBps", String.valueOf(Prefs.liveSlippageBps(this.c))).put("commitment", "confirmed");
        JSONObject tx = postTrading(endpoint + "/pump-fun/swap", body);
        String b64 = tx.optString("tx", "");
        if (b64.isEmpty()) {
            throw new Exception("Pump.fun API returned no transaction");
        }
        LiveSwap s = new LiveSwap();
        s.ok = true;
        s.txBase64 = b64;
        s.inAmountRaw = amount;
        s.outAmountRaw = outRaw;
        s.priceImpactPct = 0.0d;
        s.routeLabel = "Pump.fun";
        return s;
    }

    private void signSendConfirm(LiveSwap s) throws Exception {
        String signed = this.wallet.signSolanaTransactionBase64(s.txBase64);
        if (!this.budget.allow("HELIUS", 1)) {
            throw new Exception("Helius monthly API budget is currently throttling sendTransaction");
        }
        JSONObject req = new JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", "sendTransaction").put("params", new JSONArray().put(signed).put(new JSONObject().put("encoding", "base64").put("skipPreflight", false).put("maxRetries", 3).put("preflightCommitment", "processed")));
        JSONObject r = Network.postJson(new Network(this.c).rpcEndpoint(), req, 20000, null);
        if (r.has("error")) {
            throw new Exception("sendTransaction: " + r.get("error"));
        }
        s.signature = r.optString("result", "");
        if (s.signature.isEmpty()) {
            throw new Exception("RPC returned no signature");
        }
        waitForConfirmation(s.signature);
    }

    private void waitForConfirmation(String sig) throws Exception {
        for (int i = 0; i < 30; i++) {
            if (this.budget.allow("HELIUS", 1)) {
                JSONObject req = new JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", "getSignatureStatuses").put("params", new JSONArray().put(new JSONArray().put(sig)).put(new JSONObject().put("searchTransactionHistory", true)));
                JSONObject r = Network.postJson(new Network(this.c).rpcEndpoint(), req, 10000, null);
                JSONArray v = r.optJSONObject("result") == null ? null : r.optJSONObject("result").optJSONArray("value");
                JSONObject st = v != null ? v.optJSONObject(0) : null;
                if (st != null) {
                    if (st.has("err") && !st.isNull("err")) {
                        throw new Exception("Transaction failed: " + st.opt("err"));
                    }
                    String cs = st.optString("confirmationStatus", "");
                    if ("confirmed".equals(cs) || "finalized".equals(cs)) {
                        return;
                    }
                }
                Thread.sleep(1000L);
            } else {
                Thread.sleep(500L);
            }
        }
        throw new Exception("Transaction sent but confirmation timed out: " + sig);
    }

    private void hydrateActualFill(LiveSwap s, String mint, boolean buy) {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                if (!this.budget.allow("HELIUS", 1)) {
                    Thread.sleep(500L);
                    continue;
                }
                JSONObject opts = new JSONObject().put("encoding", "jsonParsed").put("commitment", "confirmed").put("maxSupportedTransactionVersion", 0);
                JSONObject req = new JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", "getTransaction").put("params", new JSONArray().put(s.signature).put(opts));
                JSONObject rsp = Network.postJson(new Network(this.c).rpcEndpoint(), req, 12000, null);
                JSONObject tx = rsp.optJSONObject("result");
                if (tx == null) {
                    Thread.sleep(500L);
                    continue;
                }
                JSONObject meta = tx.optJSONObject("meta");
                JSONObject tr = tx.optJSONObject("transaction");
                JSONObject msg = tr == null ? null : tr.optJSONObject("message");
                JSONArray keys = msg == null ? null : msg.optJSONArray("accountKeys");
                JSONArray pre = meta == null ? null : meta.optJSONArray("preBalances");
                JSONArray post = meta == null ? null : meta.optJSONArray("postBalances");
                int walletIndex = -1;
                if (keys != null) {
                    for (int i = 0; i < keys.length(); i++) {
                        Object k = keys.opt(i);
                        String pk = k instanceof JSONObject ? ((JSONObject) k).optString("pubkey", "") : String.valueOf(k);
                        if (this.wallet.publicKey().equals(pk)) {
                            walletIndex = i;
                            break;
                        }
                    }
                }
                if (walletIndex >= 0 && pre != null && post != null && walletIndex < pre.length() && walletIndex < post.length()) {
                    long preLamports = pre.optLong(walletIndex, 0L);
                    long postLamports = post.optLong(walletIndex, 0L);
                    s.actualSolDeltaLamports = buy ? Math.max(0L, preLamports - postLamports) : Math.max(0L, postLamports - preLamports);
                }
                s.networkFeeLamports = meta == null ? 0L : meta.optLong("fee", 0L);
                long preToken = tokenBalance(meta == null ? null : meta.optJSONArray("preTokenBalances"), mint, this.wallet.publicKey());
                long postToken = tokenBalance(meta == null ? null : meta.optJSONArray("postTokenBalances"), mint, this.wallet.publicKey());
                s.actualTokenDeltaRaw = buy ? Math.max(0L, postToken - preToken) : Math.max(0L, preToken - postToken);
                return;
            } catch (Exception e) {
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException e2) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static long tokenBalance(JSONArray arr, String mint, String owner) {
        JSONObject ui;
        long total = 0;
        if (arr == null) {
            return 0L;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject x = arr.optJSONObject(i);
            if (x != null && mint.equals(x.optString("mint", ""))) {
                String o = x.optString("owner", "");
                if ((o.isEmpty() || owner.equals(o)) && (ui = x.optJSONObject("uiTokenAmount")) != null) {
                    total += parseLong(ui.optString("amount", "0"));
                }
            }
        }
        return total;
    }

    long solBalanceLamports(String owner) throws Exception {
        if (!this.budget.allow("HELIUS", 1)) {
            throw new Exception("Helius API budget throttled balance query");
        }
        JSONObject req = new JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", "getBalance").put("params", new JSONArray().put(owner).put(new JSONObject().put("commitment", "confirmed")));
        JSONObject r = Network.postJson(new Network(this.c).rpcEndpoint(), req, 10000, null);
        if (r.has("error")) {
            throw new Exception(r.get("error").toString());
        }
        if (r.optJSONObject("result") == null) {
            return 0L;
        }
        return r.optJSONObject("result").optLong("value", 0L);
    }

    int tokenDecimals(String mint) throws Exception {
        if (!this.budget.allow("HELIUS", 1)) {
            throw new Exception("Helius API budget throttled token metadata query");
        }
        JSONObject req = new JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", "getTokenSupply").put("params", new JSONArray().put(mint).put(new JSONObject().put("commitment", "confirmed")));
        JSONObject r = Network.postJson(new Network(this.c).rpcEndpoint(), req, 10000, null);
        JSONObject value = r.optJSONObject("result") != null ? r.optJSONObject("result").optJSONObject("value") : null;
        if (value == null) {
            return 6;
        }
        return value.optInt("decimals", 6);
    }

    double solPriceUsd() throws Exception {
        Object o = Network.getAny("https://api.dexscreener.com/token-pairs/v1/solana/So11111111111111111111111111111111111111112", 10000, null);
        JSONArray a = o instanceof JSONArray ? (JSONArray) o : null;
        if (a == null || a.length() == 0) {
            throw new Exception("DEX Screener returned no SOL pairs");
        }
        double bestLiq = -1.0d;
        double price = Double.NaN;
        for (int i = 0; i < a.length(); i++) {
            JSONObject x = a.optJSONObject(i);
            if (x != null) {
                double p = Network.num(x.opt("priceUsd"));
                JSONObject l = x.optJSONObject("liquidity");
                double liq = l == null ? 0.0d : Network.num(l.opt("usd"));
                if (p > 0.0d && liq > bestLiq) {
                    bestLiq = liq;
                    price = p;
                }
            }
        }
        return price;
    }

    private JSONObject getTrading(String url) throws Exception {
        if (!this.budget.allow("TRADING", 1)) {
            throw new Exception("Trading API monthly budget/rate limit reached");
        }
        return Network.getJson(url, 15000, null);
    }

    private JSONObject postTrading(String url, JSONObject body) throws Exception {
        if (!this.budget.allow("TRADING", 1)) {
            throw new Exception("Trading API monthly budget/rate limit reached");
        }
        return Network.postJson(url, body, 20000, null);
    }

    private String base() {
        String x = Prefs.swapApi(this.c).trim();
        while (x.endsWith("/")) {
            x = x.substring(0, x.length() - 1);
        }
        return x;
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String routeLabel(JSONObject q) {
        JSONArray r = q.optJSONArray("routePlan");
        if (r == null || r.length() == 0) {
            return "";
        }
        JSONObject x = r.optJSONObject(0);
        JSONObject si = x == null ? null : x.optJSONObject("swapInfo");
        return si == null ? "" : si.optString("label", "");
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
