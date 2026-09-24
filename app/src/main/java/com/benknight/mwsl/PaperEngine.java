package com.benknight.mwsl;

import android.content.Context;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

class PaperEngine {
    private final Context context;
    private final Db db;
    private final Listener listener;

    private final Network net;

    interface Listener {
        void onLog(String str);

        void onSignal(String str, String str2);
    }

    PaperEngine(Context c, Db db, Network net2, Listener listener) {
        this.context = c.getApplicationContext();
        this.db = db;
        this.net = net2;
        this.listener = listener;
    }

    void recordSignal(TraderState tr, TradeSignal e, boolean historical) {
        if (this.db.recordExists(e.id)) {
            return;
        }
        e.traderId = tr.id;
        e.detectedAt = historical ? 0L : System.currentTimeMillis();
        if (Double.isFinite(e.signalPriceUsd) && e.signalPriceUsd > 0.0d) {
            e.priceSource = "transaction stablecoin quote";
        }
        if (!Double.isFinite(e.signalPriceUsd) || e.signalPriceUsd <= 0.0d) {
            double derived = this.net.deriveUsdFromSignal(e);
            if (Double.isFinite(derived) && derived > 0.0d) {
                e.signalPriceUsd = derived;
                e.priceSource = "transaction SOL quote × SOL/USD";
            }
        }
        Market market = this.net.tokenMarket(e.mint, false);
        if ((!Double.isFinite(e.signalPriceUsd) || e.signalPriceUsd <= 0.0d) && Double.isFinite(market.priceUsd) && market.priceUsd > 0.0d) {
            e.signalPriceUsd = market.priceUsd;
            e.priceSource = "DexScreener signal quote";
        }
        e.symbol = market.symbol;
        if (e.symbol == null || e.symbol.isEmpty()) {
            e.symbol = Db.shortAddr(e.mint);
        }
        String dex = join(e.dex);
        String router = join(e.router);
        long lag = historical ? 0L : Math.max(0L, e.detectedAt - e.time);
        String signalNote = (historical ? "historical backfill" : "live detected") + ((e.priceSource == null || e.priceSource.isEmpty()) ? "" : " · price " + e.priceSource);
        this.db.insertSignal(e, dex, router, signalNote);
        Db db = this.db;
        String str = tr.id;
        if (historical) {
            db.incHistoricalSignal(str);
        } else {
            db.incSignal(str, lag);
        }
        if (this.listener != null && !historical) {
            this.listener.onSignal(tr.name + " " + e.side, e.symbol + " · " + venue(router, dex));
        }
        if (historical) {
            return;
        }
        if ("SELL".equals(e.side) && "cielo-enhanced".equals(e.source) && !Double.isFinite(e.sellFraction)) {
            skip(tr, e.id + ":enhanced_sell_fraction", e.side, e.mint, e.symbol, dex, router, e.signalPriceUsd, e.signature, "enhanced SELL detected but source-wallet sell fraction unavailable");
            if (this.listener != null) {
                this.listener.onLog(tr.name + " SELL detected via Cielo but safely skipped: sell fraction unavailable");
                return;
            }
            return;
        }
        long freshness = System.currentTimeMillis() - e.time;
        long threshold = Math.max(120000L, ((long) Prefs.pollSec(this.context)) * 4000L);
        if (freshness > threshold) {
            skip(tr, e.id + ":stale", e.side, e.mint, e.symbol, dex, router, e.signalPriceUsd, e.signature, "stale catch-up > " + Math.round(threshold / 1000.0d) + "s");
            if (this.listener != null) {
                this.listener.onLog(tr.name + " stale catch-up logged, not paper-filled: " + e.symbol);
                return;
            }
            return;
        }
        PendingFill p = new PendingFill();
        p.id = "pending:" + e.id;
        p.traderId = tr.id;
        p.signalRecordId = e.id;
        p.executeAfter = e.detectedAt + (((long) Prefs.humanDelaySec(this.context)) * 1000L);
        p.side = e.side;
        p.mint = e.mint;
        p.symbol = e.symbol;
        p.dex = dex;
        p.router = router;
        p.signalPriceUsd = e.signalPriceUsd;
        p.sellFraction = e.sellFraction;
        p.signature = e.signature;
        p.detectedAt = e.detectedAt;
        p.txTime = e.time;
        this.db.addPending(p);
    }

