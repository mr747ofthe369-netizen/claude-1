package com.benknight.mwsl;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

class Db extends SQLiteOpenHelper {
    private static final String NAME = "mwsl_native.db";
    private static final int VERSION = 1;

    Db(Context c) {
        super(c, NAME, (SQLiteDatabase.CursorFactory) null, 1);
    }

    @Override // android.database.sqlite.SQLiteOpenHelper
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE traders(id TEXT PRIMARY KEY,name TEXT NOT NULL,wallet TEXT NOT NULL,starting_balance REAL NOT NULL,cash REAL NOT NULL,last_signature TEXT,last_sync INTEGER NOT NULL DEFAULT 0,last_error TEXT,signal_count INTEGER NOT NULL DEFAULT 0,paper_exec_count INTEGER NOT NULL DEFAULT 0,skipped_count INTEGER NOT NULL DEFAULT 0,total_detection_lag_ms INTEGER NOT NULL DEFAULT 0,total_human_delay_ms INTEGER NOT NULL DEFAULT 0,delay_samples INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE positions(trader_id TEXT NOT NULL,mint TEXT NOT NULL,symbol TEXT,qty REAL NOT NULL,cost_usd REAL NOT NULL,avg_price REAL NOT NULL,opened_at INTEGER NOT NULL,last_price REAL NOT NULL,realized_pnl REAL NOT NULL DEFAULT 0,buy_count INTEGER NOT NULL DEFAULT 0,sell_count INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(trader_id,mint))");
        db.execSQL("CREATE TABLE records(id TEXT PRIMARY KEY,record_type TEXT NOT NULL,trader_id TEXT NOT NULL,time INTEGER NOT NULL,detected_at INTEGER,side TEXT,mint TEXT,symbol TEXT,dex TEXT,router TEXT,signal_price REAL,fill_price REAL,delay_ms INTEGER,usd REAL,fee_usd REAL,pnl REAL,drift_pct REAL,signature TEXT,hold_ms INTEGER,notes TEXT)");
        db.execSQL("CREATE INDEX idx_records_trader_time ON records(trader_id,time DESC)");
        db.execSQL("CREATE INDEX idx_records_type_time ON records(record_type,time DESC)");
        db.execSQL("CREATE TABLE snapshots(id INTEGER PRIMARY KEY AUTOINCREMENT,trader_id TEXT NOT NULL,time INTEGER NOT NULL,equity REAL NOT NULL)");
        db.execSQL("CREATE INDEX idx_snapshots_trader_time ON snapshots(trader_id,time)");
        db.execSQL("CREATE TABLE pending_fills(id TEXT PRIMARY KEY,trader_id TEXT NOT NULL,signal_record_id TEXT NOT NULL,execute_after INTEGER NOT NULL,side TEXT NOT NULL,mint TEXT NOT NULL,symbol TEXT,dex TEXT,router TEXT,signal_price REAL,sell_fraction REAL,signature TEXT,detected_at INTEGER NOT NULL,tx_time INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_pending_due ON pending_fills(execute_after)");
        db.execSQL("CREATE TABLE processed_signatures(trader_id TEXT NOT NULL,signature TEXT NOT NULL,time INTEGER NOT NULL,PRIMARY KEY(trader_id,signature))");
        seed(db);
    }

    @Override // android.database.sqlite.SQLiteOpenHelper
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    synchronized void ensureSeeded() {
        seed(getWritableDatabase());
    }

    private void seed(SQLiteDatabase db) {
        for (TraderDef t : Config.TRADERS) {
            ContentValues v = new ContentValues();
            v.put("id", t.id);
            v.put("name", t.name);
            v.put("wallet", t.wallet);
            v.put("starting_balance", Double.valueOf(1000.0d));
            v.put("cash", Double.valueOf(1000.0d));
            db.insertWithOnConflict("traders", null, v, 4);
        }
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM snapshots WHERE trader_id='combined'", null);
        boolean empty = true;
        if (c.moveToFirst()) {
            empty = c.getLong(0) == 0;
        }
        boolean empty2 = empty;
        c.close();
        if (empty2) {
            long now = System.currentTimeMillis();
            for (TraderDef traderDef : Config.TRADERS) {
                insertSnapshotInternal(db, traderDef.id, now, 1000.0d);
            }
            insertSnapshotInternal(db, "combined", now, Config.TRADERS.length * 1000.0d);
        }
    }

