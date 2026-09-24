package com.benknight.mwsl;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.View;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;
import com.benknight.mwsl.AuditScanner;
import com.benknight.mwsl.CandidateScanner;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;

public class MainActivity extends Activity {
    private TextView battery;
    private EquityChartView chart;
    private Spinner chartSpinner;
    private EditText cielo;
    private TextView combined;
    private EditText days;
    private Db db;
    private EditText delay;
    private TextView dexText;
    private EditText fee;
    private CheckBox fixed;
    private EditText fixedUsd;
    private EditText helius;
    private EditText maxTx;

    private Network net;
    private EditText poll;
    private EditText pricePoll;
    private TextView recent;
    private EditText rpc;
    private EditText scannerCount;
    private TextView scannerOutput;
    private EditText scannerWallet;
    private EditText sizePct;
    private EditText slip;
    private TextView status;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, TextView> traderViews = new LinkedHashMap();
    private final Runnable refresher = new Runnable() { // from class: com.benknight.mwsl.MainActivity.1
        @Override // java.lang.Runnable
        public void run() {
            MainActivity.this.refresh();
            MainActivity.this.handler.postDelayed(this, 5000L);
        }
    };

    @Override // android.app.Activity
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        this.db = new Db(this);
        this.db.ensureSeeded();
        this.net = new Network(this);
        buildUi();
        requestNotifications();
        this.handler.post(this.refresher);
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        this.handler.removeCallbacks(this.refresher);
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda29
            @Override // android.view.View.OnApplyWindowInsetsListener
            public final WindowInsets onApplyWindowInsets(View view, WindowInsets windowInsets) {
                return MainActivity.lambda$buildUi$0(view, windowInsets);
            }
        });
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(1);
        root.setPadding(dp(16), dp(12), dp(16), dp(36));
        scrollView.addView(root);
        TextView title = heading("Meme Wallet Shadow Lab — Native");
        root.addView(title);
        root.addView(text("Paper-only public-wallet shadow tracker · 5 × $1,000 independent accounts"));
        TextView buildLabel = text("Build: " + versionName());
        buildLabel.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(buildLabel);
        this.status = text("");
        this.status.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(this.status);
        this.battery = text("");
        root.addView(this.battery);
        TextView firstRun = text("First run: each wallet establishes a live baseline. Zero signals immediately after installation is normal. New wallet activity appears automatically; Backfill History loads older activity separately without creating fake historical paper fills.");
        firstRun.setTextSize(13.0f);
        firstRun.setPadding(0, dp(6), 0, dp(10));
        root.addView(firstRun);
        Button start = button("Start 24/7");
        Button stop = button("Stop");
        Button sync = button("Sync now");
        Button backfill = button("Backfill history");
        Button export = button("Export CSV");
        Button test = button("TEST RPC / DNS & SOURCES");
        LinearLayout row1 = row();
        addWeighted(row1, start);
        addWeighted(row1, stop);
        root.addView(row1);
        LinearLayout row2 = row();
        addWeighted(row2, sync);
        addWeighted(row2, backfill);
        root.addView(row2);
        LinearLayout row3 = row();
        addWeighted(row3, test);
        addWeighted(row3, export);
        root.addView(row3);
        Button batt = button("Open battery optimisation settings");
        root.addView(batt);
        root.addView(section("Parser audit & public wallet scanner"));
        TextView scannerHelp = text("READ-ONLY AUDIT. Active copy slots use paper fills; Validation wallets are simulated but not counted as active slots; Research wallets are watched at lower frequency with paper copying disabled. Candidate discovery scores technical trackability only.");
        scannerHelp.setTextSize(13.0f);
        root.addView(scannerHelp);
        this.scannerWallet = input("", false);
        this.scannerWallet.setHint("Public Solana wallet address");
        this.scannerCount = input("20", false);
        this.scannerCount.setInputType(2);
        root.addView(field("Wallet to scan", this.scannerWallet));
        root.addView(field("Recent transactions to scan (1–100)", this.scannerCount));
        Button auditTracked = button("AUDIT TRACKED 5");
        Button scanWallet = button("SCAN PUBLIC WALLET");
        LinearLayout scanRow = row();
        addWeighted(scanRow, auditTracked);
        addWeighted(scanRow, scanWallet);
        root.addView(scanRow);
        Button discover = button("DISCOVER RECENT DEX WALLETS");
        root.addView(discover);
        auditTracked.setOnClickListener(new View.OnClickListener() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda2
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildUi$1(view);
            }
        });
        scanWallet.setOnClickListener(new View.OnClickListener() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda3
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildUi$2(view);
            }
        });
        discover.setOnClickListener(new View.OnClickListener() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda4
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildUi$3(view);
            }
        });
        Button copyResults = button("COPY RESULTS");
        Button shareResults = button("SHARE RESULTS");
        LinearLayout resultRow = row();
        addWeighted(resultRow, copyResults);
        addWeighted(resultRow, shareResults);
        root.addView(resultRow);
        copyResults.setOnClickListener(new View.OnClickListener() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda5
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildUi$4(view);
            }
        });
        shareResults.setOnClickListener(new View.OnClickListener() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda6
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildUi$5(view);
            }
        });
        this.scannerOutput = text("Parser audit ready.");
        this.scannerOutput.setTypeface(Typeface.MONOSPACE);
        this.scannerOutput.setTextSize(11.0f);
        this.scannerOutput.setTextIsSelectable(true);
        root.addView(this.scannerOutput);
        start.setOnClickListener(new View.OnClickListener() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda7
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildUi$6(view);
            }
        });
        stop.setOnClickListener(new View.OnClickListener() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda8
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildUi$7(view);
            }
        });
        sync.setOnClickListener(new View.OnClickListener() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda9
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildUi$8(view);
            }
        });
        backfill.setOnClickListener(new View.OnClickListener() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda10
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildUi$9(view);
            }
        });
        export.setOnClickListener(new View.OnClickListener() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda30
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildUi$10(view);
            }
        });
        test.setOnClickListener(new View.OnClickListener() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda31
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildUi$11(view);
            }
        });
        batt.setOnClickListener(new View.OnClickListener() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda32
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildUi$12(view);
            }
        });
        root.addView(section("Equity curve"));
        this.chartSpinner = new Spinner(this);
        String[] opts = {"Legacy combined (all 5)", "Jijo / JoJo", "Cented", "Kreo", "Yogurt", "Meech"};
        this.chartSpinner.setAdapter((SpinnerAdapter) new ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opts));
        root.addView(this.chartSpinner);
        this.chart = new EquityChartView(this);
        root.addView(this.chart, new LinearLayout.LayoutParams(-1, dp(230)));
        this.chartSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() { // from class: com.benknight.mwsl.MainActivity.2
            @Override // android.widget.AdapterView.OnItemSelectedListener
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                MainActivity.this.refreshChart();
            }

            @Override // android.widget.AdapterView.OnItemSelectedListener
            public void onNothingSelected(AdapterView<?> p) {
            }
        });
        this.combined = text("");
        this.combined.setTypeface(Typeface.MONOSPACE);
        this.combined.setTextSize(15.0f);
        root.addView(this.combined);
        root.addView(section("Trader statistics"));
        TraderDef[] traderDefArr = Config.TRADERS;
        int length = traderDefArr.length;
        int i = 0;
        while (i < length) {
            TraderDef t = traderDefArr[i];
            TraderDef[] traderDefArr2 = traderDefArr;
            int i2 = length;
            TextView h = heading(t.name + " — " + t.role);
            h.setTextSize(19.0f);
            root.addView(h);
            TextView tv = text("");
            tv.setTypeface(Typeface.MONOSPACE);
            tv.setTextSize(13.0f);
            this.traderViews.put(t.id, tv);
            root.addView(tv);
            i++;
            traderDefArr = traderDefArr2;
            length = i2;
            copyResults = copyResults;
        }
        root.addView(section("DEX / router usage"));
        this.dexText = text("");
        root.addView(this.dexText);
        root.addView(section("Recent ledger"));
        this.recent = text("");
        this.recent.setTypeface(Typeface.MONOSPACE);
        this.recent.setTextSize(11.0f);
        root.addView(this.recent);
        root.addView(section("Settings"));
        this.helius = input(Prefs.heliusKey(this), true);
        this.cielo = input(Prefs.cieloKey(this), true);
        this.rpc = input(Prefs.rpcUrl(this), false);
        this.poll = input(String.valueOf(Prefs.pollSec(this)), false);
        this.delay = input(String.valueOf(Prefs.humanDelaySec(this)), false);
        this.pricePoll = input(String.valueOf(Prefs.pricePollSec(this)), false);
        this.sizePct = input(String.valueOf(Prefs.sizePct(this)), false);
        this.fixedUsd = input(String.valueOf(Prefs.fixedUsd(this)), false);
        this.slip = input(String.valueOf(Prefs.slippageBps(this)), false);
        this.fee = input(String.valueOf(Prefs.feeBps(this)), false);
        this.days = input(String.valueOf(Prefs.backfillDays(this)), false);
        this.maxTx = input(String.valueOf(Prefs.maxBackfillTx(this)), false);
        root.addView(field("Helius API key (optional; enhanced transaction cross-check + reliable RPC)", this.helius));
        root.addView(field("Cielo API key (optional; swap feed with transaction USD prices)", this.cielo));
        root.addView(field("Fallback Solana RPC URL", this.rpc));
        root.addView(field("Wallet poll interval (seconds)", this.poll));
        root.addView(field("Human reaction delay (seconds)", this.delay));
        root.addView(field("Open-position price refresh (seconds)", this.pricePoll));
        root.addView(field("Buy size (% of that trader's current equity)", this.sizePct));
        root.addView(field("Fixed USD buy size", this.fixedUsd));
        this.fixed = new CheckBox(this);
        this.fixed.setText("Use fixed USD sizing instead of % equity");
        this.fixed.setChecked(Prefs.fixedSize(this));
        root.addView(this.fixed);
        root.addView(field("Simulated slippage (basis points)", this.slip));
        root.addView(field("Base simulated fee (basis points)", this.fee));
        root.addView(field("Historical backfill window (days)", this.days));
        root.addView(field("Maximum backfill transactions per trader", this.maxTx));
        Button save = button("Save settings");
        root.addView(save);
        save.setOnClickListener(new View.OnClickListener() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$buildUi$13(view);
            }
        });
        TextView note = text("The foreground service and database survive normal app closing. If Android/OEM battery management stops the service, reopening the app resumes from the last saved signature and deduplicates already-seen transactions.");
        note.setPadding(0, dp(10), 0, 0);
        root.addView(note);
        setContentView(scrollView);
    }

    static /* synthetic */ WindowInsets lambda$buildUi$0(View v, WindowInsets insets) {
        v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
        return insets;
    }

    public /* synthetic */ void lambda$buildUi$1(View v) {
        runTrackedAudit();
    }

    public /* synthetic */ void lambda$buildUi$2(View v) {
        runWalletScan();
    }

    public /* synthetic */ void lambda$buildUi$3(View v) {
        runCandidateDiscovery();
    }

    public /* synthetic */ void lambda$buildUi$4(View v) {
        copyScannerResults();
    }

    public /* synthetic */ void lambda$buildUi$5(View v) {
        shareScannerResults();
    }

    public /* synthetic */ void lambda$buildUi$6(View v) {
        service("com.benknight.mwsl.START");
    }

    public /* synthetic */ void lambda$buildUi$7(View v) {
        service("com.benknight.mwsl.STOP");
    }

    public /* synthetic */ void lambda$buildUi$8(View v) {
        service("com.benknight.mwsl.SYNC");
    }

    public /* synthetic */ void lambda$buildUi$9(View v) {
        service("com.benknight.mwsl.BACKFILL");
        toast("Backfill started. Progress appears in the tracking notification.");
    }

    public /* synthetic */ void lambda$buildUi$10(View v) {
        exportCsv();
    }

    public /* synthetic */ void lambda$buildUi$11(View v) {
        testSources();
    }

    public /* synthetic */ void lambda$buildUi$12(View v) {
        openBatterySettings();
    }

    public /* synthetic */ void lambda$buildUi$13(View v) {
        saveSettings();
    }

    public void refresh() {
        long now;
        String heartbeat;
        boolean ignored;
        int i;
        String feed;
        long now2 = System.currentTimeMillis();
        boolean wanted = Prefs.trackingEnabled(this);
        long hb = Prefs.lastHeartbeat(this);
        long staleAfter = Math.max(45000L, ((long) Prefs.pollSec(this)) * 4000L);
        boolean active = wanted && hb > 0 && now2 - hb < staleAfter;
        int baselined = 0;
        int errors = 0;
        TraderDef[] traderDefArr = Config.TRADERS;
        int length = traderDefArr.length;
        int i2 = 0;
        while (i2 < length) {
            long staleAfter2 = staleAfter;
            TraderState tr = this.db.trader(traderDefArr[i2].id);
            if (tr != null && tr.lastSignature != null && !tr.lastSignature.isEmpty()) {
                baselined++;
            }
            if (tr != null && tr.lastError != null && !tr.lastError.isEmpty()) {
                errors++;
            }
            i2++;
            staleAfter = staleAfter2;
        }
        String serviceState = !wanted ? "OFF" : active ? "ACTIVE" : "STARTING / STALE";
        String heartbeat2 = hb <= 0 ? "none" : age(now2 - hb) + " ago";
        this.status.setText("Service: " + serviceState + " · heartbeat " + heartbeat2 + "\nWallet feeds: " + baselined + "/5 baselined" + (errors > 0 ? " · " + errors + " reporting errors" : " · no stored RPC errors") + "\nPoll " + Prefs.pollSec(this) + "s · human delay " + Prefs.humanDelaySec(this) + "s · slippage " + Prefs.slippageBps(this) + " bps · fee floor " + Prefs.feeBps(this) + " bps");
        PowerManager pm = (PowerManager) getSystemService("power");
        boolean ignored2 = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        this.battery.setText("Battery optimisation: " + (ignored2 ? "exemption active" : "may restrict 24/7 tracking — set this app to unrestricted/background use"));
        double sum = 0.0d;
        double activeSum = 0.0d;
        double validationSum = 0.0d;
        TraderDef[] traderDefArr2 = Config.TRADERS;
        int length2 = traderDefArr2.length;
        int i3 = 0;
        while (i3 < length2) {
            long hb2 = hb;
            TraderDef t = traderDefArr2[i3];
            TraderDef[] traderDefArr3 = traderDefArr2;
            TraderStats s = this.db.stats(t.id);
            String serviceState2 = serviceState;
            TraderState tr2 = this.db.trader(t.id);
            String heartbeat3 = heartbeat2;
            PowerManager pm2 = pm;
            sum += s.equity;
            if (t.activeSlot()) {
                activeSum += s.equity;
            }
            if ("VALIDATION".equals(t.role)) {
                validationSum += s.equity;
            }
            TextView tv = this.traderViews.get(t.id);
            if (tv == null) {
                now = now2;
                heartbeat = heartbeat3;
                ignored = ignored2;
                i = length2;
            } else {
                if (tr2 == null) {
                    feed = "database row missing";
                    heartbeat = heartbeat3;
                    ignored = ignored2;
                    i = length2;
                } else {
                    String feed2 = tr2.lastError;
                    if (feed2 != null && !tr2.lastError.isEmpty()) {
                        heartbeat = heartbeat3;
                        feed = "ERROR — " + trimErr(tr2.lastError);
                        ignored = ignored2;
                        i = length2;
                    } else {
                        heartbeat = heartbeat3;
                        ignored = ignored2;
                        if (tr2.lastSync > 0) {
                            i = length2;
                            feed = "synced " + age(now2 - tr2.lastSync) + " ago";
                        } else {
                            i = length2;
                            feed = "waiting for first RPC sync";
                        }
                    }
                }
                String baseline = (tr2 == null || tr2.lastSignature == null || tr2.lastSignature.isEmpty()) ? "no" : "yes";
                now = now2;
                tv.setText("Role: " + t.role + " · paper copy " + (t.paperEnabled ? "ENABLED" : "DISABLED") + "\n" + t.purpose + "\n" + statsText(s) + "\nSkip reasons: " + this.db.skipBreakdown(t.id) + "\nFeed: " + feed + " · baseline " + baseline + "\nWallet: " + Db.shortAddr(t.wallet) + "\nVenue: " + this.db.venueBreakdown(t.id));
            }
            i3++;
            ignored2 = ignored;
            hb = hb2;
            traderDefArr2 = traderDefArr3;
            serviceState = serviceState2;
            pm = pm2;
            heartbeat2 = heartbeat;
            length2 = i;
            now2 = now;
        }
        int activeSlots = Config.activeSlotsUsed();
        double activeBase = Math.max(1, activeSlots) * 1000.0d;
        this.combined.setText(String.format(Locale.US, "Active copy slots: %d/5 · open replacements: %d\nActive copy equity: $%.2f / $%.2f   ROI: %+.2f%%\nValidation: %d wallet(s) · equity $%.2f\nResearch watchlist: %d wallet(s) · paper copy disabled\nLegacy all-wallet equity: $%.2f", Integer.valueOf(activeSlots), Integer.valueOf(Math.max(0, 5 - activeSlots)), Double.valueOf(activeSum), Double.valueOf(activeBase), Double.valueOf(((activeSum / activeBase) - 1.0d) * 100.0d), Integer.valueOf(Config.validationCount()), Double.valueOf(validationSum), Integer.valueOf(Config.researchCount()), Double.valueOf(sum)));
        StringBuilder dx = new StringBuilder();
        for (TraderDef t2 : Config.TRADERS) {
            dx.append(t2.name).append(": ").append(this.db.venueBreakdown(t2.id)).append('\n');
        }
        this.dexText.setText(dx.toString());
        String ledger = this.db.recentRecordsText(60);
        this.recent.setText((ledger == null || ledger.trim().isEmpty()) ? "No records yet. Wait for a new tracked-wallet trade or run Backfill History." : ledger);
        refreshChart();
    }

    private String statsText(TraderStats s) {
        String pf = Double.isInfinite(s.profitFactor) ? "∞" : String.format(Locale.US, "%.2f", Double.valueOf(s.profitFactor));
        return String.format(Locale.US, "Equity $%.2f  ROI %+.2f%%\nMax DD %.2f%%  Current DD %.2f%%  DD duration %s\nClosed %d  Open %d  Win %.1f%%  PF %s  Expect $%.2f\nAvg win $%.2f  Avg loss $%.2f  Hold avg %s / med %s\nSignals %d  Paper fills %d  Skipped %d  Max loss streak %d\nDetection %.1fs  Human delay %.1fs  Avg fill drift %+.3f%%", Double.valueOf(s.equity), Double.valueOf(s.roiPct), Double.valueOf(s.maxDdPct), Double.valueOf(s.currentDdPct), duration(s.maxDdDurationMs), Long.valueOf(s.closedTrades), Long.valueOf(s.openPositions), Double.valueOf(s.winRatePct), pf, Double.valueOf(s.expectancy), Double.valueOf(s.avgWin), Double.valueOf(s.avgLoss), duration((long) (s.avgHoldMin * 60000.0d)), duration((long) (s.medianHoldMin * 60000.0d)), Long.valueOf(s.signals), Long.valueOf(s.paperExecs), Long.valueOf(s.skipped), Long.valueOf(s.maxLossStreak), Double.valueOf(s.avgDetectionLagSec), Double.valueOf(s.avgHumanDelaySec), Double.valueOf(s.avgDriftPct));
    }

    public void refreshChart() {
        String id;
        String label;
        if (this.chart == null || this.chartSpinner == null) {
            return;
        }
        int p = this.chartSpinner.getSelectedItemPosition();
        if (p <= 0) {
            id = "combined";
            label = "Legacy combined";
        } else {
            id = Config.TRADERS[p - 1].id;
            label = Config.TRADERS[p - 1].name;
        }
        List<Snapshot> xs = this.db.snapshots(id, 1500);
        this.chart.setSeries(label, xs);
    }

    private void runTrackedAudit() {
        try {
            int requested = Math.max(1, Math.min(100, Integer.parseInt(this.scannerCount.getText().toString().trim())));
            final int perWallet = Math.min(20, requested);
            this.scannerOutput.setText("Auditing five tracked wallets — searching for up to " + perWallet + " actual trade signals each. Failed and non-trade transactions are skipped while the audit scans deeper…");
            new Thread(new Runnable() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda16
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$runTrackedAudit$18(perWallet);
                }
            }).start();
        } catch (Exception e) {
            toast("Enter a transaction count from 1 to 100.");
        }
    }

    public /* synthetic */ void lambda$runTrackedAudit$18(int perWallet) {
        final StringBuilder all = new StringBuilder();
        for (final TraderDef t : Config.TRADERS) {
            runOnUiThread(new Runnable() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda26
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$runTrackedAudit$14(t, all);
                }
            });
            String report = AuditScanner.scan(this.net, this.db, t.name, t.wallet, perWallet, t.id, Prefs.slippageBps(this), Prefs.feeBps(this), new AuditScanner.Progress() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda27
                @Override // com.benknight.mwsl.AuditScanner.Progress
                public final void onProgress(String str) {
                    MainActivity.this.lambda$runTrackedAudit$16(all, str);
                }
            });
            all.append("========== ").append(t.name).append(" ==========\n").append(report).append("\n\n");
            try {
                Thread.sleep(2500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        runOnUiThread(new Runnable() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda28
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$runTrackedAudit$17(all);
            }
        });
    }

    public /* synthetic */ void lambda$runTrackedAudit$14(TraderDef t, StringBuilder all) {
        this.scannerOutput.setText("Scanning " + t.name + "…\n\n" + ((Object) all));
    }

    public /* synthetic */ void lambda$runTrackedAudit$15(String msg, StringBuilder all) {
        this.scannerOutput.setText(msg + "\n\n" + ((Object) all));
    }

    public /* synthetic */ void lambda$runTrackedAudit$16(final StringBuilder all, final String msg) {
        runOnUiThread(new Runnable() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda22
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$runTrackedAudit$15(msg, all);
            }
        });
    }

    public /* synthetic */ void lambda$runTrackedAudit$17(StringBuilder all) {
        this.scannerOutput.setText(all.toString());
    }

    private void runWalletScan() {
        final String wallet = this.scannerWallet.getText().toString().trim();
        if (wallet.length() < 32 || wallet.length() > 50) {
            toast("Enter a valid-looking public Solana wallet address.");
            return;
        }
        try {
            final int requested = Math.max(1, Math.min(100, Integer.parseInt(this.scannerCount.getText().toString().trim())));
            this.scannerOutput.setText("Scanning " + Db.shortAddr(wallet) + "…");
            new Thread(new Runnable() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda15
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$runWalletScan$22(wallet, requested);
                }
            }).start();
        } catch (Exception e) {
            toast("Enter a transaction count from 1 to 100.");
        }
    }

    public /* synthetic */ void lambda$runWalletScan$22(String wallet, int requested) {
        final String report = AuditScanner.scan(this.net, this.db, "Public wallet", wallet, requested, null, Prefs.slippageBps(this), Prefs.feeBps(this), new AuditScanner.Progress() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda17
            @Override // com.benknight.mwsl.AuditScanner.Progress
            public final void onProgress(String str) {
                MainActivity.this.lambda$runWalletScan$20(str);
            }
        });
        runOnUiThread(new Runnable() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda18
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$runWalletScan$21(report);
            }
        });
    }

    public /* synthetic */ void lambda$runWalletScan$19(String msg) {
        this.scannerOutput.setText(msg);
    }

    public /* synthetic */ void lambda$runWalletScan$20(final String msg) {
        runOnUiThread(new Runnable() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda12
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$runWalletScan$19(msg);
            }
        });
    }

    public /* synthetic */ void lambda$runWalletScan$21(String report) {
        this.scannerOutput.setText(report);
    }

    private void runCandidateDiscovery() {
        int requested;
        try {
            requested = Math.max(1, Math.min(10, Integer.parseInt(this.scannerCount.getText().toString().trim())));
        } catch (Exception e) {
            requested = 3;
        }
        final int perProgram = Math.min(5, requested);
        this.scannerOutput.setText("Discovering recent signer wallets across known DEX programs…\nThis is activity discovery only, not a profitability ranking.");
        new Thread(new Runnable() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda14
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$runCandidateDiscovery$26(perProgram);
            }
        }).start();
    }

    public /* synthetic */ void lambda$runCandidateDiscovery$26(int perProgram) {
        final String report = CandidateScanner.discover(this.net, perProgram, new CandidateScanner.Progress() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda19
            @Override // com.benknight.mwsl.CandidateScanner.Progress
            public final void onProgress(String str) {
                MainActivity.this.lambda$runCandidateDiscovery$24(str);
            }
        });
        runOnUiThread(new Runnable() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda20
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$runCandidateDiscovery$25(report);
            }
        });
    }

    public /* synthetic */ void lambda$runCandidateDiscovery$23(String msg) {
        this.scannerOutput.setText(msg);
    }

    public /* synthetic */ void lambda$runCandidateDiscovery$24(final String msg) {
        runOnUiThread(new Runnable() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$runCandidateDiscovery$23(msg);
            }
        });
    }

    public /* synthetic */ void lambda$runCandidateDiscovery$25(String report) {
        this.scannerOutput.setText(report);
    }

    private void copyScannerResults() {
        String txt = this.scannerOutput == null ? "" : this.scannerOutput.getText().toString();
        if (txt.trim().isEmpty() || "Parser audit ready.".equals(txt.trim())) {
            toast("Run an audit or scan first.");
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService("clipboard");
        if (cm == null) {
            toast("Clipboard unavailable.");
        } else {
            cm.setPrimaryClip(ClipData.newPlainText("Meme Wallet Shadow Lab results", txt));
            toast("Results copied to clipboard.");
        }
    }

    private void shareScannerResults() {
        String txt = this.scannerOutput == null ? "" : this.scannerOutput.getText().toString();
        if (txt.trim().isEmpty() || "Parser audit ready.".equals(txt.trim())) {
            toast("Run an audit or scan first.");
            return;
        }
        Intent send = new Intent("android.intent.action.SEND");
        send.setType("text/plain");
        send.putExtra("android.intent.extra.SUBJECT", "Meme Wallet Shadow Lab results");
        send.putExtra("android.intent.extra.TEXT", txt);
        startActivity(Intent.createChooser(send, "Share audit results"));
    }

    private void saveSettings() {
        try {
            Prefs.save(this, this.helius.getText().toString(), this.cielo.getText().toString(), this.rpc.getText().toString(), ival(this.poll), ival(this.delay), ival(this.pricePoll), dval(this.sizePct), dval(this.fixedUsd), this.fixed.isChecked(), ival(this.slip), ival(this.fee), ival(this.days), ival(this.maxTx));
            toast("Settings saved. The running service will use the updated values.");
            refresh();
        } catch (Exception e) {
            toast("Check the numeric settings: " + e.getMessage());
        }
    }

    private void service(String action) {
        Intent i = new Intent(this, (Class<?>) TrackingService.class).setAction(action);
        if ("com.benknight.mwsl.STOP".equals(action)) {
            startService(i);
        } else {
            startForegroundService(i);
        }
        Prefs.setTrackingEnabled(this, !"com.benknight.mwsl.STOP".equals(action));
        this.handler.postDelayed(new Runnable() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda11
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.refresh();
            }
        }, 700L);
    }

    private void exportCsv() {
        new Thread(new Runnable() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda13
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$exportCsv$29();
            }
        }).start();
    }

    public /* synthetic */ void lambda$exportCsv$29() {
        try {
            final String n = CsvExporter.export(this, this.db);
            runOnUiThread(new Runnable() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda24
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$exportCsv$27(n);
                }
            });
        } catch (Exception e) {
            runOnUiThread(new Runnable() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda25
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$exportCsv$28(e);
                }
            });
        }
    }

    public /* synthetic */ void lambda$exportCsv$27(String n) {
        toast("Saved to Downloads/MemeWalletShadowLab/" + n);
    }

    public /* synthetic */ void lambda$exportCsv$28(Exception e) {
        toast("Export failed: " + e.getMessage());
    }

    private void testSources() {
        toast("Testing RPCs, five wallet feeds and market pricing…");
        new Thread(new Runnable() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda23
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$testSources$31();
            }
        }).start();
    }

    public /* synthetic */ void lambda$testSources$31() {
        StringBuilder append;
        String trimErr;
        StringBuilder b = new StringBuilder();
        boolean any = false;
        Iterator<String> it = this.net.rpcCandidates().iterator();
        while (true) {
            String str = "✓ ";
            if (!it.hasNext()) {
                break;
            }
            String ep = it.next();
            try {
                boolean ok = this.net.health(ep);
                if (!ok) {
                    str = "✗ ";
                }
                b.append(str).append(redact(ep)).append('\n');
                if (ok) {
                    any = true;
                }
            } catch (Exception e) {
                b.append("✗ ").append(redact(ep)).append(" — ").append(trimErr(e.getMessage())).append('\n');
            }
        }
        b.append("\nWallet heads:\n");
        if (any) {
            for (TraderDef t : Config.TRADERS) {
                try {
                    JSONArray a = this.net.signatures(t.wallet, null, null, 1);
                    b.append(a.length() > 0 ? "✓ " : "! ").append(t.name).append(a.length() > 0 ? " — reachable" : " — no signatures returned").append('\n');
                } catch (Exception e2) {
                    b.append("✗ ").append(t.name).append(" — ").append(trimErr(e2.getMessage())).append('\n');
                }
            }
        } else {
            b.append("RPC unavailable; wallet test skipped\n");
        }
        Market m = this.net.tokenMarket("So11111111111111111111111111111111111111112", true);
        b.append("\n");
        if (Double.isFinite(m.priceUsd)) {
            append = new StringBuilder().append("✓ DexScreener SOL $");
            trimErr = String.format(Locale.US, "%.2f", Double.valueOf(m.priceUsd));
        } else {
            append = new StringBuilder().append("✗ DexScreener — ");
            trimErr = trimErr(m.error);
        }
        b.append(append.append(trimErr).toString());
        if (!Prefs.heliusKey(this).isEmpty()) {
            try {
                JSONArray h = this.net.heliusEnhanced(Config.TRADERS[0].wallet, 1);
                b.append("\n").append(h.length() > 0 ? "✓ Helius enhanced" : "! Helius enhanced returned no rows");
            } catch (Exception e3) {
                b.append("\n✗ Helius enhanced — ").append(trimErr(e3.getMessage()));
            }
        }
        if (!Prefs.cieloKey(this).isEmpty()) {
            try {
                JSONArray ci = this.net.cieloSwaps(Config.TRADERS[0].wallet, 1);
                b.append("\n").append("✓ Cielo feed reachable · ").append(ci.length()).append(" swap row(s) in sample");
            } catch (Exception e4) {
                b.append("\n✗ Cielo feed — ").append(trimErr(e4.getMessage()));
            }
        }
        final String msg = b.toString();
        runOnUiThread(new Runnable() { // from class: com.benknight.mwsl.MainActivity$$ExternalSyntheticLambda21
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$testSources$30(msg);
            }
        });
    }

    public /* synthetic */ void lambda$testSources$30(String msg) {
        new AlertDialog.Builder(this).setTitle("Data source diagnostics").setMessage(msg).setPositiveButton("OK", (DialogInterface.OnClickListener) null).show();
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String redact(String s) {
        return s.replaceAll("([?&]api-key=)[^&]+", "$1••••••");
    }

    private void openBatterySettings() {
        try {
            startActivity(new Intent("android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"));
        } catch (Exception e) {
            startActivity(new Intent("android.settings.APPLICATION_DETAILS_SETTINGS", Uri.parse("package:" + getPackageName())));
        }
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 90);
        }
    }

    private TextView heading(String s) {
        TextView v = text(s);
        v.setTextSize(25.0f);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, dp(8), 0, dp(6));
        return v;
    }

    private TextView section(String s) {
        TextView v = heading(s);
        v.setTextSize(21.0f);
        v.setPadding(0, dp(24), 0, dp(8));
        return v;
    }

    private TextView text(String s) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(14.0f);
        v.setTextColor(-14540254);
        v.setPadding(0, dp(3), 0, dp(6));
        return v;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(0);
        return r;
    }

    private void addWeighted(LinearLayout row, View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        row.addView(v, lp);
    }

    private LinearLayout field(String label, EditText e) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(1);
        TextView l = text(label);
        l.setTextSize(12.0f);
        l.setTypeface(Typeface.DEFAULT_BOLD);
        l.setPadding(0, dp(8), 0, 0);
        box.addView(l);
        box.addView(e);
        return box;
    }

    private EditText input(String value, boolean password) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setSingleLine(true);
        e.setTextSize(14.0f);
        e.setInputType(password ? 129 : 1);
        return e;
    }

    private int dp(int n) {
        return (int) ((n * getResources().getDisplayMetrics().density) + 0.5f);
    }

    private void toast(String s) {
        Toast.makeText(this, s, 1).show();
    }

    private int ival(EditText e) {
        return Integer.parseInt(e.getText().toString().trim());
    }

    private double dval(EditText e) {
        return Double.parseDouble(e.getText().toString().trim());
    }

    private String duration(long ms) {
        if (ms <= 0) {
            return "0m";
        }
        double m = ms / 60000.0d;
        return m < 60.0d ? String.format(Locale.US, "%.0fm", Double.valueOf(m)) : m < 1440.0d ? String.format(Locale.US, "%.1fh", Double.valueOf(m / 60.0d)) : String.format(Locale.US, "%.1fd", Double.valueOf(m / 1440.0d));
    }

    private String age(long ms) {
        if (ms < 0) {
            return "now";
        }
        if (ms < 60000) {
            return Math.max(0L, ms / 1000) + "s";
        }
        return ms < 3600000 ? (ms / 60000) + "m" : ms < 86400000 ? String.format(Locale.US, "%.1fh", Double.valueOf(ms / 3600000.0d)) : String.format(Locale.US, "%.1fd", Double.valueOf(ms / 8.64E7d));
    }

    private String trimErr(String s) {
        if (s == null) {
            return "unknown";
        }
        String s2 = s.replace('\n', ' ');
        return s2.length() > 90 ? s2.substring(0, 87) + "…" : s2;
    }
}
