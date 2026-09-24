package com.benknight.mwsl;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import com.benknight.mwsl.PaperEngine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

public class TrackingService extends Service implements PaperEngine.Listener {
    static final String ACTION_BACKFILL = "com.benknight.mwsl.BACKFILL";
    static final String ACTION_START = "com.benknight.mwsl.START";
    static final String ACTION_STOP = "com.benknight.mwsl.STOP";
    static final String ACTION_SYNC = "com.benknight.mwsl.SYNC";
    private static final String CH_SIGNAL = "mwsl_signals";
    private static final String CH_TRACK = "mwsl_tracking";
    private static final int NOTIF_ID = 1001;
    private Db db;
    private PaperEngine engine;
    private ExecutorService loopExec;
    private ExecutorService maintenanceExec;

    private Network net;
    private final AtomicBoolean workerRunning = new AtomicBoolean(false);
    private final AtomicBoolean syncNow = new AtomicBoolean(false);
    private final AtomicBoolean backfillRunning = new AtomicBoolean(false);
    private final Map<String, Long> researchLastPoll = new HashMap();
    private volatile boolean stopping = false;
    private long lastPriceMark = 0;
    private volatile String lastLog = "Starting…";

    @Override // android.app.Service
    public void onCreate() {
        super.onCreate();
        Prefs.setHeartbeat(this, System.currentTimeMillis());
        this.db = new Db(this);
        this.db.ensureSeeded();
        this.net = new Network(this);
        this.engine = new PaperEngine(this, this.db, this.net, this);
        createChannels();
        startAsForeground();
    }