    void executeDue() {
        for (PendingFill p : this.db.duePending(System.currentTimeMillis())) {
            try {
                execute(p);
            } catch (Exception ex) {
                if (this.listener != null) {
                    this.listener.onLog("Paper fill " + p.traderId + ": " + ex.getMessage());
                }
            }
        }
    }

    private void execute(PendingFill p) {
        TraderState tr = this.db.trader(p.traderId);
        if (tr == null) {
            this.db.deletePending(p.id);
            return;
        }
        if (!Config.paperEnabled(p.traderId)) {
            this.db.deletePending(p.id);
            if (this.listener != null) {
                this.listener.onLog(p.traderId + " research-only: legacy pending fill cancelled");
            }
            return;
        }
        Market m = this.net.tokenMarket(p.mint, true);
        boolean delayedQuote = Double.isFinite(m.priceUsd) && m.priceUsd > 0.0d;
        double px = delayedQuote ? m.priceUsd : p.signalPriceUsd;
        String fillSource = delayedQuote ? "DexScreener delayed quote" : "estimated signal-price fallback";
        if (!Double.isFinite(px) || px <= 0.0d) {
            skip(tr, p.id + ":no_price", p.side, p.mint, p.symbol, p.dex, p.router, p.signalPriceUsd, p.signature, "no USD price from transaction or delayed market source");
            this.db.deletePending(p.id);
            if (this.listener != null) {
                this.listener.onLog(tr.name + " " + p.side + " skipped: no USD price " + Db.shortAddr(p.mint));
            }
            return;
        }
        double signal = (Double.isFinite(p.signalPriceUsd) && p.signalPriceUsd > 0.0d) ? p.signalPriceUsd : px;
        double slip = Prefs.slippageBps(this.context) / 10000.0d;
        double fill = ("BUY".equals(p.side) ? 1.0d + slip : 1.0d - slip) * px;
        double feeRate = feeRate(p.dex);
        long now = System.currentTimeMillis();
        long human = Math.max(0L, now - p.detectedAt);
        long delay = Math.max(0L, now - p.txTime);
        double d = fill / signal;
        double drift = ("BUY".equals(p.side) ? d - 1.0d : 1.0d - d) * 100.0d;
        if ("BUY".equals(p.side)) {
            buy(tr, p, fill, feeRate, human, delay, drift, fillSource);
        } else {
            sell(tr, p, fill, feeRate, human, delay, drift, fillSource);
        }
        this.db.deletePending(p.id);
        this.db.snapshot(tr.id, now);
        this.db.snapshotCombined(now);
    }

    private void buy(TraderState tr, PendingFill p, double fill, double feeRate, long human, long delay, double drift, String fillSource) {
        double eq = this.db.equity(tr.id);
        double gross = Math.max(0.01d, Math.min(Prefs.fixedSize(this.context) ? Prefs.fixedUsd(this.context) : (Prefs.sizePct(this.context) * eq) / 100.0d, tr.cash / (feeRate + 1.0d)));
        if (gross < 0.01d) {
            skip(tr, p.id + ":cash", "BUY", p.mint, p.symbol, p.dex, p.router, p.signalPriceUsd, p.signature, "insufficient paper cash");
            return;
        }
        double fee = gross * feeRate;
        double total = gross + fee;
        double qty = gross / fill;
        Position pos = this.db.position(tr.id, p.mint);
        if (pos == null) {
            pos = new Position();
            pos.traderId = tr.id;
            pos.mint = p.mint;
            pos.symbol = p.symbol;
            pos.openedAt = System.currentTimeMillis();
        }
        pos.qty += qty;
        pos.costUsd += total;
        double d = pos.costUsd;
        double qty2 = pos.qty;
        pos.avgPriceUsd = d / qty2;
        pos.lastPriceUsd = fill;
        pos.buyCount++;
        this.db.setCash(tr.id, tr.cash - total);
        this.db.upsertPosition(pos);
        this.db.incPaper(tr.id, human);
        this.db.insertPaper("paper:" + p.signalRecordId + ":buy", tr.id, System.currentTimeMillis(), "BUY", p.mint, pos.symbol, p.dex, p.router, p.signalPriceUsd, fill, delay, gross, fee, Double.NaN, drift, p.signature, "delayed mock fill · " + fillSource);
        if (this.listener != null) {
            this.listener.onLog(String.format(Locale.US, "%s mock BUY %s $%.2f @ $%.8f", tr.name, pos.symbol, Double.valueOf(gross), Double.valueOf(fill)));
        }
    }

