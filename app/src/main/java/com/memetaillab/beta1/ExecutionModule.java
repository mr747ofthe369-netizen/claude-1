package com.memetaillab.beta1;

import android.content.Context;
import java.net.URLEncoder;
import org.json.JSONArray;
import org.json.JSONObject;

class ExecutionModule {
    private final Context c;

    ExecutionModule(Context c) {
        this.c = c.getApplicationContext();
    }

    ExecPreview preview(String inputMint, String outputMint, long amountRaw, int slippageBps) {
        ExecPreview out = new ExecPreview();
        try {
            String host = Prefs.raySwap(this.c).trim();
            if (host.isEmpty()) {
                host = "https://transaction-v1.raydium.io";
            }
            String q = host + "/compute/swap-base-in?inputMint=" + enc(inputMint) + "&outputMint=" + enc(outputMint) + "&amount=" + amountRaw + "&slippageBps=" + Math.max(1, slippageBps) + "&txVersion=V0";
            JSONObject quote = Network.getJson(q, 15000, null);
            out.quoteJson = quote.toString(2);
            if (!quote.optBoolean("success", false)) {
                out.message = "Raydium quote failed: " + quote.optString("msg", "unknown");
                return out;
            }
            String wallet = Prefs.wallet(this.c).trim();
            if (wallet.isEmpty()) {
                out.ok = true;
                out.message = "Quote OK. Add a wallet public address to build the unsigned Raydium V0 transaction.";
                return out;
            }
            long micro = priorityFee();
            JSONObject body = new JSONObject().put("computeUnitPriceMicroLamports", String.valueOf(micro)).put("swapResponse", quote).put("txVersion", "V0").put("wallet", wallet).put("wrapSol", "So11111111111111111111111111111111111111112".equals(inputMint)).put("unwrapSol", "So11111111111111111111111111111111111111112".equals(outputMint));
            JSONObject tx = Network.postJson(host + "/transaction/swap-base-in", body, 20000, null);
            out.transactionJson = tx.toString(2);
            out.ok = tx.optBoolean("success", false);
            out.message = out.ok ? "Raydium quote + unsigned V0 transaction built successfully. Signing/broadcast is intentionally not automatic in Beta 1." : "Transaction build failed: " + tx.optString("msg", "unknown");
        } catch (Exception e) {
            out.message = "Execution preview error: " + Network.shortErr(e);
        }
        return out;
    }

    long priorityFee() {
        try {
            JSONObject j = Network.getJson(Prefs.rayApi(this.c) + "/main/auto-fee", 10000, null);
            JSONObject d = j.optJSONObject("data");
            JSONObject def = d != null ? d.optJSONObject("default") : null;
            if (def == null) {
                return 200000L;
            }
            Object h = def.opt("h");
            long v = Long.parseLong(String.valueOf(h));
            if (v > 0) {
                return v;
            }
            return 200000L;
        } catch (Exception e) {
            return 200000L;
        }
    }

    String broadcastSignedBase64(String signedTransactionBase64) throws Exception {
        if (signedTransactionBase64 == null || signedTransactionBase64.trim().isEmpty()) {
            throw new Exception("No signed transaction supplied");
        }
        JSONObject req = new JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", "sendTransaction").put("params", new JSONArray().put(signedTransactionBase64.trim()).put(new JSONObject().put("encoding", "base64").put("skipPreflight", false).put("maxRetries", 3)));
        JSONObject r = Network.postJson(new Network(this.c).rpcEndpoint(), req, 20000, null);
        if (r.has("error")) {
            throw new Exception(r.get("error").toString());
        }
        return r.optString("result", "");
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
