package com.memetaillab.beta1;

import android.content.Context;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class PaperEngine {
    private final Context c;
    private final Db db;

    private final Network net;

    PaperEngine(Context c, Db db, Network net2) {
        this.c = c.getApplicationContext();
        this.db = db;
        this.net = net2;
    }

    int scanNewPools() throws Exception {
        return scanNewPools(false);
    }

    int scanNewPools(boolean fast) throws Exception {
        if (Prefs.helius(this.c).trim().isEmpty()) {
            Network network = this.net;
            List<PoolSnap> found = fast ? network.discoverFast() : network.discover();
            for (PoolSnap p : found) {
                if (enabled(p.book) && !p.book.isEmpty()) {
                    this.db.upsertWatch(p);
                }
            }
        }
        return evaluateWatches();
    }

    int ingestFastEvent(String book, String signature) {
        int n = 0;
        try {
            for (PoolSnap p : this.net.discoverFromSignature(book, signature)) {
                if (enabled(p.book) && !p.book.isEmpty()) {
                    this.db.upsertWatch(p);
                    n++;
                }
            }
            if (n > 0) {
                Prefs.discovery(this.c);
            }
        } catch (Exception e) {
            Prefs.error(this.c, "Helius discovery: " + Network.shortErr(e));
        }
        return n;
    }

    int recoverRecent() {
        int n = 0;
        for (PoolSnap p : this.net.recoverRecentPools(3)) {
            if (enabled(p.book) && !p.book.isEmpty()) {
                this.db.upsertWatch(p);
                n++;
            }
        }
        if (n > 0) {
            Prefs.discovery(this.c);
        }
        return n;
    }

    int evaluateWatches() {
        String missingReason;
        long now = System.currentTimeMillis();
        this.db.expireWatch(now - ((long) (Prefs.maxAge(this.c) * 60000.0d)));
        List<PoolSnap> seeds = this.db.watchEligible(now, Prefs.minAge(this.c), Prefs.maxAge(this.c));
        ArrayList<String> addresses = new ArrayList<>();
        for (PoolSnap s : seeds) {
            if (enabled(s.book) && !this.db.hasSignal(s.book, s.pool)) {
                addresses.add(s.pool);
            }
        }
        Map<String, PoolSnap> details = this.net.pools(addresses);
        String batchErr = this.net.lastPoolBatchError();
        if (batchErr.startsWith("DEX_MISSING")) {
            missingReason = "DEX_MISSING";
        } else {
            missingReason = batchErr.isEmpty() ? "SOURCE_MISSING" : "MARKET_SOURCE_ERROR";
        }
        int accepted = 0;
        for (PoolSnap seed : seeds) {
            if (!enabled(seed.book)) {
                continue;
            }
            if (this.db.hasSignal(seed.book, seed.pool)) {
                this.db.watchState(seed.pool, "SIGNALLED", "QUALIFIED");
                continue;
            }
            PoolSnap p = details.get(seed.pool);
            if (p == null || !p.ok()) {
                this.db.watchReason(seed.pool, missingReason);
                continue;
            }
            p.book = seed.book;
            p.venue = seed.venue;
            p.createdMs = seed.createdMs;
            int activity = p.buysH1 + p.sellsH1;
            int buyers = p.buyersH1 > 0 ? p.buyersH1 : p.buysH1;
            if (p.liquidityUsd < Prefs.minLiq(this.c)) {
                this.db.watchReason(p.pool, "LIQUIDITY");
            } else if (activity < Prefs.minTrades(this.c)) {
                this.db.watchReason(p.pool, "ACTIVITY");
            } else if (buyers < Prefs.minBuyers(this.c)) {
                this.db.watchReason(p.pool, "BUYERS_PROXY");
            } else if (p.buySell() < Prefs.minRatio(this.c)) {
                this.db.watchReason(p.pool, "BUY_SELL");
            } else if (p.momentumH1 < Prefs.minMomentum(this.c)) {
                this.db.watchReason(p.pool, "MOMENTUM");
            } else if (this.db.openCount(p.book) >= Prefs.maxOpen(this.c)) {
                this.db.watchReason(p.pool, "MAX_OPEN");
            } else if (this.db.mintOpen(p.book, p.mint)) {
                this.db.watchState(p.pool, "DUPLICATE", "MINT_ALREADY_OPEN");
            } else {
                long detectedNow = System.currentTimeMillis();
                long due = detectedNow + (Prefs.effectiveDelaySec(this.c) * 1000L);
                long id = this.db.addSignal(p, due);
                if (Prefs.venueMonitor(this.c, p.book)) {
                    this.db.signalState(id, "MONITOR", "qualified-monitor-only");
                    this.db.watchState(p.pool, "MONITOR", "QUALIFIED_MONITOR");
                } else if (Prefs.venueLive(this.c, p.book)) {
                    this.db.signalState(id, "LIVE_PENDING", "qualified-live");
                    this.db.watchState(p.pool, "SIGNALLED", "QUALIFIED_LIVE");
                } else {
                    this.db.watchState(p.pool, "SIGNALLED", "QUALIFIED_PAPER");
                }
                accepted++;
            }
        }
        if (batchErr.isEmpty() || batchErr.startsWith("DEX_MISSING")) {
            Prefs.error(this.c, "");
        } else {
            Prefs.error(this.c, "Market data: " + batchErr);
        }
        return accepted;
    }

    int processPending() {
        long now = System.currentTimeMillis();
        List<Signal> pending = this.db.pending(now);
        if (pending.isEmpty()) {
            return 0;
        }
        ArrayList<String> pools = new ArrayList<>();
        for (Signal s : pending) {
            pools.add(s.pool);
        }
        Map<String, PoolSnap> quotes = this.net.pools(pools);
        int fills = 0;
        for (Signal s : pending) {
            try {
                if (this.db.openCount(s.book) >= Prefs.maxOpen(this.c)) {
                    this.db.signalState(s.id, "SKIPPED", "max-open-at-fill");
                    this.db.skip(s.book, "max-open-at-fill");
                    continue;
                }
                if (this.db.mintOpen(s.book, s.mint)) {
                    this.db.signalState(s.id, "SKIPPED", "mint-open-at-fill");
                    this.db.skip(s.book, "mint-open-at-fill");
                    continue;
                }
                PoolSnap p = quotes.get(s.pool);
                if (p == null || !p.ok()) {
                    this.db.signalState(s.id, "FAILED", "fill price unavailable");
                    this.db.skip(s.book, "fill-price-failed");
                    continue;
                }
                if (p.liquidityUsd < Math.max(500.0d, Prefs.minLiq(this.c) * 0.2d)) {
                    this.db.signalState(s.id, "SKIPPED", "liquidity collapsed");
                    this.db.skip(s.book, "liq-collapse-before-fill");
                    continue;
                }
                double stake = (Prefs.sizePct(this.c) * 1000.0d) / 100.0d;
                double cash = this.db.cash(s.book);
                if (cash < stake) {
                    this.db.signalState(s.id, "SKIPPED", "cash<stake");
                    this.db.skip(s.book, "cash<stake");
                    continue;
                }
                double entryMarket = p.priceUsd;
                double entryEff = entryMarket * ((Prefs.entryBps(this.c) / 10000.0d) + 1.0d);
                double qty = stake / entryEff;
                long fillNow = System.currentTimeMillis();
                this.db.setCash(s.book, cash - stake);
                long id = this.db.openPosition(s, entryMarket, entryEff, stake, qty);
                double drift = 0.0d;
                if (s.signalPrice > 0.0d) {
                    drift = 100.0d * ((entryMarket / s.signalPrice) - 1.0d);
                }
                double signalToFill = (fillNow - s.detectedAt) / 1000.0d;
                double dueLag = (fillNow - s.fillDue) / 1000.0d;
                this.db.ledger(fillNow, s.book, "BUY", s.mint, s.symbol, s.venue, s.pool, entryMarket, entryEff, qty, 0.0d, 0.0d, String.format(Locale.US, "signal $%s · delayed fill drift %+.3f%% · signal→fill %.1fs · due lag %+.1fs · position #%d", Db.fmt(s.signalPrice), Double.valueOf(drift), Double.valueOf(signalToFill), Double.valueOf(dueLag), Long.valueOf(id)));
                this.db.signalState(s.id, "FILLED", "");
                fills++;
            } catch (Exception e) {
                this.db.signalState(s.id, "FAILED", Network.shortErr(e));
                this.db.skip(s.book, "fill-error");
            }
        }
        return fills;
    }

    int markOpen() {
        List<Position> ps = this.db.openPositions();
        if (ps.isEmpty()) {
            for (String b : Config.BOOKS) {
                snapshotBook(b);
            }
            return 0;
        }
        ArrayList<String> addrs = new ArrayList<>();
        for (Position p : ps) {
            addrs.add(p.pool);
        }
        Map<String, PoolSnap> marks = this.net.pools(addrs);
        long now = System.currentTimeMillis();
        int exits = 0;
        for (Position p : ps) {
            PoolSnap m = marks.get(p.pool);
            if (m == null || !m.ok()) {
                continue;
            }
            p.lastPrice = m.priceUsd;
            p.lastLiquidityUsd = m.liquidityUsd;
            p.lastUpdate = now;
            double x = p.entryMarketPrice > 0.0d ? m.priceUsd / p.entryMarketPrice : 0.0d;
            if (x > p.highX) {
                p.highX = x;
            }
            if (x <= p.floorX) {
                closeAll(p, m, "STOP @ " + String.format(Locale.US, "%.2fx", Double.valueOf(p.floorX)));
                exits++;
                continue;
            }
            if (now - p.entryTime >= 172800000) {
                closeAll(p, m, "48H HORIZON");
                exits++;
                continue;
            }
            if (!p.hit7 && x >= 7.0d) {
                sellFractionOriginal(p, m, 0.1d, "PARTIAL 10% @ 7x");
                p.hit7 = true;
            }
            if (!p.hit20 && x >= 20.0d) {
                sellFractionOriginal(p, m, 0.025d, "PARTIAL 2.5% @ 20x");
                p.hit20 = true;
            }
            if (x >= 7.0d) {
                p.trailActive = true;
            }
            if (p.trailActive) {
                p.trailPeakX = Math.max(p.trailPeakX, x);
                if (x <= p.trailPeakX * 0.675d) {
                    closeAll(p, m, "32.5% RUNNER TRAIL");
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
            this.db.updatePosition(p);
        }
        for (String b : Config.BOOKS) {
            snapshotBook(b);
        }
        return exits;
    }

    String manualClose(long positionId) {
        Position target;
        Iterator<Position> it = this.db.openPositions().iterator();
        while (true) {
            if (!it.hasNext()) {
                target = null;
                break;
            }
            Position p = it.next();
            if (p.id == positionId) {
                target = p;
                break;
            }
        }
        if (target == null) {
            return "Paper position is no longer open.";
        }
        try {
            PoolSnap m = this.net.pool(target.pool);
            double markNotional = target.remainingQty * m.priceUsd;
            double est = estimateSellProceeds(target.remainingQty, m.priceUsd, m.liquidityUsd, true);
            double impact = markNotional > 0.0d ? (1.0d - (est / markNotional)) * 100.0d : 0.0d;
            closeAll(target, m, "MANUAL CLOSE");
            for (String b : Config.BOOKS) {
                snapshotBook(b);
            }
            return String.format(Locale.US, "Closed PAPER %s · proceeds $%,.2f · mark $%,.2f · estimated all-in impact %.2f%%", target.symbol, Double.valueOf(est), Double.valueOf(markNotional), Double.valueOf(impact));
        } catch (Exception e) {
            return "Paper close failed: " + Network.shortErr(e);
        }
    }

    String previewExit(long positionId) {
        Position target;
        Iterator<Position> it = this.db.openPositions().iterator();
        while (true) {
            if (!it.hasNext()) {
                target = null;
                break;
            }
            Position p = it.next();
            if (p.id == positionId) {
                target = p;
                break;
            }
        }
        if (target == null) {
            return "Paper position is no longer open.";
        }
        try {
            PoolSnap m = this.net.pool(target.pool);
            double markNotional = target.remainingQty * m.priceUsd;
            double proceeds = estimateSellProceeds(target.remainingQty, m.priceUsd, m.liquidityUsd, true);
            double impact = markNotional > 0.0d ? (1.0d - (proceeds / markNotional)) * 100.0d : 0.0d;
            return String.format(Locale.US, "%s PAPER exit estimate\n\nDisplayed mark value: $%,.2f\nPool liquidity: $%,.2f\nApprox executable proceeds: $%,.2f\nApprox all-in impact/cost: %.2f%%\n\nThis is a constant-product liquidity approximation, not a guaranteed live quote.", target.symbol, Double.valueOf(markNotional), Double.valueOf(m.liquidityUsd), Double.valueOf(proceeds), Double.valueOf(impact));
        } catch (Exception e) {
            return "Exit estimate failed: " + Network.shortErr(e);
        }
    }

    private void sellFractionOriginal(Position p, PoolSnap m, double frac, String note) {
        double qty = Math.min(p.remainingQty, p.originalQty * frac);
        if (qty <= 0.0d) {
            return;
        }
        double proceeds = estimateSellProceeds(qty, m.priceUsd, m.liquidityUsd, false);
        double eff = qty > 0.0d ? proceeds / qty : 0.0d;
        double mark = m.priceUsd * qty;
        double impact = mark > 0.0d ? (1.0d - (proceeds / mark)) * 100.0d : 0.0d;
        p.remainingQty -= qty;
        p.realizedProceeds += proceeds;
        this.db.setCash(p.book, this.db.cash(p.book) + proceeds);
        this.db.ledger(System.currentTimeMillis(), p.book, "SELL_PART", p.mint, p.symbol, p.venue, p.pool, m.priceUsd, eff, qty, proceeds, 0.0d, note + " · liquidity $" + String.format(Locale.US, "%,.0f", Double.valueOf(m.liquidityUsd)) + " · est impact " + String.format(Locale.US, "%.2f%%", Double.valueOf(impact)));
    }

    private void closeAll(Position p, PoolSnap m, String note) {
        double qty = p.remainingQty;
        double proceeds = estimateSellProceeds(qty, m.priceUsd, m.liquidityUsd, true);
        double eff = qty > 0.0d ? proceeds / qty : 0.0d;
        double mark = m.priceUsd * qty;
        double impact = mark > 0.0d ? (1.0d - (proceeds / mark)) * 100.0d : 0.0d;
        p.remainingQty = 0.0d;
        p.realizedProceeds += proceeds;
        p.state = "CLOSED";
        p.closeTime = System.currentTimeMillis();
        p.lastPrice = m.priceUsd;
        p.lastUpdate = p.closeTime;
        p.closedPnl = p.realizedProceeds - p.stakeUsd;
        this.db.setCash(p.book, this.db.cash(p.book) + proceeds);
        this.db.updatePosition(p);
        this.db.ledger(p.closeTime, p.book, "SELL_CLOSE", p.mint, p.symbol, p.venue, p.pool, m.priceUsd, eff, qty, proceeds, p.closedPnl, note + " · liquidity $" + String.format(Locale.US, "%,.0f", Double.valueOf(m.liquidityUsd)) + " · est impact " + String.format(Locale.US, "%.2f%%", Double.valueOf(impact)) + " · final P/L " + String.format(Locale.US, "%+.2f", Double.valueOf(p.closedPnl)));
    }

    private double estimateSellProceeds(double qty, double market, double liquidityUsd, boolean finalClose) {
        double notional = Math.max(0.0d, qty * market);
        double gross = notional;
        if (liquidityUsd > 0.0d && Double.isFinite(liquidityUsd)) {
            double quoteReserve = Math.max(1.0d, 0.5d * liquidityUsd);
            gross = (quoteReserve * notional) / (quoteReserve + notional);
        }
        double gross2 = gross * Math.max(0.0d, 1.0d - (Prefs.exitBps(this.c) / 10000.0d));
        if (finalClose) {
            return Math.max(0.0d, gross2 - Prefs.fixedFeeUsd(this.c));
        }
        return gross2;
    }

    private void snapshotBook(String book) {
        double unreal = 0.0d;
        for (Position p : this.db.openPositions()) {
            if (book.equals(p.book)) {
                unreal += estimateSellProceeds(p.remainingQty, p.lastPrice, p.lastLiquidityUsd, false);
            }
        }
        this.db.markSnapshot(book, this.db.cash(book) + unreal, unreal);
    }

    double estimatedLiquidationValue(Position p) {
        return estimateSellProceeds(p.remainingQty, p.lastPrice, p.lastLiquidityUsd, false);
    }

    private void rememberSkip(PoolSnap p, String why) {
        long id = this.db.addSignal(p, System.currentTimeMillis());
        this.db.signalState(id, "SKIPPED", why);
        this.db.skip(p.book, why);
    }

    private boolean enabled(String book) {
        return Prefs.venueEnabled(this.c, book);
    }
}