    synchronized TraderState trader(String id) {
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM traders WHERE id=?", new String[]{id});
        try {
            if (!c.moveToFirst()) {
                return null;
            }
            return readTrader(c);
        } finally {
            c.close();
        }
    }

    synchronized List<TraderState> traders() {
        List<TraderState> out;
        out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM traders ORDER BY rowid", null);
        try {
            while (c.moveToNext()) {
                out.add(readTrader(c));
            }
        } finally {
            c.close();
        }
        return out;
    }

    private TraderState readTrader(Cursor c) {
        TraderState t = new TraderState();
        t.id = s(c, "id");
        t.name = s(c, "name");
        t.wallet = s(c, "wallet");
        t.startingBalance = d(c, "starting_balance");
        t.cash = d(c, "cash");
        t.lastSignature = s(c, "last_signature");
        t.lastSync = l(c, "last_sync");
        t.lastError = s(c, "last_error");
        t.signalCount = l(c, "signal_count");
        t.paperExecCount = l(c, "paper_exec_count");
        t.skippedCount = l(c, "skipped_count");
        t.totalDetectionLagMs = l(c, "total_detection_lag_ms");
        t.totalHumanDelayMs = l(c, "total_human_delay_ms");
        t.delaySamples = l(c, "delay_samples");
        return t;
    }

    synchronized void setBaseline(String id, String sig, long sync) {
        ContentValues v = new ContentValues();
        v.put("last_signature", sig);
        v.put("last_sync", Long.valueOf(sync));
        v.putNull("last_error");
        getWritableDatabase().update("traders", v, "id=?", new String[]{id});
    }

    synchronized void setLastSync(String id, String sig, long sync) {
        ContentValues v = new ContentValues();
        if (sig != null) {
            v.put("last_signature", sig);
        }
        v.put("last_sync", Long.valueOf(sync));
        v.putNull("last_error");
        getWritableDatabase().update("traders", v, "id=?", new String[]{id});
    }

    synchronized void setError(String id, String err) {
        ContentValues v = new ContentValues();
        v.put("last_error", err);
        getWritableDatabase().update("traders", v, "id=?", new String[]{id});
    }

    synchronized void incSignal(String id, long lag) {
        getWritableDatabase().execSQL("UPDATE traders SET signal_count=signal_count+1,total_detection_lag_ms=total_detection_lag_ms+?,delay_samples=delay_samples+1 WHERE id=?", new Object[]{Long.valueOf(lag), id});
    }

    synchronized void incHistoricalSignal(String id) {
        getWritableDatabase().execSQL("UPDATE traders SET signal_count=signal_count+1 WHERE id=?", new Object[]{id});
    }

    synchronized void incSkipped(String id) {
        getWritableDatabase().execSQL("UPDATE traders SET skipped_count=skipped_count+1 WHERE id=?", new Object[]{id});
    }

    synchronized void incPaper(String id, long humanDelay) {
        getWritableDatabase().execSQL("UPDATE traders SET paper_exec_count=paper_exec_count+1,total_human_delay_ms=total_human_delay_ms+? WHERE id=?", new Object[]{Long.valueOf(humanDelay), id});
    }

    synchronized void setCash(String id, double cash) {
        ContentValues v = new ContentValues();
        v.put("cash", Double.valueOf(cash));
        getWritableDatabase().update("traders", v, "id=?", new String[]{id});
    }

