package com.benknight.mwsl;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    private static final String FILE = "mwsl_prefs";

    static SharedPreferences p(Context c) {
        return c.getSharedPreferences(FILE, 0);
    }

    static String heliusKey(Context c) {
        return p(c).getString("helius_key", "").trim();
    }

    static String cieloKey(Context c) {
        return p(c).getString("cielo_key", "").trim();
    }

    static String rpcUrl(Context c) {
        return p(c).getString("rpc_url", "https://solana-rpc.publicnode.com").trim();
    }

    static int pollSec(Context c) {
        return clamp(p(c).getInt("poll_sec", 12), 5, 600);
    }

    static int humanDelaySec(Context c) {
        return clamp(p(c).getInt("human_delay_sec", 15), 0, 300);
    }

    static int pricePollSec(Context c) {
        return clamp(p(c).getInt("price_poll_sec", 20), 10, 600);
    }

    static double sizePct(Context c) {
        return clampD(Double.longBitsToDouble(p(c).getLong("size_pct", Double.doubleToRawLongBits(1.0d))), 0.01d, 100.0d);
    }

    static double fixedUsd(Context c) {
        return clampD(Double.longBitsToDouble(p(c).getLong("fixed_usd", Double.doubleToRawLongBits(10.0d))), 0.01d, 1000000.0d);
    }

    static boolean fixedSize(Context c) {
        return p(c).getBoolean("fixed_size", false);
    }

    static int slippageBps(Context c) {
        return clamp(p(c).getInt("slippage_bps", 50), 0, 5000);
    }

    static int feeBps(Context c) {
        return clamp(p(c).getInt("fee_bps", 50), 0, 5000);
    }

    static int backfillDays(Context c) {
        return clamp(p(c).getInt("backfill_days", 365), 1, 3650);
    }

    static int maxBackfillTx(Context c) {
        return clamp(p(c).getInt("max_backfill_tx", 5000), 100, 100000);
    }

    static boolean trackingEnabled(Context c) {
        return p(c).getBoolean("tracking_enabled", false);
    }

    static void setTrackingEnabled(Context c, boolean v) {
        p(c).edit().putBoolean("tracking_enabled", v).apply();
    }

    static long lastHeartbeat(Context c) {
        return p(c).getLong("service_heartbeat", 0L);
    }

    static void setHeartbeat(Context c, long time) {
        p(c).edit().putLong("service_heartbeat", time).apply();
    }

    static void save(Context c, String helius, String cielo, String rpc, int poll, int delay, int pricePoll, double sizePct, double fixedUsd, boolean fixed, int slip, int fee, int days, int maxTx) {
        p(c).edit().putString("helius_key", helius.trim()).putString("cielo_key", cielo.trim()).putString("rpc_url", rpc.trim()).putInt("poll_sec", clamp(poll, 5, 600)).putInt("human_delay_sec", clamp(delay, 0, 300)).putInt("price_poll_sec", clamp(pricePoll, 10, 600)).putLong("size_pct", Double.doubleToRawLongBits(clampD(sizePct, 0.01d, 100.0d))).putLong("fixed_usd", Double.doubleToRawLongBits(clampD(fixedUsd, 0.01d, 1000000.0d))).putBoolean("fixed_size", fixed).putInt("slippage_bps", clamp(slip, 0, 5000)).putInt("fee_bps", clamp(fee, 0, 5000)).putInt("backfill_days", clamp(days, 1, 3650)).putInt("max_backfill_tx", clamp(maxTx, 100, 100000)).apply();
    }

    private static int clamp(int v, int a, int b) {
        return Math.max(a, Math.min(b, v));
    }

    private static double clampD(double v, double a, double b) {
        return Math.max(a, Math.min(b, v));
    }

    private Prefs() {
    }
}