    private void sell(TraderState tr, PendingFill p, double fill, double feeRate, long human, long delay, double drift, String fillSource) {
        Position pos = this.db.position(tr.id, p.mint);
        if (pos == null || pos.qty <= 0.0d) {
            skip(tr, p.id + ":no_position", "SELL", p.mint, p.symbol, p.dex, p.router, p.signalPriceUsd, p.signature, "SELL without paper position");
            return;
        }
        double frac = Double.isFinite(p.sellFraction) ? Math.max(0.001d, Math.min(1.0d, p.sellFraction)) : 1.0d;
        double qty = frac > 0.995d ? pos.qty : pos.qty * frac;
        double gross = qty * fill;
        double fee = gross * feeRate;
        double net2 = gross - fee;
        double cost = pos.costUsd * (qty / pos.qty);
        double pnl = net2 - cost;
        pos.qty -= qty;
        pos.costUsd -= cost;
        pos.realizedPnl += pnl;
        pos.lastPriceUsd = fill;
        pos.sellCount++;
        this.db.setCash(tr.id, tr.cash + net2);
        this.db.incPaper(tr.id, human);
        this.db.insertPaper("paper:" + p.signalRecordId + ":sell", tr.id, System.currentTimeMillis(), "SELL", p.mint, pos.symbol, p.dex, p.router, p.signalPriceUsd, fill, delay, gross, fee, pnl, drift, p.signature, "delayed mock fill · " + fillSource);
        if (pos.qty < 1.0E-12d || frac > 0.995d) {
            long hold = System.currentTimeMillis() - pos.openedAt;
            this.db.insertClosed("closed:" + tr.id + ":" + p.mint + ":" + p.signature, tr.id, System.currentTimeMillis(), p.mint, pos.symbol, p.dex, p.router, fill, fee, pos.realizedPnl, p.signature, hold);
            this.db.deletePosition(tr.id, p.mint);
        } else {
            this.db.upsertPosition(pos);
        }
        if (this.listener != null) {
            this.listener.onLog(String.format(Locale.US, "%s mock SELL %s $%.2f · PnL $%.2f", tr.name, pos.symbol, Double.valueOf(gross), Double.valueOf(pnl)));
        }
    }

    void markPrices() {
        List<Position> ps = this.db.allPositions();
        Set<String> mints = new HashSet<>();
        Iterator<Position> it = ps.iterator();
        while (it.hasNext()) {
            mints.add(it.next().mint);
        }
        for (String mint : mints) {
            Market m = this.net.tokenMarket(mint, true);
            if (!Double.isFinite(m.priceUsd) || m.priceUsd <= 0.0d) {
                continue;
            }
            for (Position p : ps) {
                if (mint.equals(p.mint)) {
                    this.db.updatePositionPrice(p.traderId, p.mint, m.priceUsd);
                }
            }
        }
        long now = System.currentTimeMillis();
        for (TraderDef t : Config.TRADERS) {
            this.db.snapshot(t.id, now);
        }
        this.db.snapshotCombined(now);
    }

    private void skip(TraderState tr, String id, String side, String mint, String symbol, String dex, String router, double signalPrice, String signature, String reason) {
        this.db.incSkipped(tr.id);
        this.db.insertSkip("skip:" + id, tr.id, System.currentTimeMillis(), side, mint, symbol, dex, router, signalPrice, signature, reason);
    }

    private double feeRate(String dex) {
        double bps = Prefs.feeBps(this.context);
        if (dex == null) {
            dex = "";
        }
        if (dex.contains("Pump.fun")) {
            bps = Math.max(bps, 125.0d);
        } else if (dex.contains("PumpSwap")) {
            bps = Math.max(bps, 50.0d);
        } else if (dex.contains("Raydium")) {
            bps = Math.max(bps, 25.0d);
        } else if (dex.contains("Orca Whirlpool")) {
            bps = Math.max(bps, 30.0d);
        }
        return bps / 10000.0d;
    }

    static String join(List<String> xs) {
        if (xs == null || xs.isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (String x : xs) {
            if (b.length() > 0) {
                b.append(" → ");
            }
            b.append(x);
        }
        return b.toString();
    }

    static String venue(String router, String dex) {
        return (router == null || router.isEmpty()) ? (dex == null || dex.isEmpty()) ? "Unknown venue" : dex : (dex == null || dex.isEmpty()) ? router : router + " → " + dex;
    }
}