    synchronized boolean recordExists(String id) {
        Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM records WHERE id=? LIMIT 1", new String[]{id});
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    synchronized boolean signatureProcessed(String traderId, String sig) {
        Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM processed_signatures WHERE trader_id=? AND signature=? LIMIT 1", new String[]{traderId, sig});
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    synchronized void markSignatureProcessed(String traderId, String sig, long time) {
        ContentValues v = new ContentValues();
        v.put("trader_id", traderId);
        v.put("signature", sig);
        v.put("time", Long.valueOf(time));
        getWritableDatabase().insertWithOnConflict("processed_signatures", null, v, 4);
    }

    synchronized void insertSignal(TradeSignal e, String dex, String router, String notes) {
        ContentValues v = new ContentValues();
        v.put("id", e.id);
        v.put("record_type", "signal");
        v.put("trader_id", e.traderId);
        v.put("time", Long.valueOf(e.time));
        v.put("detected_at", Long.valueOf(e.detectedAt));
        v.put("side", e.side);
        v.put("mint", e.mint);
        v.put("symbol", e.symbol);
        v.put("dex", dex);
        v.put("router", router);
        putNullable(v, "signal_price", e.signalPriceUsd);
        v.put("signature", e.signature);
        v.put("notes", notes);
        getWritableDatabase().insertWithOnConflict("records", null, v, 4);
    }

    synchronized void insertPaper(String id, String traderId, long time, String side, String mint, String symbol, String dex, String router, double signalPrice, double fillPrice, long delayMs, double usd, double fee, double pnl, double drift, String sig, String notes) {
        ContentValues v = new ContentValues();
        v.put("id", id);
        v.put("record_type", "paper");
        v.put("trader_id", traderId);
        v.put("time", Long.valueOf(time));
        v.put("side", side);
        v.put("mint", mint);
        v.put("symbol", symbol);
        v.put("dex", dex);
        v.put("router", router);
        putNullable(v, "signal_price", signalPrice);
        v.put("fill_price", Double.valueOf(fillPrice));
        v.put("delay_ms", Long.valueOf(delayMs));
        v.put("usd", Double.valueOf(usd));
        v.put("fee_usd", Double.valueOf(fee));
        putNullable(v, "pnl", pnl);
        putNullable(v, "drift_pct", drift);
        v.put("signature", sig);
        v.put("notes", notes);
        getWritableDatabase().insertWithOnConflict("records", null, v, 4);
    }

    synchronized void insertSkip(String id, String traderId, long time, String side, String mint, String symbol, String dex, String router, double signalPrice, String sig, String reason) {
        ContentValues v = new ContentValues();
        v.put("id", id);
        v.put("record_type", "skip");
        v.put("trader_id", traderId);
        v.put("time", Long.valueOf(time));
        v.put("side", side);
        v.put("mint", mint);
        v.put("symbol", symbol);
        v.put("dex", dex);
        v.put("router", router);
        putNullable(v, "signal_price", signalPrice);
        v.put("signature", sig);
        v.put("notes", reason);
        getWritableDatabase().insertWithOnConflict("records", null, v, 4);
    }

    synchronized void insertClosed(String id, String traderId, long time, String mint, String symbol, String dex, String router, double fillPrice, double fee, double pnl, String sig, long holdMs) {
        ContentValues v = new ContentValues();
        v.put("id", id);
        v.put("record_type", "closed");
        v.put("trader_id", traderId);
        v.put("time", Long.valueOf(time));
        v.put("side", "CLOSE");
        v.put("mint", mint);
        v.put("symbol", symbol);
        v.put("dex", dex);
        v.put("router", router);
        v.put("fill_price", Double.valueOf(fillPrice));
        v.put("fee_usd", Double.valueOf(fee));
        v.put("pnl", Double.valueOf(pnl));
        v.put("signature", sig);
        v.put("hold_ms", Long.valueOf(holdMs));
        getWritableDatabase().insertWithOnConflict("records", null, v, 4);
    }

    synchronized Position position(String traderId, String mint) {
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM positions WHERE trader_id=? AND mint=?", new String[]{traderId, mint});
        try {
            if (!c.moveToFirst()) {
                return null;
            }
            return readPosition(c);
        } finally {
            c.close();
        }
    }

    synchronized List<Position> positions(String traderId) {
        List<Position> out;
        out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM positions WHERE trader_id=?", new String[]{traderId});
        try {
            while (c.moveToNext()) {
                out.add(readPosition(c));
            }
        } finally {
            c.close();
        }
        return out;
    }

    synchronized List<Position> allPositions() {
        List<Position> out;
        out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM positions", null);
        try {
            while (c.moveToNext()) {
                out.add(readPosition(c));
            }
        } finally {
            c.close();
        }
        return out;
    }

    private Position readPosition(Cursor c) {
        Position p = new Position();
        p.traderId = s(c, "trader_id");
        p.mint = s(c, "mint");
        p.symbol = s(c, "symbol");
        p.qty = d(c, "qty");
        p.costUsd = d(c, "cost_usd");
        p.avgPriceUsd = d(c, "avg_price");
        p.openedAt = l(c, "opened_at");
        p.lastPriceUsd = d(c, "last_price");
        p.realizedPnl = d(c, "realized_pnl");
        p.buyCount = l(c, "buy_count");
        p.sellCount = l(c, "sell_count");
        return p;
    }

    synchronized void upsertPosition(Position p) {
        ContentValues v = new ContentValues();
        v.put("trader_id", p.traderId);
        v.put("mint", p.mint);
        v.put("symbol", p.symbol);
        v.put("qty", Double.valueOf(p.qty));
        v.put("cost_usd", Double.valueOf(p.costUsd));
        v.put("avg_price", Double.valueOf(p.avgPriceUsd));
        v.put("opened_at", Long.valueOf(p.openedAt));
        v.put("last_price", Double.valueOf(p.lastPriceUsd));
        v.put("realized_pnl", Double.valueOf(p.realizedPnl));
        v.put("buy_count", Long.valueOf(p.buyCount));
        v.put("sell_count", Long.valueOf(p.sellCount));
        getWritableDatabase().insertWithOnConflict("positions", null, v, 5);
    }

    synchronized void deletePosition(String traderId, String mint) {
        getWritableDatabase().delete("positions", "trader_id=? AND mint=?", new String[]{traderId, mint});
    }

    synchronized void updatePositionPrice(String traderId, String mint, double price) {
        ContentValues v = new ContentValues();
        v.put("last_price", Double.valueOf(price));
        getWritableDatabase().update("positions", v, "trader_id=? AND mint=?", new String[]{traderId, mint});
    }

    synchronized void addPending(PendingFill p) {
        ContentValues v = new ContentValues();
        v.put("id", p.id);
        v.put("trader_id", p.traderId);
        v.put("signal_record_id", p.signalRecordId);
        v.put("execute_after", Long.valueOf(p.executeAfter));
        v.put("side", p.side);
        v.put("mint", p.mint);
        v.put("symbol", p.symbol);
        v.put("dex", p.dex);
        v.put("router", p.router);
        putNullable(v, "signal_price", p.signalPriceUsd);
        putNullable(v, "sell_fraction", p.sellFraction);
        v.put("signature", p.signature);
        v.put("detected_at", Long.valueOf(p.detectedAt));
        v.put("tx_time", Long.valueOf(p.txTime));
        getWritableDatabase().insertWithOnConflict("pending_fills", null, v, 4);
    }

    synchronized List<PendingFill> duePending(long now) {
        List<PendingFill> out;
        out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM pending_fills WHERE execute_after<=? ORDER BY execute_after", new String[]{String.valueOf(now)});
        try {
            while (c.moveToNext()) {
                PendingFill p = new PendingFill();
                p.id = s(c, "id");
                p.traderId = s(c, "trader_id");
                p.signalRecordId = s(c, "signal_record_id");
                p.executeAfter = l(c, "execute_after");
                p.side = s(c, "side");
                p.mint = s(c, "mint");
                p.symbol = s(c, "symbol");
                p.dex = s(c, "dex");
                p.router = s(c, "router");
                p.signalPriceUsd = dNull(c, "signal_price");
                p.sellFraction = dNull(c, "sell_fraction");
                p.signature = s(c, "signature");
                p.detectedAt = l(c, "detected_at");
                p.txTime = l(c, "tx_time");
                out.add(p);
            }
        } finally {
            c.close();
        }
        return out;
    }

    synchronized void deletePending(String id) {
        getWritableDatabase().delete("pending_fills", "id=?", new String[]{id});
    }

    synchronized double equity(String traderId) {
        TraderState t = trader(traderId);
        if (t == null) {
            return 0.0d;
        }
        double e = t.cash;
        for (Position p : positions(traderId)) {
            e += p.qty * (p.lastPriceUsd > 0.0d ? p.lastPriceUsd : p.avgPriceUsd);
        }
        return e;
    }

    synchronized void snapshot(String traderId, long time) {
        double e = equity(traderId);
        Cursor c = getReadableDatabase().rawQuery("SELECT time,equity FROM snapshots WHERE trader_id=? ORDER BY time DESC LIMIT 1", new String[]{traderId});
        boolean add = true;
        try {
            if (c.moveToFirst()) {
                long lt = c.getLong(0);
                double le = c.getDouble(1);
                add = Math.abs(le - e) > 1.0E-4d || time - lt > 60000;
            }
        } finally {
            c.close();
        }
        if (add) {
            insertSnapshotInternal(getWritableDatabase(), traderId, time, e);
        }
        trimSnapshots(traderId);
    }

    synchronized void snapshotCombined(long time) {
        double e = 0.0d;
        for (TraderDef t : Config.TRADERS) {
            e += equity(t.id);
        }
        Cursor c = getReadableDatabase().rawQuery("SELECT time,equity FROM snapshots WHERE trader_id='combined' ORDER BY time DESC LIMIT 1", null);
        boolean add = true;
        try {
            if (c.moveToFirst()) {
                add = Math.abs(c.getDouble(1) - e) > 1.0E-4d || time - c.getLong(0) > 60000;
            }
        } finally {
            c.close();
        }
        if (add) {
            insertSnapshotInternal(getWritableDatabase(), "combined", time, e);
        }
        trimSnapshots("combined");
    }

    private void insertSnapshotInternal(SQLiteDatabase db, String id, long time, double eq) {
        ContentValues v = new ContentValues();
        v.put("trader_id", id);
        v.put("time", Long.valueOf(time));
        v.put("equity", Double.valueOf(eq));
        db.insert("snapshots", null, v);
    }

    private void trimSnapshots(String id) {
        getWritableDatabase().execSQL("DELETE FROM snapshots WHERE trader_id=? AND id NOT IN (SELECT id FROM snapshots WHERE trader_id=? ORDER BY time DESC LIMIT 20000)", new Object[]{id, id});
    }

    synchronized List<Snapshot> snapshots(String id, int max) {
        List<Snapshot> out;
        out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT time,equity FROM snapshots WHERE trader_id=? ORDER BY time DESC LIMIT ?", new String[]{id, String.valueOf(max)});
        try {
            while (c.moveToNext()) {
                out.add(new Snapshot(c.getLong(0), c.getDouble(1)));
            }
        } finally {
            c.close();
        }
        Collections.reverse(out);
        return out;
    }

    synchronized TraderStats stats(String id) {
        TraderState t = trader(id);
        TraderStats st = new TraderStats();
        st.id = id;
        st.name = t == null ? id : t.name;
        if (t == null) {
            return st;
        }
        st.equity = equity(id);
        st.roiPct = ((st.equity / t.startingBalance) - 1.0d) * 100.0d;
        st.signals = t.signalCount;
        st.paperExecs = t.paperExecCount;
        st.skipped = t.skippedCount;
        st.avgDetectionLagSec = t.delaySamples > 0 ? (((double) t.totalDetectionLagMs) / ((double) t.delaySamples)) / 1000.0d : 0.0d;
        st.avgHumanDelaySec = t.delaySamples > 0 ? (((double) t.totalHumanDelayMs) / ((double) t.delaySamples)) / 1000.0d : 0.0d;
        Cursor p = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM positions WHERE trader_id=?", new String[]{id});
        try {
            if (p.moveToFirst()) {
                st.openPositions = p.getLong(0);
            }
        } finally {
            p.close();
        }
        Cursor c = getReadableDatabase().rawQuery("SELECT pnl,hold_ms FROM records WHERE trader_id=? AND record_type='closed' ORDER BY time", new String[]{id});
        List<Double> holds = new ArrayList<>();
        double gp = 0.0d;
        double gl = 0.0d;
        double sum = 0.0d;
        long wins = 0;
        long losses = 0;
        long curLoss = 0;
        try {
            while (c.moveToNext()) {
                double pnl = c.getDouble(0);
                long h = c.isNull(1) ? 0L : c.getLong(1);
                st.closedTrades++;
                sum += pnl;
                if (pnl > 0.0d) {
                    wins++;
                    gp += pnl;
                    curLoss = 0;
                } else if (pnl < 0.0d) {
                    losses++;
                    gl += -pnl;
                    curLoss++;
                    st.maxLossStreak = Math.max(st.maxLossStreak, curLoss);
                }
                if (h > 0) {
                    holds.add(Double.valueOf(h / 60000.0d));
                }
            }
        } finally {
            c.close();
        }
        st.winRatePct = st.closedTrades > 0 ? (wins * 100.0d) / st.closedTrades : 0.0d;
        st.profitFactor = gl > 0.0d ? gp / gl : gp > 0.0d ? Double.POSITIVE_INFINITY : 0.0d;
        st.expectancy = st.closedTrades > 0 ? sum / st.closedTrades : 0.0d;
        st.avgWin = wins > 0 ? gp / wins : 0.0d;
        st.avgLoss = losses > 0 ? (-gl) / losses : 0.0d;
        if (!holds.isEmpty()) {
            double hs = 0.0d;
            for (Double x : holds) {
                hs += x.doubleValue();
            }
            st.avgHoldMin = hs / holds.size();
            Collections.sort(holds);
            int m = holds.size() / 2;
            st.medianHoldMin = holds.size() % 2 == 1 ? holds.get(m).doubleValue() : (holds.get(m - 1).doubleValue() + holds.get(m).doubleValue()) / 2.0d;
        }
        Cursor drift = getReadableDatabase().rawQuery("SELECT AVG(drift_pct) FROM records WHERE trader_id=? AND record_type='paper' AND drift_pct IS NOT NULL", new String[]{id});
        try {
            if (drift.moveToFirst() && !drift.isNull(0)) {
                st.avgDriftPct = drift.getDouble(0);
            }
        } finally {
            drift.close();
        }
        List<Snapshot> snaps = snapshots(id, 20000);
        if (!snaps.isEmpty()) {
            double peak = snaps.get(0).equity;
            long peakT = snaps.get(0).time;
            double maxDd = 0.0d;
            double maxPeak = peak;
            long maxPeakT = peakT;
            long troughT = peakT;
            for (Snapshot s : snaps) {
                if (s.equity > peak) {
                    peak = s.equity;
                    peakT = s.time;
                }
                double dd = peak > 0.0d ? (peak - s.equity) / peak : 0.0d;
                if (dd > maxDd) {
                    maxDd = dd;
                    maxPeak = peak;
                    maxPeakT = peakT;
                    troughT = s.time;
                }
            }
            Snapshot last = snaps.get(snaps.size() - 1);
            double finalPeak = 0.0d;
            for (Snapshot s2 : snaps) {
                finalPeak = Math.max(finalPeak, s2.equity);
            }
            st.maxDdPct = maxDd * 100.0d;
            st.currentDdPct = finalPeak > 0.0d ? ((finalPeak - last.equity) / finalPeak) * 100.0d : 0.0d;
            long recovery = 0;
            for (Snapshot s3 : snaps) {
                if (s3.time >= troughT && s3.equity >= maxPeak) {
                    recovery = s3.time;
                    break;
                }
            }
            st.maxDdDurationMs = (recovery > 0 ? recovery : last.time) - maxPeakT;
        }
        return st;
    }

    synchronized String recentRecordsText(int limit) {
        StringBuilder b;
        b = new StringBuilder();
        Cursor c = getReadableDatabase().rawQuery("SELECT time,trader_id,record_type,side,symbol,mint,dex,router,usd,pnl,notes FROM records ORDER BY time DESC LIMIT ?", new String[]{String.valueOf(limit)});
        try {
            while (c.moveToNext()) {
                String sym = c.isNull(4) ? shortAddr(s(c, "mint")) : c.getString(4);
                b.append(String.format(Locale.US, "%1$tF %1$tT  %2$s  %3$s/%4$s  %5$s  %6$s  $%7$.2f", Long.valueOf(c.getLong(0)), s(c, "trader_id"), s(c, "record_type"), s(c, "side"), sym, venue(s(c, "router"), s(c, "dex")), Double.valueOf(c.isNull(8) ? 0.0d : c.getDouble(8))));
                if (!c.isNull(9)) {
                    b.append(String.format(Locale.US, "  PnL $%.2f", Double.valueOf(c.getDouble(9))));
                }
                if (!c.isNull(10) && !c.getString(10).isEmpty()) {
                    b.append("  [").append(c.getString(10)).append("]");
                }
                b.append('\n');
            }
        } finally {
            c.close();
        }
        return b.toString();
    }

    synchronized String venueBreakdown(String traderId) {
        StringBuilder b;
        b = new StringBuilder();
        Cursor c = getReadableDatabase().rawQuery("SELECT COALESCE(NULLIF(router,''),NULLIF(dex,''),'Unknown') venue,COUNT(*) n FROM records WHERE trader_id=? AND record_type='signal' GROUP BY venue ORDER BY n DESC", new String[]{traderId});
        try {
            while (c.moveToNext()) {
                b.append(c.getString(0)).append(": ").append(c.getLong(1)).append("  ");
            }
        } finally {
            c.close();
        }
        return b.length() == 0 ? "No venue data yet" : b.toString();
    }

    synchronized String skipBreakdown(String traderId) {
        StringBuilder b;
        b = new StringBuilder();
        Cursor c = getReadableDatabase().rawQuery("SELECT COALESCE(notes,'unknown reason') reason,COUNT(*) n FROM records WHERE trader_id=? AND record_type='skip' GROUP BY reason ORDER BY n DESC", new String[]{traderId});
        try {
            while (c.moveToNext()) {
                if (b.length() > 0) {
                    b.append(" · ");
                }
                b.append(c.getString(0)).append(": ").append(c.getLong(1));
            }
        } finally {
            c.close();
        }
        return b.length() == 0 ? "none" : b.toString();
    }

    synchronized Cursor exportCursor() {
        return getReadableDatabase().rawQuery("SELECT * FROM records ORDER BY time", null);
    }

    private static String venue(String router, String dex) {
        return (router == null || router.isEmpty() || dex == null || dex.isEmpty()) ? (router == null || router.isEmpty()) ? (dex == null || dex.isEmpty()) ? "Unknown" : dex : router : router + " → " + dex;
    }

    static String shortAddr(String x) {
        return x == null ? "—" : x.length() > 12 ? x.substring(0, 5) + "…" + x.substring(x.length() - 5) : x;
    }

    private static void putNullable(ContentValues v, String k, double x) {
        if (Double.isFinite(x)) {
            v.put(k, Double.valueOf(x));
        } else {
            v.putNull(k);
        }
    }

    private static String s(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        if (i < 0 || c.isNull(i)) {
            return null;
        }
        return c.getString(i);
    }

    private static double d(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        if (i < 0 || c.isNull(i)) {
            return 0.0d;
        }
        return c.getDouble(i);
    }

    private static double dNull(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        if (i < 0 || c.isNull(i)) {
            return Double.NaN;
        }
        return c.getDouble(i);
    }

    private static long l(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        if (i < 0 || c.isNull(i)) {
            return 0L;
        }
        return c.getLong(i);
    }
}
