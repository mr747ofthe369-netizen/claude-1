package com.memetaillab.beta1;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    private static final String N = "mtl_beta1";

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(N, 0);
    }

    static String rpc(Context c) {
        return p(c).getString("rpc", "https://api.mainnet-beta.solana.com");
    }

    static String helius(Context c) {
        return p(c).getString("helius", "");
    }

    static String wallet(Context c) {
        return p(c).getString("wallet", "");
    }

    static int scanSec(Context c) {
        return p(c).getInt("scanSec", 90);
    }

    static int markSec(Context c) {
        return p(c).getInt("markSec", 20);
    }

    static int delaySec(Context c) {
        return p(c).getInt("delaySec", 15);
    }

    static String delayMode(Context c) {
        return p(c).getString("delayMode", "HUMAN SIM 15s");
    }

    static int effectiveDelaySec(Context c) {
        String m = delayMode(c);
        if ("AUTOMATED 0s".equals(m)) {
            return 0;
        }
        if ("HUMAN SIM 15s".equals(m)) {
            return 15;
        }
        return Math.max(0, delaySec(c));
    }

    static double sizePct(Context c) {
        return d(c, "sizePct", 1.0d);
    }

    static int entryBps(Context c) {
        return p(c).getInt("entryBps", 150);
    }

    static int exitBps(Context c) {
        return p(c).getInt("exitBps", 200);
    }

    static double fixedFeeUsd(Context c) {
        return d(c, "fixedFeeUsd", 0.03d);
    }

    static double minLiq(Context c) {
        return d(c, "minLiq", 15000.0d);
    }

    static int minTrades(Context c) {
        return p(c).getInt("minTrades", 20);
    }

    static int minBuyers(Context c) {
        return p(c).getInt("minBuyers", 20);
    }

    static double minRatio(Context c) {
        return d(c, "minRatio", 1.1d);
    }

    static double minMomentum(Context c) {
        return d(c, "minMomentum", 0.0d);
    }

    static double minAge(Context c) {
        return d(c, "minAge", 5.0d);
    }

    static double maxAge(Context c) {
        return d(c, "maxAge", 25.0d);
    }

    static int maxOpen(Context c) {
        return p(c).getInt("maxOpen", 25);
    }

    static String venueMode(Context c, String book) {
        String key = "mode_" + book;
        String legacyDefault = "PAPER";
        if (!p(c).contains(key)) {
            boolean enabled = true;
            if ("RAYDIUM".equals(book)) {
                enabled = p(c).getBoolean("raydium", true);
            } else if ("PUMPSWAP".equals(book)) {
                enabled = p(c).getBoolean("pumpswap", true);
            } else if ("PUMPFUN".equals(book)) {
                enabled = p(c).getBoolean("pumpfun", true);
            }
            legacyDefault = enabled ? "PAPER" : "OFF";
        }
        return p(c).getString(key, legacyDefault);
    }

    static boolean venueEnabled(Context c, String book) {
        return !"OFF".equals(venueMode(c, book));
    }

    static boolean venuePaper(Context c, String book) {
        return "PAPER".equals(venueMode(c, book));
    }

    static boolean venueMonitor(Context c, String book) {
        return "MONITOR".equals(venueMode(c, book));
    }

    static boolean venueLive(Context c, String book) {
        return "LIVE".equals(venueMode(c, book));
    }

    static void setVenueMode(Context c, String book, String mode) {
        p(c).edit().putString("mode_" + book, mode).apply();
    }

    static boolean tracking(Context c) {
        return p(c).getBoolean("tracking", false);
    }

    static boolean protectionOnly(Context c) {
        return p(c).getBoolean("protectionOnly", false);
    }

    static long heartbeat(Context c) {
        return p(c).getLong("heartbeat", 0L);
    }

    static String lastError(Context c) {
        return p(c).getString("lastError", "");
    }

    static long lastDiscovery(Context c) {
        return p(c).getLong("lastDiscovery", 0L);
    }

    static long lastMark(Context c) {
        return p(c).getLong("lastMark", 0L);
    }

    static long firstStart(Context c) {
        return p(c).getLong("firstStart", 0L);
    }

    static String marketSource(Context c) {
        return p(c).getString("marketSource", "DEX Screener");
    }

    static String discoverySource(Context c) {
        return p(c).getString("discoverySource", "Helius program stream");
    }

    static String rayApi(Context c) {
        return p(c).getString("rayApi", "https://api-v3.raydium.io");
    }

    static String raySwap(Context c) {
        return p(c).getString("raySwap", "https://transaction-v1.raydium.io");
    }

    static String swapApi(Context c) {
        return p(c).getString("swapApi", "");
    }

    static String coinGeckoKey(Context c) {
        return p(c).getString("coinGeckoKey", "");
    }

    static boolean coinGeckoPro(Context c) {
        return p(c).getBoolean("coinGeckoPro", true);
    }

    static boolean coinGeckoEnabled(Context c) {
        return p(c).getBoolean("coinGeckoEnabled", false);
    }

    static boolean liveArmed(Context c) {
        return p(c).getBoolean("liveArmed", false);
    }

    static double liveAllocationUsd(Context c, String book) {
        return d(c, "liveAllocation_" + book, 500.0d);
    }

    static double liveSizePct(Context c) {
        return d(c, "liveSizePct", 1.0d);
    }

    static double liveMaxTradeUsd(Context c) {
        return d(c, "liveMaxTradeUsd", 10.0d);
    }

    static double liveMaxExposureUsd(Context c) {
        return d(c, "liveMaxExposureUsd", 150.0d);
    }

    static double liveDailyLossUsd(Context c) {
        return d(c, "liveDailyLossUsd", 50.0d);
    }

    static int liveSlippageBps(Context c) {
        return p(c).getInt("liveSlippageBps", 500);
    }

    static double liveMaxImpactPct(Context c) {
        return d(c, "liveMaxImpactPct", 10.0d);
    }

    static long livePriorityMaxLamports(Context c) {
        return p(c).getLong("livePriorityMaxLamports", 1000000L);
    }

    static String livePriorityLevel(Context c) {
        return p(c).getString("livePriorityLevel", "high");
    }

    static boolean fastStream(Context c) {
        return p(c).getBoolean("fastStream", true);
    }

    static String streamState(Context c) {
        return p(c).getString("streamState", "OFF");
    }

    static long streamLastEvent(Context c) {
        return p(c).getLong("streamLastEvent", 0L);
    }

    static int streamCount(Context c, String book) {
        return p(c).getInt("streamCount_" + book, 0);
    }

    static void streamState(Context c, String s) {
        p(c).edit().putString("streamState", s).apply();
    }

    static void streamEvent(Context c, String book) {
        SharedPreferences sp = p(c);
        sp.edit().putLong("streamLastEvent", System.currentTimeMillis()).putInt("streamCount_" + book, sp.getInt("streamCount_" + book, 0) + 1).apply();
    }

    private static double d(Context c, String k, double v) {
        try {
            return Double.parseDouble(p(c).getString(k, String.valueOf(v)));
        } catch (Exception e) {
            return v;
        }
    }

    static void setTracking(Context c, boolean v) {
        p(c).edit().putBoolean("tracking", v).apply();
    }

    static void setProtectionOnly(Context c, boolean v) {
        p(c).edit().putBoolean("protectionOnly", v).apply();
    }

    static void beat(Context c) {
        p(c).edit().putLong("heartbeat", System.currentTimeMillis()).apply();
    }

    static void discovery(Context c) {
        p(c).edit().putLong("lastDiscovery", System.currentTimeMillis()).apply();
    }

    static void mark(Context c) {
        p(c).edit().putLong("lastMark", System.currentTimeMillis()).apply();
    }

    static void error(Context c, String s) {
        p(c).edit().putString("lastError", s == null ? "" : s).apply();
    }

    static void marketSource(Context c, String s) {
        p(c).edit().putString("marketSource", s == null ? "" : s).apply();
    }

    static void discoverySource(Context c, String s) {
        p(c).edit().putString("discoverySource", s == null ? "" : s).apply();
    }

    static void ensureFirstStart(Context c) {
        if (firstStart(c) == 0) {
            p(c).edit().putLong("firstStart", System.currentTimeMillis()).apply();
        }
    }

    static void setLiveArmed(Context c, boolean v) {
        p(c).edit().putBoolean("liveArmed", v).apply();
    }

    static void saveLive(Context c, String swapApi, String coinGeckoKey, boolean coinGeckoPro, boolean coinGeckoEnabled, double rayAllocation, double psAllocation, double pfAllocation, double sizePct, double maxTrade, double maxExposure, double dailyLoss, int slippageBps, double maxImpactPct, long priorityMaxLamports, String priorityLevel) {
        SharedPreferences.Editor edit = p(c).edit();
        String str = "";
        SharedPreferences.Editor putString = edit.putString("swapApi", swapApi == null ? "" : swapApi.trim());
        if (coinGeckoKey != null) {
            str = coinGeckoKey.trim();
        }
        putString.putString("coinGeckoKey", str).putBoolean("coinGeckoPro", coinGeckoPro).putBoolean("coinGeckoEnabled", coinGeckoEnabled).putString("liveAllocation_RAYDIUM", String.valueOf(Math.max(0.0d, rayAllocation))).putString("liveAllocation_PUMPSWAP", String.valueOf(Math.max(0.0d, psAllocation))).putString("liveAllocation_PUMPFUN", String.valueOf(Math.max(0.0d, pfAllocation))).putString("liveSizePct", String.valueOf(Math.max(0.05d, sizePct))).putString("liveMaxTradeUsd", String.valueOf(Math.max(0.1d, maxTrade))).putString("liveMaxExposureUsd", String.valueOf(Math.max(0.1d, maxExposure))).putString("liveDailyLossUsd", String.valueOf(Math.max(0.1d, dailyLoss))).putInt("liveSlippageBps", Math.max(1, slippageBps)).putString("liveMaxImpactPct", String.valueOf(Math.max(0.1d, maxImpactPct))).putLong("livePriorityMaxLamports", Math.max(0L, priorityMaxLamports)).putString("livePriorityLevel", priorityLevel == null ? "high" : priorityLevel).apply();
    }

    static void save(Context c, String rpc, String helius, String wallet, String rayApi, String raySwap, int scanSec, int markSec, String delayMode, int delaySec, double sizePct, int entryBps, int exitBps, double fixedFeeUsd, double minLiq, int minTrades, int minBuyers, double minRatio, double minMomentum, double minAge, double maxAge, int maxOpen, String rayMode, String psMode, String pfMode, boolean fastStream) {
        p(c).edit().putString("rpc", rpc.trim()).putString("helius", helius.trim()).putString("wallet", wallet.trim()).putString("rayApi", rayApi.trim()).putString("raySwap", raySwap.trim()).putInt("scanSec", Math.max(15, scanSec)).putInt("markSec", Math.max(5, markSec)).putString("delayMode", delayMode).putInt("delaySec", Math.max(0, delaySec)).putString("sizePct", String.valueOf(Math.max(0.05d, sizePct))).putInt("entryBps", Math.max(0, entryBps)).putInt("exitBps", Math.max(0, exitBps)).putString("fixedFeeUsd", String.valueOf(Math.max(0.0d, fixedFeeUsd))).putString("minLiq", String.valueOf(Math.max(0.0d, minLiq))).putInt("minTrades", Math.max(0, minTrades)).putInt("minBuyers", Math.max(0, minBuyers)).putString("minRatio", String.valueOf(Math.max(0.0d, minRatio))).putString("minMomentum", String.valueOf(minMomentum)).putString("minAge", String.valueOf(Math.max(0.0d, minAge))).putString("maxAge", String.valueOf(Math.max(minAge, maxAge))).putInt("maxOpen", Math.max(1, maxOpen)).putString("mode_RAYDIUM", rayMode).putString("mode_PUMPSWAP", psMode).putString("mode_PUMPFUN", pfMode).putBoolean("fastStream", fastStream).apply();
    }

    private Prefs() {
    }
}
