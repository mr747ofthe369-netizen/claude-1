package com.benknight.mwsl;

import org.json.JSONObject;

class EnhancedParser {
    static TradeSignal fromCielo(JSONObject x, String wallet) {
        if (x == null || !"swap".equalsIgnoreCase(x.optString("tx_type", "")) || !"solana".equalsIgnoreCase(x.optString("chain", "solana"))) {
            return null;
        }
        String a0 = x.optString("token0_address", "");
        String a1 = x.optString("token1_address", "");
        double q0 = x.optDouble("token0_amount", Double.NaN);
        double q1 = x.optDouble("token1_amount", Double.NaN);
        boolean quote0 = isQuote(a0);
        boolean quote1 = isQuote(a1);
        if (quote0 == quote1) {
            return null;
        }
        TradeSignal e = new TradeSignal();
        e.signature = x.optString("tx_hash", "");
        e.time = x.optLong("timestamp", 0L) * 1000;
        if (e.time <= 0) {
            e.time = System.currentTimeMillis();
        }
        e.slot = x.optLong("block", 0L);
        e.source = "cielo-enhanced";
        if (quote0 && !quote1) {
            e.side = "BUY";
            e.mint = a1;
            e.tokenQty = q1;
            e.quoteMint = normalizeQuote(a0);
            e.quoteAmount = q0;
            e.quoteUnit = quoteUnit(a0);
            e.symbol = x.optString("token1_symbol", "");
            e.signalPriceUsd = x.optDouble("token1_price_usd", Double.NaN);
        } else {
            e.side = "SELL";
            e.mint = a0;
            e.tokenQty = q0;
            e.quoteMint = normalizeQuote(a1);
            e.quoteAmount = q1;
            e.quoteUnit = quoteUnit(a1);
            e.symbol = x.optString("token0_symbol", "");
            e.signalPriceUsd = x.optDouble("token0_price_usd", Double.NaN);
            e.sellFraction = Double.NaN;
        }
        e.priceSource = "Cielo transaction USD";
        String dex = x.optString("dex", "");
        if (!dex.isEmpty()) {
            e.dex.add(dex);
        }
        e.id = "sig:" + e.signature + ":" + e.mint + ":" + e.side + ":cielo";
        return e;
    }

    static boolean heliusSaysSwap(JSONObject x) {
        return x != null && "SWAP".equalsIgnoreCase(x.optString("type", ""));
    }

    private static boolean isQuote(String mint) {
        if (mint == null) {
            return false;
        }
        return "native".equalsIgnoreCase(mint) || "So11111111111111111111111111111111111111112".equals(mint) || "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v".equals(mint) || "Es9vMFrzaCERmJfrF4H2FYDkgFdmHqkV6P5dY1Y9Fgr".equals(mint);
    }

    private static String normalizeQuote(String mint) {
        return "native".equalsIgnoreCase(mint) ? "So11111111111111111111111111111111111111112" : mint;
    }

    private static String quoteUnit(String mint) {
        if ("native".equalsIgnoreCase(mint) || "So11111111111111111111111111111111111111112".equals(mint)) {
            return "SOL";
        }
        return "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v".equals(mint) ? "USDC" : "Es9vMFrzaCERmJfrF4H2FYDkgFdmHqkV6P5dY1Y9Fgr".equals(mint) ? "USDT" : "";
    }

    private EnhancedParser() {
    }
}
