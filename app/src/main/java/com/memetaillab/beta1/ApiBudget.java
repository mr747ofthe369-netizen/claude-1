package com.memetaillab.beta1;

import android.content.Context;
import android.content.SharedPreferences;
import java.time.Duration;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.Locale;

final class ApiBudget {
    static final String COINGECKO = "COINGECKO";
    static final String HELIUS = "HELIUS";
    static final String TRADING = "TRADING";
    private final Context c;

    ApiBudget(Context c) {
        this.c = c.getApplicationContext();
    }

    static class Snapshot {
        double burstRpm;
        boolean configured;
        double daysRemaining;
        long monthly;
        int planPerMinute;
        String provider = "";
        long remaining;
        int reservePct;
        int resetDay;
        double sustainableRpm;
        long usableRemaining;
        long used;

        Snapshot() {
        }

        String summary() {
            return !this.configured ? this.provider + ": not configured" : String.format(Locale.US, "%s · %,d/%,d used · sustainable %.2f/min · burst %.0f/min · %.1fd left", this.provider, Long.valueOf(this.used), Long.valueOf(this.monthly), Double.valueOf(this.sustainableRpm), Double.valueOf(this.burstRpm), Double.valueOf(this.daysRemaining));
        }
    }

    Snapshot snapshot(String p) {
        SharedPreferences s = prefs();
        Snapshot x = new Snapshot();
        x.provider = p;
        x.monthly = s.getLong(k(p, "monthly"), 0L);
        x.used = s.getLong(k(p, "used"), 0L);
        x.planPerMinute = s.getInt(k(p, "rpm"), 0);
        x.resetDay = s.getInt(k(p, "resetDay"), 1);
        x.reservePct = s.getInt(k(p, "reserve"), 10);
        x.configured = x.monthly > 0 && x.planPerMinute > 0;
        x.remaining = Math.max(0L, x.monthly - x.used);
        long target = (long) Math.floor(x.monthly * (1.0d - (Math.max(0, Math.min(50, x.reservePct)) / 100.0d)));
        x.usableRemaining = Math.max(0L, target - x.used);
        double mins = minutesUntilReset(Math.max(1, Math.min(28, x.resetDay)));
        x.daysRemaining = mins / 1440.0d;
        x.sustainableRpm = mins <= 0.0d ? 0.0d : x.usableRemaining / mins;
        double accumulated = Math.max(1.0d, Math.min(x.planPerMinute, x.sustainableRpm * 8.0d));
        x.burstRpm = x.configured ? accumulated : 0.0d;
        return x;
    }

    synchronized boolean allow(String provider, int calls) {
        Snapshot x = snapshot(provider);
        if (!x.configured) {
            return true;
        }
        if (calls <= 0) {
            return true;
        }
        if (x.usableRemaining < calls || x.sustainableRpm <= 0.0d) {
            return false;
        }
        SharedPreferences s = prefs();
        long now = System.currentTimeMillis();
        long last = s.getLong(k(provider, "tokenTs"), 0L);
        double capacity = Math.max(1.0d, x.burstRpm);
        double tokens;
        try {
            tokens = Double.parseDouble(s.getString(k(provider, "tokens"), String.valueOf(capacity)));
        } catch (Exception e) {
            tokens = capacity;
        }
        if (last <= 0) {
            last = now;
        }
        double elapsedMin = Math.max(0.0d, (now - last) / 60000.0d);
        tokens = Math.min(capacity, tokens + (x.sustainableRpm * elapsedMin));
        long window = s.getLong(k(provider, "window"), 0L);
        int count = s.getInt(k(provider, "windowCount"), 0);
        if (now - window >= 60000) {
            window = now;
            count = 0;
        }
        if (count + calls > x.planPerMinute || tokens + 1.0E-9d < calls) {
            s.edit().putString(k(provider, "tokens"), String.valueOf(tokens)).putLong(k(provider, "tokenTs"), now).putLong(k(provider, "window"), window).putInt(k(provider, "windowCount"), count).apply();
            return false;
        }
        s.edit().putString(k(provider, "tokens"), String.valueOf(tokens - calls)).putLong(k(provider, "tokenTs"), now).putLong(k(provider, "window"), window).putInt(k(provider, "windowCount"), count + calls).putLong(k(provider, "used"), x.used + calls).apply();
        return true;
    }

    synchronized void record(String provider, int calls) {
        SharedPreferences s = prefs();
        long used = s.getLong(k(provider, "used"), 0L);
        s.edit().putLong(k(provider, "used"), Math.max(0L, calls + used)).apply();
    }

    void configure(String p, long monthly, int rpm, int resetDay, int reservePct, long used) {
        prefs().edit().putLong(k(p, "monthly"), Math.max(0L, monthly)).putInt(k(p, "rpm"), Math.max(0, rpm)).putInt(k(p, "resetDay"), Math.max(1, Math.min(28, resetDay))).putInt(k(p, "reserve"), Math.max(0, Math.min(50, reservePct))).putLong(k(p, "used"), Math.max(0L, used)).apply();
    }

    void resetUsage(String p) {
        prefs().edit().putLong(k(p, "used"), 0L).putLong(k(p, "window"), 0L).putInt(k(p, "windowCount"), 0).remove(k(p, "tokens")).remove(k(p, "tokenTs")).apply();
    }

    private double minutesUntilReset(int day) {
        ZonedDateTime now = ZonedDateTime.now();
        YearMonth ym = YearMonth.from(now);
        int d = Math.min(day, ym.lengthOfMonth());
        ZonedDateTime reset = now.withDayOfMonth(d).toLocalDate().atStartOfDay(now.getZone());
        if (!reset.isAfter(now)) {
            YearMonth next = ym.plusMonths(1L);
            reset = next.atDay(Math.min(day, next.lengthOfMonth())).atStartOfDay(now.getZone());
        }
        return Math.max(1L, Duration.between(now, reset).toMinutes());
    }

    private SharedPreferences prefs() {
        return this.c.getSharedPreferences("mtl_api_budget", 0);
    }

    private static String k(String p, String n) {
        return p + "_" + n;
    }
}