    @Override // android.app.Service
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(a)) {
            Prefs.setTrackingEnabled(this, false);
            this.stopping = true;
            stopForeground(true);
            stopSelf();
            return 2;
        }
        if (ACTION_BACKFILL.equals(a)) {
            Prefs.setTrackingEnabled(this, true);
            startWorker();
            startBackfill();
            return 1;
        }
        if (ACTION_SYNC.equals(a)) {
            this.syncNow.set(true);
        }
        Prefs.setTrackingEnabled(this, true);
        startWorker();
        return 1;
    }

    public /* synthetic */ void lambda$startWorker$0() {
        while (!this.stopping) {
            long began = System.currentTimeMillis();
            Prefs.setHeartbeat(this, began);
            try {
                syncAll();
                this.engine.executeDue();
                long now = System.currentTimeMillis();
                if (now - this.lastPriceMark >= ((long) Prefs.pricePollSec(this)) * 1000L) {
                    this.engine.markPrices();
                    this.lastPriceMark = now;
                }
                updateServiceNotification();
            } catch (Exception e) {
                this.lastLog = "Loop: " + e.getMessage();
                updateServiceNotification();
            }
            long elapsed = System.currentTimeMillis() - began;
            long wait = Math.max(1000L, (((long) Prefs.pollSec(this)) * 1000L) - elapsed);
            if (this.syncNow.getAndSet(false)) {
                wait = 250;
            }
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e2) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        this.workerRunning.set(false);
    }

    private void startWorker() {
        if (this.workerRunning.getAndSet(true)) {
            return;
        }
        this.stopping = false;
        this.loopExec = Executors.newSingleThreadExecutor();
        this.loopExec.submit(new Runnable() { // from class: com.benknight.mwsl.TrackingService$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                TrackingService.this.lambda$startWorker$0();
            }
        });
    }

    private void syncAll() {
        long now = System.currentTimeMillis();
        long researchEvery = Math.max(300000L, ((long) Prefs.pollSec(this)) * 10000L);
        for (TraderDef d : Config.TRADERS) {
            if (this.stopping) {
                return;
            }
            if (d.researchOnly()) {
                Long last = this.researchLastPoll.get(d.id);
                if (last != null && now - last.longValue() < researchEvery) {
                    continue;
                }
                this.researchLastPoll.put(d.id, Long.valueOf(now));
            }
            syncTrader(d);
        }
        this.engine.executeDue();
    }

    private void syncTrader(TraderDef def) {
        TraderState tr = this.db.trader(def.id);
        if (tr == null) {
            return;
        }
        try {
            if (tr.lastSignature == null || tr.lastSignature.isEmpty()) {
                JSONArray head = retrySignatures(def.wallet, null, null, 1, 3);
                this.db.setBaseline(def.id, head.length() > 0 ? head.getJSONObject(0).optString("signature", null) : null, System.currentTimeMillis());
                this.lastLog = def.name + ": live baseline set";
                return;
            }
            List<JSONObject> found = new ArrayList<>();
            int cap = Math.max(5000, Prefs.maxBackfillTx(this));
            String before = null;
            String newest = null;
            while (found.size() < cap) {
                JSONArray page = retrySignatures(def.wallet, tr.lastSignature, before, Math.min(1000, cap - found.size()), 3);
                if (page.length() == 0) {
                    break;
                }
                if (newest == null) {
                    newest = page.getJSONObject(0).optString("signature", null);
                }
                for (int i = 0; i < page.length(); i++) {
                    found.add(page.getJSONObject(i));
                }
                if (page.length() < 1000) {
                    break;
                }
                before = page.getJSONObject(page.length() - 1).optString("signature", null);
                if (before == null || before.isEmpty()) {
                    break;
                }
            }
            if (found.isEmpty()) {
                this.db.setLastSync(def.id, null, System.currentTimeMillis());
                return;
            }
            Map<String, JSONObject> cieloBySig = new HashMap<>();
            Map<String, JSONObject> heliusBySig = new HashMap<>();
            if (!Prefs.cieloKey(this).isEmpty()) {
                try {
                    JSONArray cs = this.net.cieloSwaps(def.wallet, Math.min(100, Math.max(20, found.size())));
                    for (int i3 = 0; i3 < cs.length(); i3++) {
                        JSONObject x = cs.optJSONObject(i3);
                        if (x != null && !x.optString("tx_hash", "").isEmpty()) {
                            cieloBySig.put(x.optString("tx_hash"), x);
                        }
                    }
                } catch (Exception e2) {
                    onLog(def.name + " Cielo fallback: " + e2.getMessage());
                }
            }
            if (!Prefs.heliusKey(this).isEmpty()) {
                try {
                    JSONArray hs = this.net.heliusEnhanced(def.wallet, Math.min(100, Math.max(20, found.size())));
                    for (int i4 = 0; i4 < hs.length(); i4++) {
                        JSONObject x2 = hs.optJSONObject(i4);
                        if (x2 != null && !x2.optString("signature", "").isEmpty()) {
                            heliusBySig.put(x2.optString("signature"), x2);
                        }
                    }
                } catch (Exception e3) {
                    onLog(def.name + " Helius enhanced: " + e3.getMessage());
                }
            }
            Collections.reverse(found);
            for (JSONObject s : found) {
                String sig = s.optString("signature", "");
                if (sig.isEmpty() || (s.opt("err") != null && !s.isNull("err"))) {
                    continue;
                }
                if (this.db.signatureProcessed(def.id, sig)) {
                    continue;
                }
                try {
                    JSONObject tx = retryTransaction(sig, 2);
                    List<TradeSignal> es = SolanaParser.parseTrades(def.wallet, s, tx);
                    if (es.isEmpty()) {
                        TradeSignal ce = EnhancedParser.fromCielo(cieloBySig.get(sig), def.wallet);
                        if (ce != null) {
                            es.add(ce);
                        } else if (EnhancedParser.heliusSaysSwap(heliusBySig.get(sig))) {
                            onLog(def.name + " Helius says SWAP; raw parser could not safely derive direction/size for " + Db.shortAddr(sig));
                        }
                    }
                    TraderState current = this.db.trader(def.id);
                    if (def.paperEnabled) {
                        for (TradeSignal e : es) {
                            this.engine.recordSignal(current, e, false);
                        }
                    } else if (!es.isEmpty()) {
                        onLog(def.name + " research-only: " + es.size() + " attributable trade signal(s) observed; no paper fill");
                    }
                    this.db.markSignatureProcessed(def.id, sig, System.currentTimeMillis());
                } catch (Exception e) {
                    onLog(def.name + " tx " + Db.shortAddr(sig) + ": " + e.getMessage());
                }
            }
            this.db.setLastSync(def.id, newest, System.currentTimeMillis());
            this.lastLog = def.name + ": " + found.size() + " new transaction(s) checked";
        } catch (Exception e) {
            this.db.setError(def.id, e.getMessage());
            this.lastLog = def.name + " sync error: " + e.getMessage();
        }
    }

    private JSONArray retrySignatures(String wallet, String until, String before, int limit, int attempts) throws Exception {
        Exception last = null;
        for (int i = 0; i < attempts; i++) {
            try {
                return this.net.signatures(wallet, until, before, limit);
            } catch (Exception e) {
                last = e;
                try {
                    Thread.sleep((1L << i) * 400L);
                } catch (InterruptedException e2) {
                }
            }
        }
        if (last == null) {
            throw new Exception("signature fetch failed");
        }
        throw last;
    }

    private JSONObject retryTransaction(String sig, int attempts) throws Exception {
        Exception last = null;
        for (int i = 0; i < attempts; i++) {
            try {
                return this.net.transaction(sig);
            } catch (Exception e) {
                last = e;
                try {
                    Thread.sleep((1L << i) * 350L);
                } catch (InterruptedException e2) {
                }
            }
        }
        if (last == null) {
            throw new Exception("transaction fetch failed");
        }
        throw last;
    }

    public /* synthetic */ void lambda$startBackfill$1() {
        String str;
        TraderState tr;
        TraderDef def;
        String str2;
        Iterator<JSONObject> it;
        String str3;
        TraderState tr2;
        try {
            try {
                long cutoff = System.currentTimeMillis() - (((long) Prefs.backfillDays(this)) * 86400000L);
                int max = Prefs.maxBackfillTx(this);
                for (TraderDef def2 : Config.TRADERS) {
                    if (this.stopping) {
                        break;
                    }
                    if (def2.paperEnabled) {
                        TraderState tr3 = this.db.trader(def2.id);
                        String before = null;
                        List<JSONObject> all = new ArrayList<>();
                        boolean done = false;
                        while (true) {
                            str = "Backfill ";
                            if (done || all.size() >= max) {
                                break;
                            }
                            List<JSONObject> all2 = all;
                            tr = tr3;
                            def = def2;
                            JSONArray page = retrySignatures(def2.wallet, null, before, Math.min(1000, max - all.size()), 3);
                            if (page.length() == 0) {
                                str2 = "signature";
                                str = "Backfill ";
                                all = all2;
                                break;
                            }
                            int i = 0;
                            while (true) {
                                if (i >= page.length()) {
                                    all = all2;
                                    break;
                                }
                                JSONObject s = page.getJSONObject(i);
                                long bt = s.optLong("blockTime", 0L) * 1000;
                                if (bt > 0 && bt < cutoff) {
                                    done = true;
                                    all = all2;
                                    break;
                                } else {
                                    List<JSONObject> all3 = all2;
                                    all3.add(s);
                                    i++;
                                    all2 = all3;
                                }
                            }
                            int i2 = page.length();
                            str2 = "signature";
                            String before2 = page.getJSONObject(i2 - 1).optString(str2, null);
                            str = "Backfill ";
                            this.lastLog = str + def.name + ": " + all.size() + " signatures";
                            updateServiceNotification();
                            if (page.length() < 1000) {
                                break;
                            }
                            before = before2;
                            def2 = def;
                            tr3 = tr;
                        }
                        tr = tr3;
                        def = def2;
                        str2 = "signature";
                        Collections.reverse(all);
                        Iterator<JSONObject> it2 = all.iterator();
                        int n = 0;
                        while (it2.hasNext()) {
                            JSONObject s2 = it2.next();
                            if (this.stopping) {
                                break;
                            }
                            String sig = s2.optString(str2, "");
                            if (sig.isEmpty()) {
                                it = it2;
                                str3 = str2;
                                tr2 = tr;
                            } else if (s2.opt("err") == null || s2.isNull("err")) {
                                it = it2;
                                try {
                                    JSONObject tx = retryTransaction(sig, 2);
                                    try {
                                        Iterator<TradeSignal> it3 = SolanaParser.parseTrades(def.wallet, s2, tx).iterator();
                                        while (it3.hasNext()) {
                                            TradeSignal e = it3.next();
                                            JSONObject tx2 = tx;
                                            Iterator<TradeSignal> it4 = it3;
                                            JSONObject s3 = s2;
                                            tr2 = tr;
                                            str3 = str2;
                                            try {
                                                this.engine.recordSignal(tr2, e, true);
                                                tr = tr2;
                                                tx = tx2;
                                                s2 = s3;
                                                str2 = str3;
                                                it3 = it4;
                                            } catch (Exception e2) {
                                            }
                                        }
                                        str3 = str2;
                                        tr2 = tr;
                                    } catch (Exception e3) {
                                        str3 = str2;
                                        tr2 = tr;
                                    }
                                } catch (Exception e4) {
                                    str3 = str2;
                                    tr2 = tr;
                                }
                                n++;
                                if (n % 25 == 0) {
                                    this.lastLog = str + def.name + ": decoded " + n + "/" + all.size();
                                    updateServiceNotification();
                                }
                            } else {
                                it = it2;
                                str3 = str2;
                                tr2 = tr;
                            }
                            tr = tr2;
                            it2 = it;
                            str2 = str3;
                        }
                    }
                }
                this.lastLog = "Historical backfill complete";
            } catch (Exception e5) {
                this.lastLog = "Backfill stopped: " + e5.getMessage();
            }
        } finally {
            this.backfillRunning.set(false);
            updateServiceNotification();
        }
    }

    private void startBackfill() {
        if (this.backfillRunning.getAndSet(true)) {
            return;
        }
        if (this.maintenanceExec == null) {
            this.maintenanceExec = Executors.newSingleThreadExecutor();
        }
        this.maintenanceExec.submit(new Runnable() { // from class: com.benknight.mwsl.TrackingService$$ExternalSyntheticLambda1
            @Override // java.lang.Runnable
            public final void run() {
                TrackingService.this.lambda$startBackfill$1();
            }
        });
    }

    @Override // com.benknight.mwsl.PaperEngine.Listener
    public void onSignal(String title, String body) {
        NotificationManager nm = (NotificationManager) getSystemService("notification");
        Intent i = new Intent(this, (Class<?>) MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, 201326592);
        Notification.Builder b = new Notification.Builder(this, CH_SIGNAL);
        b.setSmallIcon(android.R.drawable.stat_notify_more).setContentTitle(title).setContentText(body).setAutoCancel(true).setContentIntent(pi);
        nm.notify(((int) (System.currentTimeMillis() % 100000)) + 2000, b.build());
        this.lastLog = title + " — " + body;
        updateServiceNotification();
    }

    @Override // com.benknight.mwsl.PaperEngine.Listener
    public void onLog(String text) {
        this.lastLog = text;
        updateServiceNotification();
    }

    private void createChannels() {
        NotificationManager nm = (NotificationManager) getSystemService("notification");
        nm.createNotificationChannel(new NotificationChannel(CH_TRACK, "Wallet tracking", 2));
        nm.createNotificationChannel(new NotificationChannel(CH_SIGNAL, "Trade signals", 3));
    }

    private Notification serviceNotification() {
        Intent open = new Intent(this, (Class<?>) MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 1, open, 201326592);
        Intent stop = new Intent(this, (Class<?>) TrackingService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 2, stop, 201326592);
        Notification.Builder b = new Notification.Builder(this, CH_TRACK);
        return b.setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("Meme Wallet Shadow Lab — tracking").setContentText(this.lastLog).setOngoing(true).setContentIntent(openPi).addAction(android.R.drawable.ic_media_pause, "Stop", stopPi).build();
    }

    private void startAsForeground() {
        Notification n = serviceNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1001, n, 1073741824);
        } else {
            startForeground(1001, n);
        }
    }

    private void updateServiceNotification() {
        NotificationManager nm = (NotificationManager) getSystemService("notification");
        nm.notify(1001, serviceNotification());
    }

    @Override // android.app.Service
    public void onDestroy() {
        this.stopping = true;
        Prefs.setHeartbeat(this, 0L);
        if (this.loopExec != null) {
            this.loopExec.shutdownNow();
        }
        if (this.maintenanceExec != null) {
            this.maintenanceExec.shutdownNow();
        }
        super.onDestroy();
    }

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        return null;
    }
}
