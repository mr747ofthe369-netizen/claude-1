package com.memetaillab.beta1;

import android.content.Context;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class LiveTrader {
    private final Context c;
    private final Db db;
    private final LiveExecution exec;

    private final Network net;

    LiveTrader(Context c, Db db, Network net2) {
        this.c = c.getApplicationContext();
        this.db = db;
        this.net = net2;
        this.exec = new LiveExecution(c);
    }

    int processPending() {
        List<Signal> xs = this.db.pendingLive(System.currentTimeMillis());
        if (xs.isEmpty()) {
            return 0;
        }
        int fills = 0;
        for (Signal s : xs) {
            if (!Prefs.venueLive(this.c, s.book)) {
                this.db.signalState(s.id, "LIVE_BLOCKED", "venue no longer LIVE");
                continue;
            }
            if (!Prefs.liveArmed(this.c)) {
                this.db.signalState(s.id, "LIVE_BLOCKED", "master LIVE disarmed");
                continue;
            }
            try {
                String gate = riskGate(s.book, s.mint);
                if (!gate.isEmpty()) {
                    this.db.signalState(s.id, "LIVE_BLOCKED", gate);
                    continue;
                }
                double alloc = Prefs.liveAllocationUsd(this.c, s.book);
                double stake = Math.min(Prefs.liveMaxTradeUsd(this.c), (Prefs.liveSizePct(this.c) * alloc) / 100.0d);
                if (stake <= 0.0d) {
                    throw new Exception("Live stake is zero");
                }
                int decimals = this.exec.tokenDecimals(s.mint);
                LiveSwap sw = this.exec.buy(s.book, s.mint, stake);
                double actualStake = sw.estimatedInputUsd > 0.0d ? sw.estimatedInputUsd : stake;
                double units = sw.outAmountRaw / Math.pow(10.0d, decimals);
                double effective = units > 0.0d ? actualStake / units : s.signalPrice;
                this.db.openLivePosition(s, actualStake, sw.inAmountRaw, sw.outAmountRaw, decimals, effective, sw.signature);
                this.db.liveLedger(System.currentTimeMillis(), s.book, "BUY", s.mint, s.symbol, s.venue, s.pool, sw.outAmountRaw, actualStake, sw.inAmountRaw, sw.signature, String.format(Locale.US, "LIVE buy · route %s · impact %.3f%% · network fee %,d lamports", sw.routeLabel, Double.valueOf(sw.priceImpactPct), Long.valueOf(sw.networkFeeLamports)));
                this.db.signalState(s.id, "LIVE_FILLED", sw.signature);
                fills++;
            } catch (Exception e) {
                this.db.signalState(s.id, "LIVE_FAILED", Network.shortErr(e));
                this.db.liveLedger(System.currentTimeMillis(), s.book, "ERROR", s.mint, s.symbol, s.venue, s.pool, 0L, 0.0d, 0L, "", "BUY · " + Network.shortErr(e));
            }
        }
        return fills;
    }

    int markOpen() {
        List<LivePosition> ps = this.db.openLivePositions();
        if (ps.isEmpty()) {
            return 0;
        }
        ArrayList<String> pools = new ArrayList<>();
        for (LivePosition p : ps) {
            pools.add(p.pool);
        }
        Map<String, PoolSnap> marks = this.net.pools(pools);
        long now = System.currentTimeMillis();
        int exits = 0;
        for (LivePosition p : ps) {
            PoolSnap m = marks.get(p.pool);
            if (m == null || !m.ok()) {
                continue;
            }
            p.lastPriceUsd = m.priceUsd;
            p.lastUpdate = now;
            double x = p.entryPriceUsd > 0.0d ? m.priceUsd / p.entryPriceUsd : 0.0d;
            if (x > p.highX) {
                p.highX = x;
            }
            try {
                if (x <= p.floorX && closeAll(p, "STOP @ " + fmtX(p.floorX))) {
                    exits++;
                    continue;
                }
                if (now - p.entryTime >= 172800000 && closeAll(p, "48H HORIZON")) {
                    exits++;
                    continue;
                }
                if (!p.hit7 && x >= 7.0d && sellFractionOriginal(p, 0.1d, "PARTIAL 10% @ 7x")) {
                    p.hit7 = true;
                }
                if (!p.hit20 && x >= 20.0d && sellFractionOriginal(p, 0.025d, "PARTIAL 2.5% @ 20x")) {
                    p.hit20 = true;
                }
                if (x >= 7.0d) {
                    p.trailActive = true;
                }
                if (p.trailActive) {
                    p.trailPeakX = Math.max(p.trailPeakX, x);
                    if (x <= p.trailPeakX * 0.675d && closeAll(p, "32.5% RUNNER TRAIL")) {
                        exits++;
                        continue;
                    }
                }
                if (x >= 2.0d) {
                    p.floorX = Math.max(p.floorX, 1.5d);
                }
                if (x >= 3.0d) {
                    p.floorX = Math.max(p.floorX, 2.0d);
                }
                if (x >= 5.0d) {
                    p.floorX = Math.max(p.floorX, 2.5d);
                }
                this.db.updateLivePosition(p);
            } catch (Exception e) {
                this.db.liveLedger(now, p.book, "ERROR", p.mint, p.symbol, p.venue, p.pool, 0L, 0.0d, 0L, "", "EXIT · " + Network.shortErr(e));
                this.db.updateLivePosition(p);
            }
        }
        return exits;
    }

    String previewExit(long positionId) {
        LivePosition p = findOpen(positionId);
        if (p == null) {
            return "LIVE position is no longer open.";
        }
        try {
            LiveSwap q = this.exec.previewSell(p.book, p.mint, p.remainingRaw);
            double units = p.remainingRaw / Math.pow(10.0d, p.decimals);
            double mark = p.lastPriceUsd * units;
            double diff = 0.0d;
            if (mark > 0.0d) {
                diff = 100.0d * (1.0d - (q.estimatedOutputUsd / mark));
            }
            return String.format(Locale.US, "%s LIVE executable quote\n\nDisplayed mark value: $%,.2f\nExecutable output estimate: $%,.2f\nRoute: %s\nQuote price impact: %.3f%%\nMark-to-exit difference: %.2f%%\n\nThe actual transaction can still move before confirmation.", p.symbol, Double.valueOf(mark), Double.valueOf(q.estimatedOutputUsd), q.routeLabel, Double.valueOf(q.priceImpactPct), Double.valueOf(diff));
        } catch (Exception e) {
            return "LIVE exit quote failed: " + Network.shortErr(e);
        }
    }

    String manualClose(long positionId) {
        LivePosition p = findOpen(positionId);
        if (p == null) {
            return "LIVE position is no longer open.";
        }
        try {
            if (closeAll(p, "MANUAL CLOSE")) {
                return "LIVE close confirmed\n" + p.symbol + "\nSignature: " + p.lastSellSig + "\nP/L: " + String.format(Locale.US, "$%+,.2f", Double.valueOf(p.closedPnlUsd));
            }
            return "Nothing to close.";
        } catch (Exception e) {
            this.db.liveLedger(System.currentTimeMillis(), p.book, "ERROR", p.mint, p.symbol, p.venue, p.pool, 0L, 0.0d, 0L, "", "MANUAL CLOSE · " + Network.shortErr(e));
            return "LIVE close failed: " + Network.shortErr(e);
        }
    }

    private LivePosition findOpen(long id) {
        for (LivePosition p : this.db.openLivePositions()) {
            if (p.id == id) {
                return p;
            }
        }
        return null;
    }

    private String riskGate(String book, String mint) {
        if (!this.exec.ready()) {
            return this.exec.readiness();
        }
        if (this.db.liveMintOpen(book, mint)) {
            return "mint already open LIVE";
        }
        if (this.db.liveOpenCount(book) >= Prefs.maxOpen(this.c)) {
            return "max LIVE positions reached";
        }
        LiveStats st = this.db.liveStats();
        if (st.realizedPnlTodayUsd <= (-Prefs.liveDailyLossUsd(this.c))) {
            return "daily live-loss cutoff reached";
        }
        double alloc = Prefs.liveAllocationUsd(this.c, book);
        double stake = Math.min(Prefs.liveMaxTradeUsd(this.c), (Prefs.liveSizePct(this.c) * alloc) / 100.0d);
        return this.db.liveOpenStakeUsd(book) + stake > alloc + 1.0E-6d ? "venue live allocation exhausted" : st.openExposureUsd + stake > Prefs.liveMaxExposureUsd(this.c) + 1.0E-6d ? "max total live exposure reached" : "";
    }

    private boolean sellFractionOriginal(LivePosition p, double frac, String note) throws Exception {
        long raw = Math.min(p.remainingRaw, Math.max(1L, (long) Math.floor(p.originalRaw * frac)));
        if (raw <= 0) {
            return false;
        }
        LiveSwap sw = this.exec.sell(p.book, p.mint, raw);
        long actualSold = sw.inAmountRaw > 0 ? Math.min(p.remainingRaw, sw.inAmountRaw) : raw;
        p.remainingRaw -= actualSold;
        p.realizedLamports += sw.outAmountRaw;
        p.realizedUsd += sw.estimatedOutputUsd;
        p.lastSellSig = sw.signature;
        this.db.liveLedger(System.currentTimeMillis(), p.book, "SELL_PART", p.mint, p.symbol, p.venue, p.pool, actualSold, sw.estimatedOutputUsd, sw.outAmountRaw, sw.signature, note + " · network fee " + sw.networkFeeLamports + " lamports");
        this.db.updateLivePosition(p);
        return true;
    }

    private boolean closeAll(LivePosition p, String note) throws Exception {
        long raw = p.remainingRaw;
        if (raw <= 0) {
            return false;
        }
        LiveSwap sw = this.exec.sell(p.book, p.mint, raw);
        p.remainingRaw = Math.max(0L, p.remainingRaw - (sw.inAmountRaw > 0 ? sw.inAmountRaw : raw));
        p.realizedLamports += sw.outAmountRaw;
        p.realizedUsd += sw.estimatedOutputUsd;
        p.lastSellSig = sw.signature;
        p.state = "CLOSED";
        p.closeTime = System.currentTimeMillis();
        p.closedPnlUsd = p.realizedUsd - p.stakeUsd;
        this.db.liveLedger(p.closeTime, p.book, "SELL_CLOSE", p.mint, p.symbol, p.venue, p.pool, raw, sw.estimatedOutputUsd, sw.outAmountRaw, sw.signature, note + " · LIVE P/L " + String.format(Locale.US, "%+.2f", Double.valueOf(p.closedPnlUsd)) + " · network fee " + sw.networkFeeLamports + " lamports");
        this.db.updateLivePosition(p);
        return true;
    }

    private static String fmtX(double x) {
        return String.format(Locale.US, "%.2fx", Double.valueOf(x));
    }
}
