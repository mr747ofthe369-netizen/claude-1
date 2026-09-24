package com.memetaillab.beta1;

import android.R;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import com.memetaillab.beta1.FastStream;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TrackerService extends Service implements FastStream.Listener {
    static final String ACTION_PROTECT = "com.memetaillab.beta1.PROTECT";
    static final String ACTION_START = "com.memetaillab.beta1.START";
    static final String ACTION_STOP = "com.memetaillab.beta1.STOP";
    static final String ACTION_STREAM_RESTART = "com.memetaillab.beta1.STREAM_RESTART";
    static final String ACTION_SYNC = "com.memetaillab.beta1.SYNC";
    private static final String CH = "mtl_beta1";
    private static final int NID = 3691;
    private Db db;
    private PaperEngine engine;
    private FastStream fastStream;
    private LiveTrader liveTrader;
    private ScheduledExecutorService markEx;

    private Network net;
    private ScheduledExecutorService scanEx;
    private final ConcurrentLinkedQueue<String[]> fastEvents = new ConcurrentLinkedQueue<>();
    private volatile long lastMark = 0;
    private volatile long lastRecovery = 0;
    private volatile long lastEval = 0;
    private volatile boolean markBusy = false;
    private volatile boolean scanBusy = false;
    private volatile boolean forceScan = false;
    private volatile int lastSignals = 0;
    private volatile int lastFills = 0;
    private volatile int lastExits = 0;
    private volatile int lastDiscovered = 0;
    private volatile String lastFastBook = "";

    @Override // android.app.Service
    public void onCreate() {
        super.onCreate();
        this.db = new Db(this);
        this.net = new Network(this);
        this.engine = new PaperEngine(this, this.db, this.net);
        this.liveTrader = new LiveTrader(this, this.db, this.net);
        this.fastStream = new FastStream(this, this);
        channel();
        this.markEx = Executors.newSingleThreadScheduledExecutor();
        this.scanEx = Executors.newSingleThreadScheduledExecutor();
        this.markEx.scheduleWithFixedDelay(new Runnable() { // from class: com.memetaillab.beta1.TrackerService$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                TrackerService.this.markTick();
            }
        }, 0L, 5L, TimeUnit.SECONDS);
        this.scanEx.scheduleWithFixedDelay(new Runnable() { // from class: com.memetaillab.beta1.TrackerService$$ExternalSyntheticLambda1
            @Override // java.lang.Runnable
            public final void run() {
                TrackerService.this.scanTick();
            }
        }, 0L, 1L, TimeUnit.SECONDS);
    }

    @Override // android.app.Service
    public int onStartCommand(Intent i, int flags, int startId) {
        String a;
        if (i == null) {
            a = Prefs.protectionOnly(this) ? ACTION_PROTECT : ACTION_START;
        } else {
            a = i.getAction();
        }
        if (ACTION_STOP.equals(a)) {
            Prefs.setLiveArmed(this, false);
            Prefs.setTracking(this, false);
            if (this.fastStream != null) {
                this.fastStream.stop();
            }
            if (this.db != null && !this.db.openLivePositions().isEmpty()) {
                Prefs.setProtectionOnly(this, true);
                startForeground(NID, note("Entries stopped · protecting existing LIVE exits"));
                return 1;
            }
            Prefs.setProtectionOnly(this, false);
            stopForeground(true);
            stopSelf();
            return 2;
        }
        if (ACTION_PROTECT.equals(a)) {
            Prefs.setLiveArmed(this, false);
            Prefs.setTracking(this, false);
            Prefs.setProtectionOnly(this, true);
            if (this.fastStream != null) {
                this.fastStream.stop();
            }
            startForeground(NID, note("Protection-only · managing existing LIVE exits"));
            return 1;
        }
        Prefs.setProtectionOnly(this, false);
        Prefs.setTracking(this, true);
        Prefs.ensureFirstStart(this);
        if (ACTION_SYNC.equals(a)) {
            this.forceScan = true;
        }
        if (ACTION_STREAM_RESTART.equals(a) && this.fastStream != null) {
            this.fastStream.restart();
        }
        if (Prefs.fastStream(this) && this.fastStream != null) {
            this.fastStream.start();
        } else if (this.fastStream != null) {
            this.fastStream.stop();
        }
        startForeground(NID, note("Starting…"));
        return 1;
    }

    @Override // com.memetaillab.beta1.FastStream.Listener
    public void onFastEvent(String book, String signature) {
        this.lastFastBook = book;
        if (signature != null && !signature.isEmpty()) {
            this.fastEvents.offer(new String[]{book, signature});
        }
        update("Helius event " + book + " · resolving pool");
    }

    @Override // com.memetaillab.beta1.FastStream.Listener
    public void onState(String state) {
        update("Stream " + state);
    }

    public void markTick() {
        if (this.markBusy) {
            return;
        }
        boolean tracking = Prefs.tracking(this);
        boolean protecting = (this.db == null || this.db.openLivePositions().isEmpty()) ? false : true;
        if (!tracking && !protecting) {
            return;
        }
        this.markBusy = true;
        try {
            long now = System.currentTimeMillis();
            if (tracking) {
                int fills = this.engine.processPending();
                int liveFills = this.liveTrader.processPending();
                if (fills + liveFills > 0) {
                    this.lastFills = fills + liveFills;
                }
            }
            boolean paperOpen = tracking && !this.db.openPositions().isEmpty();
            boolean liveOpen = !this.db.openLivePositions().isEmpty();
            if (paperOpen || liveOpen || (tracking && now - this.lastMark >= ((long) Prefs.markSec(this)) * 1000)) {
                int exits = tracking ? this.engine.markOpen() : 0;
                int liveExits = liveOpen ? this.liveTrader.markOpen() : 0;
                this.lastMark = System.currentTimeMillis();
                Prefs.mark(this);
                if (exits + liveExits > 0) {
                    this.lastExits = exits + liveExits;
                }
            }
            Prefs.beat(this);
            if (!tracking && this.db.openLivePositions().isEmpty()) {
                Prefs.setProtectionOnly(this, false);
                stopForeground(true);
                stopSelf();
                return;
            }
            update(tracking ? "Live · " + this.lastSignals + " signals · " + this.lastFills + " fills · " + this.lastExits + " exits" : "Entries stopped · protecting " + this.db.openLivePositions().size() + " LIVE position(s)");
        } catch (Exception e) {
            Prefs.error(this, "Mark/fill: " + Network.shortErr(e));
            Prefs.beat(this);
            update("Mark/fill error · " + Network.shortErr(e));
        } finally {
            this.markBusy = false;
        }
    }

    public void scanTick() {
        if (!Prefs.tracking(this) || this.scanBusy) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean hasEvent = !this.fastEvents.isEmpty();
        boolean evalDue = now - this.lastEval >= 10000;
        boolean recoveryDue = this.forceScan || now - this.lastRecovery >= ((long) Prefs.scanSec(this)) * 1000;
        if (!hasEvent && !evalDue && !recoveryDue) {
            return;
        }
        this.scanBusy = true;
        int discovered = 0;
        try {
            for (int n = 0; n < 2; n++) {
                String[] e = this.fastEvents.poll();
                if (e == null) {
                    break;
                }
                discovered += this.engine.ingestFastEvent(e[0], e[1]);
            }
            if (discovered > 0) {
                this.lastDiscovered = discovered;
            }
            if (recoveryDue) {
                if (!Prefs.helius(this).trim().isEmpty()) {
                    discovered += this.engine.recoverRecent();
                } else {
                    discovered += this.engine.scanNewPools(false);
                }
                this.lastRecovery = System.currentTimeMillis();
                this.forceScan = false;
            }
            if (evalDue || discovered > 0) {
                this.lastSignals = this.engine.evaluateWatches();
                this.lastEval = System.currentTimeMillis();
            }
            String issue = Prefs.lastError(this);
            update("Helius→DEX · " + discovered + " pools · " + this.lastSignals + " qualified · " + (issue.isEmpty() ? "healthy" : issue));
        } catch (Exception e2) {
            Prefs.error(this, "Discovery/eval: " + Network.shortErr(e2));
            this.lastRecovery = System.currentTimeMillis();
            this.lastEval = System.currentTimeMillis();
            this.forceScan = false;
            update("Discovery/eval error · " + Network.shortErr(e2));
        } finally {
            this.scanBusy = false;
        }
    }

    private void update(String s) {
        NotificationManager n = (NotificationManager) getSystemService("notification");
        if (n != null) {
            n.notify(NID, note(s));
        }
    }

    private Notification note(String line) {
        double eq = 0.0d;
        int open = 0;
        for (String b : Config.BOOKS) {
            BookStats s = this.db == null ? null : this.db.stats(b);
            if (s != null) {
                eq += s.equity;
                open += s.open;
            }
        }
        Intent in = new Intent(this, (Class<?>) MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, in, 201326592);
        return new Notification.Builder(this, CH).setSmallIcon(R.drawable.stat_notify_sync).setContentTitle(Prefs.liveArmed(this) ? "Meme Tail Lab · LIVE ARMED" : "Meme Tail Lab Beta 3").setContentText(line + " · equity $" + String.format(Locale.US, "%.2f", Double.valueOf(eq)) + " · open " + open).setContentIntent(pi).setOngoing(true).build();
    }

    private void channel() {
        NotificationChannel c = new NotificationChannel(CH, "Meme Tail live tracker", 2);
        c.setDescription("Helius event discovery plus DEX Screener market data");
        NotificationManager n = (NotificationManager) getSystemService(NotificationManager.class);
        if (n != null) {
            n.createNotificationChannel(c);
        }
    }

    @Override // android.app.Service
    public void onDestroy() {
        if (this.fastStream != null) {
            this.fastStream.stop();
        }
        if (this.markEx != null) {
            this.markEx.shutdownNow();
        }
        if (this.scanEx != null) {
            this.scanEx.shutdownNow();
        }
        super.onDestroy();
    }

    @Override // android.app.Service
    public IBinder onBind(Intent i) {
        return null;
    }
}
