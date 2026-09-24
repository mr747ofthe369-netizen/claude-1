package com.memetaillab.beta1;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

class Db extends SQLiteOpenHelper {
    private final Context app;

    Db(Context c) {
        super(c, "meme_tail_beta1.db", (SQLiteDatabase.CursorFactory) null, 4);
        this.app = c.getApplicationContext();
    }

    @Override // android.database.sqlite.SQLiteOpenHelper
    public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE account(book TEXT PRIMARY KEY, cash REAL NOT NULL, peak REAL NOT NULL, max_dd REAL NOT NULL, created INTEGER NOT NULL)");
        d.execSQL("CREATE TABLE watch(book TEXT,pool TEXT PRIMARY KEY,mint TEXT,symbol TEXT,name TEXT,venue TEXT,created INTEGER,first_seen INTEGER,last_seen INTEGER,state TEXT,last_reason TEXT DEFAULT '',last_eval INTEGER DEFAULT 0)");
        d.execSQL("CREATE INDEX ix_watch_age ON watch(state,created)");
        d.execSQL("CREATE TABLE signal(id INTEGER PRIMARY KEY AUTOINCREMENT,book TEXT,mint TEXT,symbol TEXT,name TEXT,venue TEXT,pool TEXT,detected INTEGER,fill_due INTEGER,signal_price REAL,liq REAL,buyers INTEGER,buys INTEGER,sells INTEGER,momentum REAL,buy_sell REAL,state TEXT,reason TEXT)");
        d.execSQL("CREATE UNIQUE INDEX ux_signal ON signal(book,pool)");
        d.execSQL("CREATE TABLE position(id INTEGER PRIMARY KEY AUTOINCREMENT,book TEXT,mint TEXT,symbol TEXT,name TEXT,venue TEXT,pool TEXT,entry_time INTEGER,close_time INTEGER,signal_price REAL,entry_market REAL,entry_effective REAL,stake REAL,original_qty REAL,remaining_qty REAL,realized REAL,high_x REAL,floor_x REAL,trail_peak REAL,trail_active INTEGER,hit7 INTEGER,hit20 INTEGER,last_price REAL,last_liquidity REAL DEFAULT 0,last_update INTEGER,state TEXT,closed_pnl REAL)");
        d.execSQL("CREATE INDEX ix_pos_state ON position(state,book)");
        d.execSQL("CREATE TABLE ledger(id INTEGER PRIMARY KEY AUTOINCREMENT,ts INTEGER,book TEXT,event TEXT,mint TEXT,symbol TEXT,venue TEXT,pool TEXT,market_price REAL,effective_price REAL,qty REAL,proceeds REAL,pnl REAL,note TEXT)");
        d.execSQL("CREATE TABLE snapshot(id INTEGER PRIMARY KEY AUTOINCREMENT,ts INTEGER,book TEXT,equity REAL,cash REAL,unreal REAL,dd REAL)");
        d.execSQL("CREATE INDEX ix_snap ON snapshot(book,ts)");
        d.execSQL("CREATE TABLE skip(book TEXT,reason TEXT,count INTEGER,PRIMARY KEY(book,reason))");
        d.execSQL("CREATE TABLE live_position(id INTEGER PRIMARY KEY AUTOINCREMENT,book TEXT,mint TEXT,symbol TEXT,venue TEXT,pool TEXT,entry_time INTEGER,close_time INTEGER,stake_usd REAL,entry_lamports INTEGER,entry_price_usd REAL,decimals INTEGER,original_raw INTEGER,remaining_raw INTEGER,realized_lamports INTEGER,realized_usd REAL,high_x REAL,floor_x REAL,trail_peak REAL,trail_active INTEGER,hit7 INTEGER,hit20 INTEGER,last_price_usd REAL,last_update INTEGER,state TEXT,buy_sig TEXT,last_sell_sig TEXT,closed_pnl_usd REAL)");
        d.execSQL("CREATE INDEX ix_live_pos ON live_position(state,book)");
        d.execSQL("CREATE TABLE live_ledger(id INTEGER PRIMARY KEY AUTOINCREMENT,ts INTEGER,book TEXT,event TEXT,mint TEXT,symbol TEXT,venue TEXT,pool TEXT,amount_raw INTEGER,usd_value REAL,sol_lamports INTEGER,signature TEXT,note TEXT)");
        for (String b : Config.BOOKS) {
            d.execSQL("INSERT INTO account(book,cash,peak,max_dd,created) VALUES(?,?,?,?,?)", new Object[]{b, Double.valueOf(1000.0d), Double.valueOf(1000.0d), 0, Long.valueOf(System.currentTimeMillis())});
        }
    }

    @Override // android.database.sqlite.SQLiteOpenHelper
    public void onUpgrade(SQLiteDatabase d, int o, int n) {
        if (o < 2) {
            try {
                d.execSQL("ALTER TABLE watch ADD COLUMN last_reason TEXT DEFAULT ''");
            } catch (Exception e) {
            }
            try {
                d.execSQL("ALTER TABLE watch ADD COLUMN last_eval INTEGER DEFAULT 0");
            } catch (Exception e2) {
            }
        }
        if (o < 3) {
            try {
                d.execSQL("CREATE TABLE live_position(id INTEGER PRIMARY KEY AUTOINCREMENT,book TEXT,mint TEXT,symbol TEXT,venue TEXT,pool TEXT,entry_time INTEGER,close_time INTEGER,stake_usd REAL,entry_lamports INTEGER,entry_price_usd REAL,decimals INTEGER,original_raw INTEGER,remaining_raw INTEGER,realized_lamports INTEGER,realized_usd REAL,high_x REAL,floor_x REAL,trail_peak REAL,trail_active INTEGER,hit7 INTEGER,hit20 INTEGER,last_price_usd REAL,last_update INTEGER,state TEXT,buy_sig TEXT,last_sell_sig TEXT,closed_pnl_usd REAL)");
            } catch (Exception e3) {
            }
            try {
                d.execSQL("CREATE INDEX ix_live_pos ON live_position(state,book)");
            } catch (Exception e4) {
            }
            try {
                d.execSQL("CREATE TABLE live_ledger(id INTEGER PRIMARY KEY AUTOINCREMENT,ts INTEGER,book TEXT,event TEXT,mint TEXT,symbol TEXT,venue TEXT,pool TEXT,amount_raw INTEGER,usd_value REAL,sol_lamports INTEGER,signature TEXT,note TEXT)");
            } catch (Exception e5) {
            }
        }
        if (o < 4) {
            try {
                d.execSQL("ALTER TABLE position ADD COLUMN last_liquidity REAL DEFAULT 0");
            } catch (Exception e6) {
            }
        }
    }

    synchronized void upsertWatch(PoolSnap p) {
        SQLiteDatabase d = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("book", p.book);
        v.put("pool", p.pool);
        v.put("mint", p.mint);
        v.put("symbol", p.symbol);
        v.put("name", p.name);
        v.put("venue", p.venue);
        v.put("created", Long.valueOf(p.createdMs));
        v.put("first_seen", Long.valueOf(System.currentTimeMillis()));
        v.put("last_seen", Long.valueOf(System.currentTimeMillis()));
        v.put("state", "ACTIVE");
        v.put("last_reason", "WAIT_AGE");
        v.put("last_eval", (Integer) 0);
        long x = d.insertWithOnConflict("watch", null, v, 4);
        if (x == -1) {
            ContentValues u = new ContentValues();
            u.put("last_seen", Long.valueOf(System.currentTimeMillis()));
            u.put("symbol", p.symbol);
            u.put("name", p.name);
            u.put("venue", p.venue);
            d.update("watch", u, "pool=? AND state='ACTIVE'", new String[]{p.pool});
        }
    }

    synchronized List<PoolSnap> watchEligible(long now, double minAge, double maxAge) {
        ArrayList<PoolSnap> out;
        out = new ArrayList<>();
        long newest = now - ((long) (minAge * 60000.0d));
        long oldest = now - ((long) (60000.0d * maxAge));
        try (Cursor c = getReadableDatabase().rawQuery("SELECT book,pool,mint,symbol,name,venue,created FROM watch WHERE state='ACTIVE' AND created<=? AND created>=? ORDER BY created", new String[]{String.valueOf(newest), String.valueOf(oldest)})) {
            while (c.moveToNext()) {
                PoolSnap p = new PoolSnap();
                p.book = c.getString(0);
                p.pool = c.getString(1);
                p.mint = c.getString(2);
                p.symbol = c.getString(3);
                p.name = c.getString(4);
                p.venue = c.getString(5);
                p.createdMs = c.getLong(6);
                out.add(p);
            }
        }
        return out;
    }

    synchronized void watchState(String pool, String state) {
        watchState(pool, state, null);
    }

    synchronized void watchState(String pool, String state, String reason) {
        ContentValues v = new ContentValues();
        v.put("state", state);
        if (reason != null) {
            v.put("last_reason", reason);
        }
        v.put("last_eval", Long.valueOf(System.currentTimeMillis()));
        getWritableDatabase().update("watch", v, "pool=?", new String[]{pool});
    }

    synchronized void watchReason(String pool, String reason) {
        ContentValues v = new ContentValues();
        v.put("last_reason", reason == null ? "" : reason);
        v.put("last_eval", Long.valueOf(System.currentTimeMillis()));
        getWritableDatabase().update("watch", v, "pool=? AND state='ACTIVE'", new String[]{pool});
    }

    synchronized int expireWatch(long before) {
        ContentValues v;
        v = new ContentValues();
        v.put("state", "EXPIRED");
        return getWritableDatabase().update("watch", v, "state='ACTIVE' AND created<?", new String[]{String.valueOf(before)});
    }

    synchronized boolean hasSignal(String book, String pool) {
        boolean moveToFirst;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM signal WHERE book=? AND pool=? LIMIT 1", new String[]{book, pool})) {
            moveToFirst = c.moveToFirst();
        }
        return moveToFirst;
    }

    synchronized long addSignal(PoolSnap p, long due) {
        ContentValues v;
        v = new ContentValues();
        v.put("book", p.book);
        v.put("mint", p.mint);
        v.put("symbol", p.symbol);
        v.put("name", p.name);
        v.put("venue", p.venue);
        v.put("pool", p.pool);
        v.put("detected", Long.valueOf(System.currentTimeMillis()));
        v.put("fill_due", Long.valueOf(due));
        v.put("signal_price", Double.valueOf(p.priceUsd));
        v.put("liq", Double.valueOf(p.liquidityUsd));
        v.put("buyers", Integer.valueOf(p.buyersH1));
        v.put("buys", Integer.valueOf(p.buysH1));
        v.put("sells", Integer.valueOf(p.sellsH1));
        v.put("momentum", Double.valueOf(p.momentumH1));
        v.put("buy_sell", Double.valueOf(p.buySell()));
        v.put("state", "PENDING");
        v.put("reason", "");
        return getWritableDatabase().insert("signal", null, v);
    }

    synchronized List<Signal> pendingLive(long now) {
        ArrayList<Signal> out;
        out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,book,mint,symbol,name,venue,pool,detected,fill_due,signal_price,liq,buyers,buys,sells,momentum,buy_sell,state,reason FROM signal WHERE state='LIVE_PENDING' AND fill_due<=? ORDER BY fill_due", new String[]{String.valueOf(now)})) {
            while (c.moveToNext()) {
                Signal s = new Signal();
                int i = 0 + 1;
                s.id = c.getLong(0);
                int i2 = i + 1;
                s.book = c.getString(i);
                int i3 = i2 + 1;
                s.mint = c.getString(i2);
                int i4 = i3 + 1;
                s.symbol = c.getString(i3);
                int i5 = i4 + 1;
                s.name = c.getString(i4);
                int i6 = i5 + 1;
                s.venue = c.getString(i5);
                int i7 = i6 + 1;
                s.pool = c.getString(i6);
                int i8 = i7 + 1;
                s.detectedAt = c.getLong(i7);
                int i9 = i8 + 1;
                s.fillDue = c.getLong(i8);
                int i10 = i9 + 1;
                s.signalPrice = c.getDouble(i9);
                int i11 = i10 + 1;
                s.liquidity = c.getDouble(i10);
                int i12 = i11 + 1;
                s.buyers = c.getInt(i11);
                int i13 = i12 + 1;
                s.buys = c.getInt(i12);
                int i14 = i13 + 1;
                s.sells = c.getInt(i13);
                int i15 = i14 + 1;
                s.momentum = c.getDouble(i14);
                int i16 = i15 + 1;
                s.buySell = c.getDouble(i15);
                int i17 = i16 + 1;
                s.state = c.getString(i16);
                int i18 = i17 + 1;
                s.reason = c.getString(i17);
                out.add(s);
            }
        }
        return out;
    }

    synchronized List<Signal> pending(long now) {
        ArrayList<Signal> out;
        out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,book,mint,symbol,name,venue,pool,detected,fill_due,signal_price,liq,buyers,buys,sells,momentum,buy_sell,state,reason FROM signal WHERE state='PENDING' AND fill_due<=? ORDER BY fill_due", new String[]{String.valueOf(now)})) {
            while (c.moveToNext()) {
                Signal s = new Signal();
                int i = 0 + 1;
                s.id = c.getLong(0);
                int i2 = i + 1;
                s.book = c.getString(i);
                int i3 = i2 + 1;
                s.mint = c.getString(i2);
                int i4 = i3 + 1;
                s.symbol = c.getString(i3);
                int i5 = i4 + 1;
                s.name = c.getString(i4);
                int i6 = i5 + 1;
                s.venue = c.getString(i5);
                int i7 = i6 + 1;
                s.pool = c.getString(i6);
                int i8 = i7 + 1;
                s.detectedAt = c.getLong(i7);
                int i9 = i8 + 1;
                s.fillDue = c.getLong(i8);
                int i10 = i9 + 1;
                s.signalPrice = c.getDouble(i9);
                int i11 = i10 + 1;
                s.liquidity = c.getDouble(i10);
                int i12 = i11 + 1;
                s.buyers = c.getInt(i11);
                int i13 = i12 + 1;
                s.buys = c.getInt(i12);
                int i14 = i13 + 1;
                s.sells = c.getInt(i13);
                int i15 = i14 + 1;
                s.momentum = c.getDouble(i14);
                int i16 = i15 + 1;
                s.buySell = c.getDouble(i15);
                int i17 = i16 + 1;
                s.state = c.getString(i16);
                int i18 = i17 + 1;
                s.reason = c.getString(i17);
                out.add(s);
            }
        }
        return out;
    }

    synchronized void signalState(long id, String state, String reason) {
        ContentValues v = new ContentValues();
        v.put("state", state);
        v.put("reason", reason);
        getWritableDatabase().update("signal", v, "id=?", new String[]{String.valueOf(id)});
    }

    synchronized double cash(String book) {
        double d;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT cash FROM account WHERE book=?", new String[]{book})) {
            d = c.moveToFirst() ? c.getDouble(0) : 0.0d;
        }
        return d;
    }

    synchronized void setCash(String book, double cash) {
        ContentValues v = new ContentValues();
        v.put("cash", Double.valueOf(cash));
        getWritableDatabase().update("account", v, "book=?", new String[]{book});
    }

    synchronized int openCount(String book) {
        int i;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM position WHERE book=? AND state='OPEN'", new String[]{book})) {
            i = c.moveToFirst() ? c.getInt(0) : 0;
        }
        return i;
    }

    synchronized boolean mintOpen(String book, String mint) {
        boolean moveToFirst;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM position WHERE book=? AND mint=? AND state='OPEN' LIMIT 1", new String[]{book, mint})) {
            moveToFirst = c.moveToFirst();
        }
        return moveToFirst;
    }

    synchronized long openPosition(Signal s, double market, double effective, double stake, double qty) {
        ContentValues v;
        v = new ContentValues();
        v.put("book", s.book);
        v.put("mint", s.mint);
        v.put("symbol", s.symbol);
        v.put("name", s.name);
        v.put("venue", s.venue);
        v.put("pool", s.pool);
        v.put("entry_time", Long.valueOf(System.currentTimeMillis()));
        v.put("close_time", (Integer) 0);
        v.put("signal_price", Double.valueOf(s.signalPrice));
        v.put("entry_market", Double.valueOf(market));
        v.put("entry_effective", Double.valueOf(effective));
        v.put("stake", Double.valueOf(stake));
        v.put("original_qty", Double.valueOf(qty));
        v.put("remaining_qty", Double.valueOf(qty));
        v.put("realized", (Integer) 0);
        v.put("high_x", (Integer) 1);
        v.put("floor_x", Double.valueOf(0.675d));
        v.put("trail_peak", (Integer) 1);
        v.put("trail_active", (Integer) 0);
        v.put("hit7", (Integer) 0);
        v.put("hit20", (Integer) 0);
        v.put("last_price", Double.valueOf(market));
        v.put("last_liquidity", (Integer) 0);
        v.put("last_update", Long.valueOf(System.currentTimeMillis()));
        v.put("state", "OPEN");
        v.put("closed_pnl", (Integer) 0);
        return getWritableDatabase().insert("position", null, v);
    }

    synchronized List<Position> openPositions() {
        ArrayList<Position> out;
        out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,book,mint,symbol,name,venue,pool,entry_time,close_time,signal_price,entry_market,entry_effective,stake,original_qty,remaining_qty,realized,high_x,floor_x,trail_peak,trail_active,hit7,hit20,last_price,last_liquidity,last_update,state,closed_pnl FROM position WHERE state='OPEN' ORDER BY entry_time", null)) {
            while (c.moveToNext()) {
                out.add(readPos(c));
            }
        }
        return out;
    }

    synchronized List<Position> recentPositions(int limit) {
        ArrayList<Position> out;
        out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,book,mint,symbol,name,venue,pool,entry_time,close_time,signal_price,entry_market,entry_effective,stake,original_qty,remaining_qty,realized,high_x,floor_x,trail_peak,trail_active,hit7,hit20,last_price,last_liquidity,last_update,state,closed_pnl FROM position ORDER BY id DESC LIMIT " + Math.max(1, limit), null)) {
            while (c.moveToNext()) {
                out.add(readPos(c));
            }
        }
        return out;
    }

    private Position readPos(Cursor c) {
        Position p = new Position();
        int i = 0 + 1;
        p.id = c.getLong(0);
        int i2 = i + 1;
        p.book = c.getString(i);
        int i3 = i2 + 1;
        p.mint = c.getString(i2);
        int i4 = i3 + 1;
        p.symbol = c.getString(i3);
        int i5 = i4 + 1;
        p.name = c.getString(i4);
        int i6 = i5 + 1;
        p.venue = c.getString(i5);
        int i7 = i6 + 1;
        p.pool = c.getString(i6);
        int i8 = i7 + 1;
        p.entryTime = c.getLong(i7);
        int i9 = i8 + 1;
        p.closeTime = c.getLong(i8);
        int i10 = i9 + 1;
        p.signalPrice = c.getDouble(i9);
        int i11 = i10 + 1;
        p.entryMarketPrice = c.getDouble(i10);
        int i12 = i11 + 1;
        p.entryEffectivePrice = c.getDouble(i11);
        int i13 = i12 + 1;
        p.stakeUsd = c.getDouble(i12);
        int i14 = i13 + 1;
        p.originalQty = c.getDouble(i13);
        int i15 = i14 + 1;
        p.remainingQty = c.getDouble(i14);
        int i16 = i15 + 1;
        p.realizedProceeds = c.getDouble(i15);
        int i17 = i16 + 1;
        p.highX = c.getDouble(i16);
        int i18 = i17 + 1;
        p.floorX = c.getDouble(i17);
        int i19 = i18 + 1;
        p.trailPeakX = c.getDouble(i18);
        int i20 = i19 + 1;
        p.trailActive = c.getInt(i19) != 0;
        int i21 = i20 + 1;
        p.hit7 = c.getInt(i20) != 0;
        int i22 = i21 + 1;
        p.hit20 = c.getInt(i21) != 0;
        int i23 = i22 + 1;
        p.lastPrice = c.getDouble(i22);
        int i24 = i23 + 1;
        p.lastLiquidityUsd = c.getDouble(i23);
        int i25 = i24 + 1;
        p.lastUpdate = c.getLong(i24);
        int i26 = i25 + 1;
        p.state = c.getString(i25);
        int i27 = i26 + 1;
        p.closedPnl = c.getDouble(i26);
        return p;
    }

    synchronized void updatePosition(Position p) {
        ContentValues v = new ContentValues();
        v.put("remaining_qty", Double.valueOf(p.remainingQty));
        v.put("realized", Double.valueOf(p.realizedProceeds));
        v.put("high_x", Double.valueOf(p.highX));
        v.put("floor_x", Double.valueOf(p.floorX));
        v.put("trail_peak", Double.valueOf(p.trailPeakX));
        v.put("trail_active", Integer.valueOf(p.trailActive ? 1 : 0));
        v.put("hit7", Integer.valueOf(p.hit7 ? 1 : 0));
        v.put("hit20", Integer.valueOf(p.hit20 ? 1 : 0));
        v.put("last_price", Double.valueOf(p.lastPrice));
        v.put("last_liquidity", Double.valueOf(p.lastLiquidityUsd));
        v.put("last_update", Long.valueOf(p.lastUpdate));
        v.put("state", p.state);
        v.put("close_time", Long.valueOf(p.closeTime));
        v.put("closed_pnl", Double.valueOf(p.closedPnl));
        getWritableDatabase().update("position", v, "id=?", new String[]{String.valueOf(p.id)});
    }

    synchronized int liveOpenCount(String book) {
        int i;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM live_position WHERE book=? AND state='OPEN'", new String[]{book})) {
            i = c.moveToFirst() ? c.getInt(0) : 0;
        }
        return i;
    }

    synchronized boolean liveMintOpen(String book, String mint) {
        boolean moveToFirst;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM live_position WHERE book=? AND mint=? AND state='OPEN' LIMIT 1", new String[]{book, mint})) {
            moveToFirst = c.moveToFirst();
        }
        return moveToFirst;
    }

    synchronized double liveOpenStakeUsd(String book) {
        double d;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COALESCE(SUM(stake_usd),0) FROM live_position WHERE book=? AND state='OPEN'", new String[]{book})) {
            d = c.moveToFirst() ? c.getDouble(0) : 0.0d;
        }
        return d;
    }

    synchronized long openLivePosition(Signal s, double stakeUsd, long entryLamports, long outRaw, int decimals, double entryPriceUsd, String sig) {
        ContentValues v;
        v = new ContentValues();
        long now = System.currentTimeMillis();
        v.put("book", s.book);
        v.put("mint", s.mint);
        v.put("symbol", s.symbol);
        v.put("venue", s.venue);
        v.put("pool", s.pool);
        v.put("entry_time", Long.valueOf(now));
        v.put("close_time", (Integer) 0);
        v.put("stake_usd", Double.valueOf(stakeUsd));
        v.put("entry_lamports", Long.valueOf(entryLamports));
        v.put("entry_price_usd", Double.valueOf(entryPriceUsd));
        v.put("decimals", Integer.valueOf(decimals));
        v.put("original_raw", Long.valueOf(outRaw));
        v.put("remaining_raw", Long.valueOf(outRaw));
        v.put("realized_lamports", (Integer) 0);
        v.put("realized_usd", (Integer) 0);
        v.put("high_x", (Integer) 1);
        v.put("floor_x", Double.valueOf(0.675d));
        v.put("trail_peak", (Integer) 1);
        v.put("trail_active", (Integer) 0);
        v.put("hit7", (Integer) 0);
        v.put("hit20", (Integer) 0);
        v.put("last_price_usd", Double.valueOf(entryPriceUsd));
        v.put("last_update", Long.valueOf(now));
        v.put("state", "OPEN");
        v.put("buy_sig", sig);
        v.put("last_sell_sig", "");
        v.put("closed_pnl_usd", (Integer) 0);
        return getWritableDatabase().insert("live_position", null, v);
    }

    synchronized List<LivePosition> openLivePositions() {
        ArrayList<LivePosition> out;
        out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,book,mint,symbol,venue,pool,entry_time,close_time,stake_usd,entry_lamports,entry_price_usd,decimals,original_raw,remaining_raw,realized_lamports,realized_usd,high_x,floor_x,trail_peak,trail_active,hit7,hit20,last_price_usd,last_update,state,buy_sig,last_sell_sig,closed_pnl_usd FROM live_position WHERE state='OPEN' ORDER BY entry_time", null)) {
            while (c.moveToNext()) {
                out.add(readLivePos(c));
            }
        }
        return out;
    }

    synchronized List<LivePosition> recentLivePositions(int limit) {
        ArrayList<LivePosition> out;
        out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,book,mint,symbol,venue,pool,entry_time,close_time,stake_usd,entry_lamports,entry_price_usd,decimals,original_raw,remaining_raw,realized_lamports,realized_usd,high_x,floor_x,trail_peak,trail_active,hit7,hit20,last_price_usd,last_update,state,buy_sig,last_sell_sig,closed_pnl_usd FROM live_position ORDER BY id DESC LIMIT " + Math.max(1, limit), null)) {
            while (c.moveToNext()) {
                out.add(readLivePos(c));
            }
        }
        return out;
    }

    private LivePosition readLivePos(Cursor c) {
        LivePosition p = new LivePosition();
        int i = 0 + 1;
        p.id = c.getLong(0);
        int i2 = i + 1;
        p.book = c.getString(i);
        int i3 = i2 + 1;
        p.mint = c.getString(i2);
        int i4 = i3 + 1;
        p.symbol = c.getString(i3);
        int i5 = i4 + 1;
        p.venue = c.getString(i4);
        int i6 = i5 + 1;
        p.pool = c.getString(i5);
        int i7 = i6 + 1;
        p.entryTime = c.getLong(i6);
        int i8 = i7 + 1;
        p.closeTime = c.getLong(i7);
        int i9 = i8 + 1;
        p.stakeUsd = c.getDouble(i8);
        int i10 = i9 + 1;
        p.entryLamports = c.getLong(i9);
        int i11 = i10 + 1;
        p.entryPriceUsd = c.getDouble(i10);
        int i12 = i11 + 1;
        p.decimals = c.getInt(i11);
        int i13 = i12 + 1;
        p.originalRaw = c.getLong(i12);
        int i14 = i13 + 1;
        p.remainingRaw = c.getLong(i13);
        int i15 = i14 + 1;
        p.realizedLamports = c.getLong(i14);
        int i16 = i15 + 1;
        p.realizedUsd = c.getDouble(i15);
        int i17 = i16 + 1;
        p.highX = c.getDouble(i16);
        int i18 = i17 + 1;
        p.floorX = c.getDouble(i17);
        int i19 = i18 + 1;
        p.trailPeakX = c.getDouble(i18);
        int i20 = i19 + 1;
        p.trailActive = c.getInt(i19) != 0;
        int i21 = i20 + 1;
        p.hit7 = c.getInt(i20) != 0;
        int i22 = i21 + 1;
        p.hit20 = c.getInt(i21) != 0;
        int i23 = i22 + 1;
        p.lastPriceUsd = c.getDouble(i22);
        int i24 = i23 + 1;
        p.lastUpdate = c.getLong(i23);
        int i25 = i24 + 1;
        p.state = c.getString(i24);
        int i26 = i25 + 1;
        p.buySig = c.getString(i25);
        int i27 = i26 + 1;
        p.lastSellSig = c.getString(i26);
        int i28 = i27 + 1;
        p.closedPnlUsd = c.getDouble(i27);
        return p;
    }

    synchronized void updateLivePosition(LivePosition p) {
        ContentValues v = new ContentValues();
        v.put("remaining_raw", Long.valueOf(p.remainingRaw));
        v.put("realized_lamports", Long.valueOf(p.realizedLamports));
        v.put("realized_usd", Double.valueOf(p.realizedUsd));
        v.put("high_x", Double.valueOf(p.highX));
        v.put("floor_x", Double.valueOf(p.floorX));
        v.put("trail_peak", Double.valueOf(p.trailPeakX));
        v.put("trail_active", Integer.valueOf(p.trailActive ? 1 : 0));
        v.put("hit7", Integer.valueOf(p.hit7 ? 1 : 0));
        v.put("hit20", Integer.valueOf(p.hit20 ? 1 : 0));
        v.put("last_price_usd", Double.valueOf(p.lastPriceUsd));
        v.put("last_update", Long.valueOf(p.lastUpdate));
        v.put("state", p.state);
        v.put("close_time", Long.valueOf(p.closeTime));
        v.put("last_sell_sig", p.lastSellSig);
        v.put("closed_pnl_usd", Double.valueOf(p.closedPnlUsd));
        getWritableDatabase().update("live_position", v, "id=?", new String[]{String.valueOf(p.id)});
    }

    synchronized void liveLedger(long ts, String book, String event, String mint, String symbol, String venue, String pool, long raw, double usd, long lamports, String sig, String note) {
        ContentValues v = new ContentValues();
        v.put("ts", Long.valueOf(ts));
        v.put("book", book);
        v.put("event", event);
        v.put("mint", mint);
        v.put("symbol", symbol);
        v.put("venue", venue);
        v.put("pool", pool);
        v.put("amount_raw", Long.valueOf(raw));
        v.put("usd_value", Double.valueOf(usd));
        v.put("sol_lamports", Long.valueOf(lamports));
        v.put("signature", sig);
        v.put("note", note);
        getWritableDatabase().insert("live_ledger", null, v);
    }

    synchronized LiveStats liveStats() {
        LiveStats s = new LiveStats();
        long start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT decimals,remaining_raw,last_price_usd FROM live_position WHERE state='OPEN'", null)) {
            while (c.moveToNext()) {
                s.openCount++;
                double units = c.getLong(1) / Math.pow(10.0d, c.getInt(0));
                s.openExposureUsd += c.getDouble(2) * units;
            }
        }
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COALESCE(SUM(closed_pnl_usd),0) FROM live_position WHERE state='CLOSED' AND close_time>=?", new String[]{String.valueOf(start)})) {
            if (c.moveToFirst()) {
                s.realizedPnlTodayUsd = c.getDouble(0);
            }
        }
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM live_ledger WHERE event='BUY'", null)) {
            if (c.moveToFirst()) {
                s.liveBuys = c.getInt(0);
            }
        }
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM live_ledger WHERE event LIKE 'SELL%'", null)) {
            if (c.moveToFirst()) {
                s.liveSells = c.getInt(0);
            }
        }
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM live_ledger WHERE event='ERROR'", null)) {
            if (c.moveToFirst()) {
                s.failedOrders = c.getInt(0);
            }
        }
        return s;
    }

    synchronized void resetLiveLogs() {
        SQLiteDatabase d = getWritableDatabase();
        d.delete("live_position", null, null);
        d.delete("live_ledger", null, null);
        d.execSQL("UPDATE signal SET state='SKIPPED', reason='live logs reset' WHERE state='LIVE_PENDING'");
    }

    synchronized void ledger(long ts, String book, String event, String mint, String symbol, String venue, String pool, double market, double effective, double qty, double proceeds, double pnl, String note) {
        ContentValues v = new ContentValues();
        v.put("ts", Long.valueOf(ts));
        v.put("book", book);
        v.put("event", event);
        v.put("mint", mint);
        v.put("symbol", symbol);
        v.put("venue", venue);
        v.put("pool", pool);
        v.put("market_price", Double.valueOf(market));
        v.put("effective_price", Double.valueOf(effective));
        v.put("qty", Double.valueOf(qty));
        v.put("proceeds", Double.valueOf(proceeds));
        v.put("pnl", Double.valueOf(pnl));
        v.put("note", note);
        getWritableDatabase().insert("ledger", null, v);
    }

    synchronized void skip(String book, String reason) {
        SQLiteDatabase d = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("book", book);
        v.put("reason", reason);
        v.put("count", (Integer) 1);
        long x = d.insertWithOnConflict("skip", null, v, 4);
        if (x == -1) {
            d.execSQL("UPDATE skip SET count=count+1 WHERE book=? AND reason=?", new Object[]{book, reason});
        }
    }

    synchronized String skipText(String book) {
        StringBuilder b = new StringBuilder();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT reason,count FROM skip WHERE book=? ORDER BY count DESC LIMIT 8", new String[]{book})) {
            while (c.moveToNext()) {
                if (b.length() > 0) {
                    b.append(" · ");
                }
                b.append(c.getString(0)).append(" ").append(c.getInt(1));
            }
        }
        return b.length() == 0 ? "none" : b.toString();
    }

    synchronized ScannerTelemetry telemetry(String book, long now, double minAge, double maxAge) {
        ScannerTelemetry t;
        t = new ScannerTelemetry();
        t.book = book;
        long minCut = now - ((long) (maxAge * 60000.0d));
        long maxCut = now - ((long) (60000.0d * minAge));
        SQLiteDatabase d = getReadableDatabase();
        t.discovered = count(d, "SELECT COUNT(*) FROM watch WHERE book=?", new String[]{book});
        t.active = count(d, "SELECT COUNT(*) FROM watch WHERE book=? AND state='ACTIVE'", new String[]{book});
        t.waitingAge = count(d, "SELECT COUNT(*) FROM watch WHERE book=? AND state='ACTIVE' AND created>?", new String[]{book, String.valueOf(maxCut)});
        t.eligible = count(d, "SELECT COUNT(*) FROM watch WHERE book=? AND state='ACTIVE' AND created>=? AND created<=?", new String[]{book, String.valueOf(minCut), String.valueOf(maxCut)});
        t.qualified = count(d, "SELECT COUNT(*) FROM signal WHERE book=?", new String[]{book});
        t.pending = count(d, "SELECT COUNT(*) FROM signal WHERE book=? AND state='PENDING'", new String[]{book});
        t.fills = count(d, "SELECT COUNT(*) FROM ledger WHERE book=? AND event='BUY'", new String[]{book});
        t.rejected = count(d, "SELECT COUNT(*) FROM watch WHERE book=? AND state='EXPIRED'", new String[]{book});
        t.fillFailed = count(d, "SELECT COUNT(*) FROM signal WHERE book=? AND state IN ('FAILED','SKIPPED')", new String[]{book});
        StringBuilder b = new StringBuilder();
        try (Cursor c = d.rawQuery("SELECT last_reason,COUNT(*) n FROM watch WHERE book=? AND state='ACTIVE' AND created>=? AND created<=? AND last_reason<>'' GROUP BY last_reason ORDER BY n DESC", new String[]{book, String.valueOf(minCut), String.valueOf(maxCut)})) {
            while (c.moveToNext()) {
                if (b.length() > 0) {
                    b.append(" · ");
                }
                b.append(c.getString(0)).append(" ").append(c.getInt(1));
            }
        }
        t.reasons = b.length() == 0 ? "none" : b.toString();
        return t;
    }

    private int count(SQLiteDatabase d, String q, String[] a) {
        Cursor c = d.rawQuery(q, a);
        try {
            int i = c.moveToFirst() ? c.getInt(0) : 0;
            if (c != null) {
                c.close();
            }
            return i;
        } catch (Throwable th) {
            if (c != null) {
                try {
                    c.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
            }
            throw th;
        }
    }

    synchronized void markSnapshot(String book, double equity, double unreal) {
        SQLiteDatabase d = getWritableDatabase();
        double cash = cash(book);
        double peak = 1000.0d;
        double maxdd = 0.0d;
        try (Cursor c = d.rawQuery("SELECT peak,max_dd FROM account WHERE book=?", new String[]{book})) {
            if (c.moveToFirst()) {
                peak = c.getDouble(0);
                maxdd = c.getDouble(1);
            }
        }
        if (equity > peak) {
            peak = equity;
        }
        double dd = peak > 0.0d ? ((peak - equity) * 100.0d) / peak : 0.0d;
        if (dd > maxdd) {
            maxdd = dd;
        }
        ContentValues a = new ContentValues();
        a.put("peak", Double.valueOf(peak));
        a.put("max_dd", Double.valueOf(maxdd));
        d.update("account", a, "book=?", new String[]{book});
        ContentValues v = new ContentValues();
        v.put("ts", Long.valueOf(System.currentTimeMillis()));
        v.put("book", book);
        v.put("equity", Double.valueOf(equity));
        v.put("cash", Double.valueOf(cash));
        v.put("unreal", Double.valueOf(unreal));
        v.put("dd", Double.valueOf(dd));
        d.insert("snapshot", null, v);
        d.execSQL("DELETE FROM snapshot WHERE id IN (SELECT id FROM snapshot WHERE book=? ORDER BY id DESC LIMIT -1 OFFSET 5000)", new Object[]{book});
    }

    synchronized BookStats stats(String book) {
        BookStats s = new BookStats();
        s.book = book;
        s.cash = cash(book);
        double openValue = 0.0d;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT remaining_qty,last_price,last_liquidity FROM position WHERE book=? AND state='OPEN'", new String[]{book})) {
            while (c.moveToNext()) {
                openValue += paperLiquidationValue(c.getDouble(0), c.getDouble(1), c.getDouble(2), false);
            }
        }
        s.unrealized = openValue;
        s.equity = s.cash + openValue;
        s.roiPct = ((s.equity / 1000.0d) - 1.0d) * 100.0d;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT peak,max_dd FROM account WHERE book=?", new String[]{book})) {
            if (c.moveToFirst()) {
                double peak = c.getDouble(0);
                s.maxDdPct = c.getDouble(1);
                s.currentDdPct = peak > 0.0d ? ((peak - s.equity) * 100.0d) / peak : 0.0d;
            }
        }
        s.open = openCount(book);
        ArrayList<Double> pnls = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT closed_pnl FROM position WHERE book=? AND state='CLOSED'", new String[]{book})) {
            while (c.moveToNext()) {
                pnls.add(Double.valueOf(c.getDouble(0)));
            }
        }
        s.closed = pnls.size();
        double win = 0.0d;
        double loss = 0.0d;
        double sw = 0.0d;
        double sl = 0.0d;
        for (Double d : pnls) {
            double x = d.doubleValue();
            if (x > 0.0d) {
                s.wins++;
                win += x;
                sw += x;
            } else if (x < 0.0d) {
                s.losses++;
                loss += -x;
                sl += x;
            }
        }
        s.profitFactor = (loss == 0.0d && win > 0.0d) ? Double.POSITIVE_INFINITY : loss > 0.0d ? win / loss : 0.0d;
        s.expectancy = s.closed > 0 ? (sw + sl) / s.closed : 0.0d;
        s.avgWin = s.wins > 0 ? sw / s.wins : 0.0d;
        s.avgLoss = s.losses > 0 ? sl / s.losses : 0.0d;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM signal WHERE book=?", new String[]{book})) {
            if (c.moveToFirst()) {
                s.signals = c.getInt(0);
            }
        }
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM ledger WHERE book=? AND event='BUY'", new String[]{book})) {
            if (c.moveToFirst()) {
                s.fills = c.getInt(0);
            }
        }
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COALESCE(SUM(count),0) FROM skip WHERE book=?", new String[]{book})) {
            if (c.moveToFirst()) {
                s.skips = c.getInt(0);
            }
        }
        return s;
    }

    synchronized List<Signal> recentSignals(int limit) {
        ArrayList<Signal> out;
        out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,book,mint,symbol,name,venue,pool,detected,fill_due,signal_price,liq,buyers,buys,sells,momentum,buy_sell,state,reason FROM signal ORDER BY id DESC LIMIT " + Math.max(1, limit), null)) {
            while (c.moveToNext()) {
                Signal s = new Signal();
                int i = 0 + 1;
                s.id = c.getLong(0);
                int i2 = i + 1;
                s.book = c.getString(i);
                int i3 = i2 + 1;
                s.mint = c.getString(i2);
                int i4 = i3 + 1;
                s.symbol = c.getString(i3);
                int i5 = i4 + 1;
                s.name = c.getString(i4);
                int i6 = i5 + 1;
                s.venue = c.getString(i5);
                int i7 = i6 + 1;
                s.pool = c.getString(i6);
                int i8 = i7 + 1;
                s.detectedAt = c.getLong(i7);
                int i9 = i8 + 1;
                s.fillDue = c.getLong(i8);
                int i10 = i9 + 1;
                s.signalPrice = c.getDouble(i9);
                int i11 = i10 + 1;
                s.liquidity = c.getDouble(i10);
                int i12 = i11 + 1;
                s.buyers = c.getInt(i11);
                int i13 = i12 + 1;
                s.buys = c.getInt(i12);
                int i14 = i13 + 1;
                s.sells = c.getInt(i13);
                int i15 = i14 + 1;
                s.momentum = c.getDouble(i14);
                int i16 = i15 + 1;
                s.buySell = c.getDouble(i15);
                int i17 = i16 + 1;
                s.state = c.getString(i16);
                int i18 = i17 + 1;
                s.reason = c.getString(i17);
                out.add(s);
            }
        }
        return out;
    }

    synchronized List<double[]> combinedSnapshots(int limit) {
        ArrayList<double[]> all = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT ts,book,equity FROM snapshot ORDER BY ts ASC", null)) {
            double ray = Double.NaN;
            double ps = Double.NaN;
            double pf = Double.NaN;
            while (c.moveToNext()) {
                long ts = c.getLong(0);
                String b = c.getString(1);
                double e = c.getDouble(2);
                if ("RAYDIUM".equals(b)) {
                    ray = e;
                } else if ("PUMPSWAP".equals(b)) {
                    ps = e;
                } else if ("PUMPFUN".equals(b)) {
                    pf = e;
                }
                if (Double.isFinite(ray) && Double.isFinite(ps) && Double.isFinite(pf)) {
                    all.add(new double[]{ts, ray + ps + pf});
                }
            }
        }
        int from = Math.max(0, all.size() - Math.max(2, limit));
        return new ArrayList(all.subList(from, all.size()));
    }

    synchronized int signalCountByState(String state) {
        int i;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM signal WHERE state=?", new String[]{state})) {
            i = c.moveToFirst() ? c.getInt(0) : 0;
        }
        return i;
    }

    synchronized int totalSignals() {
        int i;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM signal", null)) {
            i = c.moveToFirst() ? c.getInt(0) : 0;
        }
        return i;
    }

    synchronized String recentLedger(int limit) {
        StringBuilder b = new StringBuilder();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT ts,book,event,symbol,mint,venue,pool,market_price,proceeds,pnl,note FROM ledger ORDER BY id DESC LIMIT " + Math.max(1, limit), null)) {
            while (c.moveToNext()) {
                b.append(new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(c.getLong(0)))).append(" ").append(c.getString(1)).append(" ").append(c.getString(2)).append(" ").append(c.getString(3)).append("\n").append("  ").append(c.getString(5)).append("  price $").append(fmt(c.getDouble(7))).append("  proceeds $").append(fmt(c.getDouble(8))).append("  pnl $").append(fmt(c.getDouble(9))).append("\n").append("  mint ").append(c.getString(4)).append("\n  pool ").append(c.getString(6)).append("\n").append("  ").append(c.getString(10) == null ? "" : c.getString(10)).append("\n\n");
            }
        }
        return b.toString();
    }

    synchronized List<double[]> snapshots(String book, int limit) {
        ArrayList<double[]> a = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT ts,equity FROM snapshot WHERE book=? ORDER BY id DESC LIMIT " + Math.max(2, limit), new String[]{book})) {
            while (c.moveToNext()) {
                a.add(new double[]{c.getLong(0), c.getDouble(1)});
            }
        }
        Collections.reverse(a);
        return a;
    }

    synchronized void resetPaper() {
        SQLiteDatabase d = getWritableDatabase();
        d.delete("watch", null, null);
        d.delete("signal", null, null);
        d.delete("position", null, null);
        d.delete("ledger", null, null);
        d.delete("snapshot", null, null);
        d.delete("skip", null, null);
        d.delete("account", null, null);
        for (String b : Config.BOOKS) {
            d.execSQL("INSERT INTO account(book,cash,peak,max_dd,created) VALUES(?,?,?,?,?)", new Object[]{b, Double.valueOf(1000.0d), Double.valueOf(1000.0d), 0, Long.valueOf(System.currentTimeMillis())});
        }
    }

    private double paperLiquidationValue(double qty, double price, double liquidity, boolean finalClose) {
        double notional = Math.max(0.0d, qty * price);
        double gross = notional;
        if (liquidity > 0.0d && Double.isFinite(liquidity)) {
            double quoteReserve = Math.max(1.0d, 0.5d * liquidity);
            gross = (quoteReserve * notional) / (quoteReserve + notional);
        }
        double gross2 = gross * Math.max(0.0d, 1.0d - (Prefs.exitBps(this.app) / 10000.0d));
        if (finalClose) {
            return Math.max(0.0d, gross2 - Prefs.fixedFeeUsd(this.app));
        }
        return gross2;
    }

    static String fmt(double x) {
        return !Double.isFinite(x) ? "—" : Math.abs(x) >= 1.0d ? String.format(Locale.US, "%.4f", Double.valueOf(x)) : String.format(Locale.US, "%.8f", Double.valueOf(x));
    }
}
