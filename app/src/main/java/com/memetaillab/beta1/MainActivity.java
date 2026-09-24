package com.memetaillab.beta1;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.memetaillab.beta1.ApiBudget;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private ApiBudget apiBudget;
    private TextView apiResult;
    private SecureBotWallet botWallet;
    private TextView budgetStatus;
    private Switch coinGeckoEnabled;
    private EditText coinGeckoKey;
    private Switch coinGeckoPro;
    private FrameLayout content;
    private Db db;
    private LinearLayout delayModeRow;
    private String delayModeValue;
    private EditText delaySec;
    private EditText entryBps;
    private EditText exAmount;
    private EditText exInput;
    private EditText exOutput;
    private EditText exSlip;
    private ExecutionModule exec;
    private TextView execResult;
    private EditText exitBps;
    private Switch fastStreamToggle;
    private EditText fixedFee;
    private EditText helius;
    private EquityChartView homeChart;
    private TextView homeEquity;
    private TextView homeMark;
    private TextView homeOpen;
    private TextView homePnl;
    private TextView homeRoi;
    private TextView homeStatus;
    private TextView homeVenueSummary;
    private EditText liveDailyLoss;
    private EditText liveMaxExposure;
    private EditText liveMaxImpact;
    private EditText liveMaxTrade;
    private EditText livePfAlloc;
    private EditText livePriorityLamports;
    private EditText livePsAlloc;
    private EditText liveRayAlloc;
    private TextView liveRiskStatus;
    private EditText liveSize;
    private EditText liveSlippage;
    private LiveTrader liveTrader;
    private TextView liveWalletStatus;
    private EditText markSec;
    private EditText maxAge;
    private EditText maxOpen;
    private EditText minAge;
    private EditText minBuyers;
    private EditText minLiq;
    private EditText minMomentum;
    private EditText minRatio;
    private EditText minTrades;

    private Network net;
    private PaperEngine paperEngine;
    private String pfModeValue;
    private EquityChartView portfolioChart;
    private TextView portfolioHeadline;
    private LinearLayout portfolioRecent;
    private LinearLayout portfolioStats;
    private LinearLayout positionBox;
    private String psModeValue;
    private EditText rayApi;
    private String rayModeValue;
    private EditText raySwap;
    private EditText rpc;
    private EditText scanSec;
    private TextView settingsRuntime;
    private LinearLayout signalList;
    private TextView signalStatus;
    private LinearLayout signalSummary;
    private EditText signedTx;
    private EditText sizePct;
    private EditText swapApi;
    private LinearLayout tradeVenueBox;
    private EditText wallet;
    private static final int BG = Color.rgb(5, 10, 8);
    private static final int SURFACE = Color.rgb(12, 20, 17);
    private static final int SURFACE_2 = Color.rgb(17, 28, 23);
    private static final int BORDER = Color.rgb(34, 58, 48);
    private static final int GREEN = Color.rgb(46, 242, 161);
    private static final int GREEN_DARK = Color.rgb(9, 56, 38);
    private static final int TEXT = Color.rgb(241, 247, 244);
    private static final int MUTED = Color.rgb(145, 162, 154);
    private static final int RED = Color.rgb(255, 94, 111);
    private static final int AMBER = Color.rgb(255, 190, 74);
    private final Handler h = new Handler(Looper.getMainLooper());
    private LinearLayout[] pages = new LinearLayout[5];
    private TextView[] nav = new TextView[5];
    private int pageIndex = 0;
    private final HashMap<String, EditText[]> budgetInputs = new HashMap<>();
    private final Runnable refresher = new Runnable() { // from class: com.memetaillab.beta1.MainActivity.1
        @Override // java.lang.Runnable
        public void run() {
            MainActivity.this.refresh();
            MainActivity.this.h.postDelayed(this, 3000L);
        }
    };

    @Override // android.app.Activity
    public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        this.db = new Db(this);
        this.net = new Network(this);
        this.exec = new ExecutionModule(this);
        this.botWallet = new SecureBotWallet(this);
        this.apiBudget = new ApiBudget(this);
        this.paperEngine = new PaperEngine(this, this.db, this.net);
        this.liveTrader = new LiveTrader(this, this.db, this.net);
        this.rayModeValue = Prefs.venueMode(this, "RAYDIUM");
        this.psModeValue = Prefs.venueMode(this, "PUMPSWAP");
        this.pfModeValue = Prefs.venueMode(this, "PUMPFUN");
        this.delayModeValue = Prefs.delayMode(this);
        buildUi();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 9);
        }
        this.h.post(this.refresher);
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        this.h.removeCallbacks(this.refresher);
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout shell = new LinearLayout(this);
        boolean z = true;
        shell.setOrientation(1);
        shell.setBackgroundColor(BG);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(16);
        top.setPadding(dp(16), dp(10), dp(16), dp(10));
        TextView brand = text("⚗  Meme Tail Lab", 21.0f, TEXT, true);
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView beta = pill("BETA 3", GREEN_DARK, GREEN);
        top.addView(beta);
        shell.addView(top);
        this.content = new FrameLayout(this);
        shell.addView(this.content, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        this.pages[0] = homePage();
        this.pages[1] = signalsPage();
        this.pages[2] = tradePage();
        this.pages[3] = portfolioPage();
        this.pages[4] = settingsPage();
        for (LinearLayout p : this.pages) {
            this.content.addView(p, new FrameLayout.LayoutParams(-1, -1));
        }
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(0);
        bottom.setPadding(dp(4), dp(5), dp(4), dp(7));
        bottom.setBackground(bg(Color.rgb(7, 13, 11), 0.0f, BORDER, 1));
        String[] labels = {"⌂\nHome", "ϟ\nSignals", "⇄\nTrade", "▥\nPortfolio", "⚙\nSettings"};
        int i = 0;
        while (i < labels.length) {
            final int idx = i;
            this.nav[i] = text(labels[i], 11.0f, MUTED, z);
            this.nav[i].setGravity(17);
            this.nav[i].setPadding(dp(2), dp(4), dp(2), dp(4));
            this.nav[i].setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda3
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.lambda$buildUi$0(idx, view);
                }
            });
            bottom.addView(this.nav[i], new LinearLayout.LayoutParams(0, dp(54), 1.0f));
            i++;
            z = true;
        }
        shell.addView(bottom);
        setContentView(shell);
        switchPage(0);
    }

    public /* synthetic */ void lambda$buildUi$0(int idx, View v) {
        switchPage(idx);
    }

    private LinearLayout homePage() {
        LinearLayout pageRoot = pageRoot();
        pageRoot.addView(titleBlock("Live Dashboard", "Paper analytics plus separately controlled real-money LIVE execution."));
        this.homeStatus = text("", 12.0f, MUTED, false);
        this.homeStatus.setPadding(0, 0, 0, dp(10));
        pageRoot.addView(this.homeStatus);
        LinearLayout equity = card();
        View cap = text("LIVE TOTAL EQUITY", 11.0f, MUTED, true);
        equity.addView(cap);
        this.homeEquity = text("$3,000.00", 34.0f, TEXT, true);
        equity.addView(this.homeEquity);
        this.homePnl = text("+$0.00  (+0.00%)", 16.0f, GREEN, true);
        this.homePnl.setPadding(0, dp(3), 0, dp(8));
        equity.addView(this.homePnl);
        this.homeChart = new EquityChartView(this);
        equity.addView(this.homeChart, new LinearLayout.LayoutParams(-1, dp(190)));
        LinearLayout mini = row();
        this.homeRoi = metric("ROI", "0.00%");
        this.homeOpen = metric("OPEN", "0");
        this.homeMark = metric("MARK", "—");
        addWeight(mini, this.homeRoi);
        addWeight(mini, this.homeOpen);
        addWeight(mini, this.homeMark);
        equity.addView(mini);
        pageRoot.addView(equity);
        pageRoot.addView(sectionLabel("Venue allocation"));
        this.homeVenueSummary = text("", 14.0f, TEXT, false);
        this.homeVenueSummary.setBackground(bg(SURFACE, 18.0f, BORDER, 1));
        this.homeVenueSummary.setPadding(dp(14), dp(12), dp(14), dp(12));
        pageRoot.addView(this.homeVenueSummary);
        pageRoot.addView(sectionLabel("Engine controls"));
        LinearLayout controls = row();
        Button start = action("START", true);
        Button sync = action("SCAN NOW", false);
        Button stop = action("STOP", false);
        addWeight(controls, start);
        addWeight(controls, sync);
        addWeight(controls, stop);
        pageRoot.addView(controls);
        start.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda7
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$homePage$1(view);
            }
        });
        sync.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda8
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$homePage$2(view);
            }
        });
        stop.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda9
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$homePage$3(view);
            }
        });
        TextView rules = text("Runner logic  ·  −32.5% initial stop  →  1.5× / 2× / 2.5× floors  ·  7× runner trail  ·  partials at 7× and 20×", 11.0f, MUTED, false);
        rules.setPadding(dp(4), dp(12), dp(4), dp(4));
        pageRoot.addView(rules);
        return wrap(pageRoot);
    }

    public /* synthetic */ void lambda$homePage$1(View v) {
        service("com.memetaillab.beta1.START");
    }

    public /* synthetic */ void lambda$homePage$2(View v) {
        service("com.memetaillab.beta1.SYNC");
    }

    public /* synthetic */ void lambda$homePage$3(View v) {
        service("com.memetaillab.beta1.STOP");
    }

    private LinearLayout signalsPage() {
        LinearLayout root = pageRoot();
        root.addView(titleBlock("Signals", "Fast-stream activity and qualified meme-coin opportunities."));
        this.signalStatus = text("", 12.0f, MUTED, false);
        root.addView(this.signalStatus);
        this.signalSummary = new LinearLayout(this);
        this.signalSummary.setOrientation(0);
        this.signalSummary.setPadding(0, dp(8), 0, dp(8));
        root.addView(this.signalSummary);
        root.addView(sectionLabel("Recent signals"));
        this.signalList = new LinearLayout(this);
        this.signalList.setOrientation(1);
        root.addView(this.signalList);
        return wrap(root);
    }

    private LinearLayout tradePage() {
        LinearLayout root = pageRoot();
        root.addView(titleBlock("Trading Venues", "Control each Solana venue independently."));
        TextView note = text("OFF ignores a venue. MONITOR records signals. PAPER uses the $1,000 mock book. LIVE sends real transactions from the dedicated encrypted bot wallet only when MASTER LIVE is armed. Helius discovers; CoinGecko (optional) / DEX Screener supply market data.", 11.0f, MUTED, false);
        note.setPadding(0, 0, 0, dp(10));
        root.addView(note);
        this.tradeVenueBox = new LinearLayout(this);
        this.tradeVenueBox.setOrientation(1);
        root.addView(this.tradeVenueBox);
        root.addView(sectionLabel("Open positions"));
        this.positionBox = new LinearLayout(this);
        this.positionBox.setOrientation(1);
        root.addView(this.positionBox);
        return wrap(root);
    }

    private LinearLayout portfolioPage() {
        LinearLayout root = pageRoot();
        root.addView(titleBlock("Portfolio", "Combined $3,000 paper account performance including open trades."));
        LinearLayout equity = card();
        this.portfolioHeadline = text("", 28.0f, TEXT, true);
        equity.addView(this.portfolioHeadline);
        this.portfolioChart = new EquityChartView(this);
        equity.addView(this.portfolioChart, new LinearLayout.LayoutParams(-1, dp(220)));
        root.addView(equity);
        this.portfolioStats = new LinearLayout(this);
        this.portfolioStats.setOrientation(1);
        this.portfolioStats.setPadding(0, dp(10), 0, 0);
        root.addView(this.portfolioStats);
        root.addView(sectionLabel("Recent fills / positions"));
        this.portfolioRecent = new LinearLayout(this);
        this.portfolioRecent.setOrientation(1);
        root.addView(this.portfolioRecent);
        Button export = action("EXPORT ALL CSV", false);
        export.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda61
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$portfolioPage$4(view);
            }
        });
        root.addView(export);
        return wrap(root);
    }

    public /* synthetic */ void lambda$portfolioPage$4(View v) {
        exportCsv();
    }

    private LinearLayout settingsPage() {
        LinearLayout root = pageRoot();
        root.addView(titleBlock("Execution & Settings", "Infrastructure, reaction speed, filters and execution tools."));
        this.settingsRuntime = text("", 12.0f, MUTED, false);
        this.settingsRuntime.setPadding(0, 0, 0, dp(8));
        root.addView(this.settingsRuntime);
        root.addView(sectionLabel("Connections"));
        this.helius = input(Prefs.helius(this), true, "Helius API key");
        this.wallet = input(Prefs.wallet(this), false, "Trading wallet PUBLIC address");
        this.rpc = input(Prefs.rpc(this), false, "Fallback Solana RPC");
        root.addView(fieldCard("Helius API Key", this.helius));
        root.addView(fieldCard("Wallet public address", this.wallet));
        root.addView(fieldCard("Fallback RPC", this.rpc));
        LinearLayout testRow = row();
        Button test = action("TEST CONNECTIONS", false);
        Button battery = action("BACKGROUND SETTINGS", false);
        addWeight(testRow, test);
        addWeight(testRow, battery);
        root.addView(testRow);
        this.apiResult = text("Not tested yet.", 11.0f, MUTED, false);
        this.apiResult.setPadding(dp(8), dp(6), dp(8), dp(8));
        root.addView(this.apiResult);
        test.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda20
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$settingsPage$7(view);
            }
        });
        battery.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda25
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$settingsPage$8(view);
            }
        });
        root.addView(sectionLabel("LIVE trading wallet"));
        this.swapApi = input(Prefs.swapApi(this), true, "https://jupiter-swap-api.quiknode.pro/YOUR_KEY");
        root.addView(fieldCard("QuickNode Swap API endpoint", this.swapApi));
        this.liveWalletStatus = text("", 12.0f, TEXT, true);
        this.liveWalletStatus.setPadding(dp(12), dp(10), dp(12), dp(10));
        this.liveWalletStatus.setBackground(bg(SURFACE, 16.0f, BORDER, 1));
        this.liveWalletStatus.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda26
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$settingsPage$9(view);
            }
        });
        root.addView(this.liveWalletStatus);
        LinearLayout wr = row();
        Button genWallet = action("GENERATE BOT WALLET", true);
        Button importWallet = action("IMPORT SECRET", false);
        Button exportWallet = action("BACKUP SECRET", false);
        addWeight(wr, genWallet);
        addWeight(wr, importWallet);
        addWeight(wr, exportWallet);
        root.addView(wr);
        genWallet.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda27
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$settingsPage$10(view);
            }
        });
        importWallet.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda28
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$settingsPage$11(view);
            }
        });
        exportWallet.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda29
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$settingsPage$12(view);
            }
        });
        root.addView(sectionLabel("LIVE risk controls"));
        this.liveRayAlloc = input(String.valueOf(Prefs.liveAllocationUsd(this, "RAYDIUM")), false, "Raydium USD allocation");
        this.livePsAlloc = input(String.valueOf(Prefs.liveAllocationUsd(this, "PUMPSWAP")), false, "PumpSwap USD allocation");
        this.livePfAlloc = input(String.valueOf(Prefs.liveAllocationUsd(this, "PUMPFUN")), false, "Pump.fun USD allocation");
        this.liveSize = input(String.valueOf(Prefs.liveSizePct(this)), false, "Position %");
        this.liveMaxTrade = input(String.valueOf(Prefs.liveMaxTradeUsd(this)), false, "Max trade USD");
        this.liveMaxExposure = input(String.valueOf(Prefs.liveMaxExposureUsd(this)), false, "Max exposure USD");
        this.liveDailyLoss = input(String.valueOf(Prefs.liveDailyLossUsd(this)), false, "Daily loss cutoff USD");
        this.liveSlippage = input(String.valueOf(Prefs.liveSlippageBps(this)), false, "Slippage bps");
        this.liveMaxImpact = input(String.valueOf(Prefs.liveMaxImpactPct(this)), false, "Max price impact %");
        this.livePriorityLamports = input(String.valueOf(Prefs.livePriorityMaxLamports(this)), false, "Max priority lamports");
        root.addView(twoFields("Raydium allocation USD", this.liveRayAlloc, "PumpSwap allocation USD", this.livePsAlloc));
        root.addView(twoFields("Pump.fun allocation USD", this.livePfAlloc, "Position size % of venue allocation", this.liveSize));
        root.addView(twoFields("Maximum USD per trade", this.liveMaxTrade, "Maximum total open exposure USD", this.liveMaxExposure));
        root.addView(twoFields("Daily realized-loss cutoff USD", this.liveDailyLoss, "LIVE slippage bps", this.liveSlippage));
        root.addView(twoFields("Maximum quote price impact %", this.liveMaxImpact, "Priority-fee cap lamports", this.livePriorityLamports));
        this.liveRiskStatus = text("", 11.0f, MUTED, false);
        this.liveRiskStatus.setPadding(0, dp(6), 0, dp(6));
        root.addView(this.liveRiskStatus);
        LinearLayout armRow = row();
        Button arm = action("ARM LIVE AUTO", true);
        Button disarm = action("EMERGENCY DISARM", false);
        disarm.setTextColor(RED);
        addWeight(armRow, arm);
        addWeight(armRow, disarm);
        root.addView(armRow);
        arm.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda30
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$settingsPage$13(view);
            }
        });
        disarm.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda31
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$settingsPage$14(view);
            }
        });
        root.addView(sectionLabel("API plan budget manager"));
        TextView budgetInfo = text("Enter each provider's monthly allowance and hard per-minute limit. The app calculates a sustainable average and a token-bucket burst rate so quiet periods bank capacity for fast signal bursts.", 11.0f, MUTED, false);
        budgetInfo.setPadding(0, 0, 0, dp(8));
        root.addView(budgetInfo);
        root.addView(budgetCard("HELIUS", "Helius"));
        root.addView(budgetCard("COINGECKO", "CoinGecko"));
        root.addView(budgetCard("TRADING", "Trading API"));
        this.budgetStatus = text("", 11.0f, MUTED, false);
        this.budgetStatus.setPadding(0, dp(5), 0, dp(10));
        root.addView(this.budgetStatus);
        this.coinGeckoEnabled = new Switch(this);
        this.coinGeckoEnabled.setText("Use CoinGecko paid on-chain data when configured");
        this.coinGeckoEnabled.setTextColor(TEXT);
        this.coinGeckoEnabled.setChecked(Prefs.coinGeckoEnabled(this));
        root.addView(this.coinGeckoEnabled);
        this.coinGeckoPro = new Switch(this);
        this.coinGeckoPro.setText("CoinGecko Pro key (off = Demo key)");
        this.coinGeckoPro.setTextColor(TEXT);
        this.coinGeckoPro.setChecked(Prefs.coinGeckoPro(this));
        root.addView(this.coinGeckoPro);
        this.coinGeckoKey = input(Prefs.coinGeckoKey(this), true, "CoinGecko API key");
        root.addView(fieldCard("CoinGecko API key", this.coinGeckoKey));
        root.addView(sectionLabel("Execution mode"));
        this.delayModeRow = new LinearLayout(this);
        this.delayModeRow.setOrientation(0);
        root.addView(this.delayModeRow);
        renderDelayModes();
        this.delaySec = input(String.valueOf(Prefs.delaySec(this)), false, "Custom seconds");
        this.sizePct = input(String.valueOf(Prefs.sizePct(this)), false, "Position size %");
        root.addView(twoFields("Custom delay seconds", this.delaySec, "Position size % of $1,000 book", this.sizePct));
        root.addView(sectionLabel("Trade costs"));
        this.entryBps = input(String.valueOf(Prefs.entryBps(this)), false, "Entry bps");
        this.exitBps = input(String.valueOf(Prefs.exitBps(this)), false, "Exit bps");
        this.fixedFee = input(String.valueOf(Prefs.fixedFeeUsd(this)), false, "Fixed fee USD");
        root.addView(twoFields("Entry cost bps", this.entryBps, "Exit cost bps", this.exitBps));
        root.addView(fieldCard("Fixed round-trip cost USD", this.fixedFee));
        root.addView(sectionLabel("Signal filters"));
        this.minLiq = input(String.valueOf(Prefs.minLiq(this)), false, "Minimum liquidity");
        this.minTrades = input(String.valueOf(Prefs.minTrades(this)), false, "Minimum transactions");
        this.minBuyers = input(String.valueOf(Prefs.minBuyers(this)), false, "Minimum buyers");
        this.minRatio = input(String.valueOf(Prefs.minRatio(this)), false, "Buy/sell ratio");
        this.minMomentum = input(String.valueOf(Prefs.minMomentum(this)), false, "Momentum %");
        this.minAge = input(String.valueOf(Prefs.minAge(this)), false, "Min age minutes");
        this.maxAge = input(String.valueOf(Prefs.maxAge(this)), false, "Max age minutes");
        this.maxOpen = input(String.valueOf(Prefs.maxOpen(this)), false, "Max positions");
        root.addView(twoFields("Minimum liquidity USD", this.minLiq, "Minimum 1h transactions", this.minTrades));
        root.addView(twoFields("Minimum buyers / proxy", this.minBuyers, "Minimum buy/sell ratio", this.minRatio));
        root.addView(twoFields("Minimum 1h momentum %", this.minMomentum, "Maximum open per venue", this.maxOpen));
        root.addView(twoFields("Minimum pool age min", this.minAge, "Maximum pool age min", this.maxAge));
        root.addView(sectionLabel("Infrastructure cadence"));
        this.scanSec = input(String.valueOf(Prefs.scanSec(this)), false, "Deep scan seconds");
        this.markSec = input(String.valueOf(Prefs.markSec(this)), false, "Idle mark seconds");
        root.addView(twoFields("Deep fallback scan seconds", this.scanSec, "Idle mark seconds", this.markSec));
        this.rayApi = input(Prefs.rayApi(this), false, "Raydium API");
        this.raySwap = input(Prefs.raySwap(this), false, "Raydium trade API");
        root.addView(fieldCard("Raydium API host", this.rayApi));
        root.addView(fieldCard("Raydium Trade API host", this.raySwap));
        this.fastStreamToggle = new Switch(this);
        this.fastStreamToggle.setText("Helius fast WebSocket stream");
        this.fastStreamToggle.setTextColor(TEXT);
        this.fastStreamToggle.setTextSize(14.0f);
        this.fastStreamToggle.setChecked(Prefs.fastStream(this));
        this.fastStreamToggle.setPadding(dp(12), dp(10), dp(12), dp(10));
        this.fastStreamToggle.setBackground(bg(SURFACE, 16.0f, BORDER, 1));
        root.addView(this.fastStreamToggle);
        Button save = action("SAVE SETTINGS + RESTART STREAM", true);
        save.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda32
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$settingsPage$15(view);
            }
        });
        root.addView(save);
        root.addView(sectionLabel("Raydium execution module"));
        TextView warn = text("Advanced manual Raydium tool. Beta 3 LIVE AUTO uses the separate encrypted bot wallet and QuickNode transaction builder above; this section remains for manual diagnostics.", 11.0f, AMBER, false);
        warn.setPadding(0, 0, 0, dp(8));
        root.addView(warn);
        this.exInput = input("So11111111111111111111111111111111111111112", false, "Input mint");
        this.exOutput = input("", false, "Output mint");
        this.exAmount = input("10000000", false, "Raw amount");
        this.exSlip = input("150", false, "Slippage bps");
        root.addView(fieldCard("Input mint", this.exInput));
        root.addView(fieldCard("Output mint", this.exOutput));
        root.addView(twoFields("Raw amount", this.exAmount, "Slippage bps", this.exSlip));
        LinearLayout er = row();
        Button latest = action("LATEST RAY TOKEN", false);
        Button preview = action("BUILD QUOTE + TX", true);
        addWeight(er, latest);
        addWeight(er, preview);
        root.addView(er);
        latest.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda34
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$settingsPage$16(view);
            }
        });
        preview.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda21
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$settingsPage$17(view);
            }
        });
        this.signedTx = input("", true, "Externally signed base64 V0 transaction");
        this.signedTx.setMinLines(3);
        root.addView(fieldCard("Signed transaction", this.signedTx));
        Button broadcast = action("BROADCAST SIGNED TRANSACTION", false);
        broadcast.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda23
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$settingsPage$18(view);
            }
        });
        root.addView(broadcast);
        this.execResult = text("Execution preview ready.", 11.0f, MUTED, false);
        this.execResult.setTextIsSelectable(true);
        this.execResult.setPadding(0, dp(6), 0, dp(8));
        root.addView(this.execResult);
        Button reset = action("RESET ALL PAPER ACCOUNTS", false);
        reset.setTextColor(RED);
        reset.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda24
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$settingsPage$19(view);
            }
        });
        root.addView(reset);
        return wrap(root);
    }

    public /* synthetic */ void lambda$settingsPage$5(String x) {
        this.apiResult.setText(x);
    }

    public /* synthetic */ void lambda$settingsPage$6() {
        final String x = this.net.testSources();
        runOnUiThread(new Runnable() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda44
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$settingsPage$5(x);
            }
        });
    }

    public /* synthetic */ void lambda$settingsPage$7(View v) {
        this.apiResult.setText("Testing…");
        new Thread(new Runnable() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda54
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$settingsPage$6();
            }
        }).start();
    }

    public /* synthetic */ void lambda$settingsPage$8(View v) {
        openBattery();
    }

    public /* synthetic */ void lambda$settingsPage$9(View v) {
        if (this.botWallet.exists()) {
            copy("Bot wallet public address", this.botWallet.publicKey());
        }
    }

    public /* synthetic */ void lambda$settingsPage$10(View v) {
        generateBotWallet();
    }

    public /* synthetic */ void lambda$settingsPage$11(View v) {
        importBotWallet();
    }

    public /* synthetic */ void lambda$settingsPage$12(View v) {
        exportBotWallet();
    }

    public /* synthetic */ void lambda$settingsPage$13(View v) {
        armLive();
    }

    public /* synthetic */ void lambda$settingsPage$14(View v) {
        Prefs.setLiveArmed(this, false);
        toast("LIVE buys disarmed. Protective exits remain active.");
        refresh();
    }

    public /* synthetic */ void lambda$settingsPage$15(View v) {
        saveSettings();
    }

    public /* synthetic */ void lambda$settingsPage$16(View v) {
        useLatestRaydium();
    }

    public /* synthetic */ void lambda$settingsPage$17(View v) {
        previewExecution();
    }

    public /* synthetic */ void lambda$settingsPage$18(View v) {
        confirmBroadcast();
    }

    public /* synthetic */ void lambda$settingsPage$19(View v) {
        confirmReset();
    }

    public void refresh() {
        try {
            refreshHome();
            refreshSignals();
            refreshTrade();
            refreshPortfolio();
            refreshRuntime();
        } catch (Exception e) {
            String msg = e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "unknown" : e.getMessage());
            if (this.homeStatus != null) {
                this.homeStatus.setText("UI refresh issue · " + msg);
                this.homeStatus.setTextColor(RED);
            }
        }
    }

    private void refreshHome() {
        long now = System.currentTimeMillis();
        long hb = Prefs.heartbeat(this);
        long lm = Prefs.lastMark(this);
        long se = Prefs.streamLastEvent(this);
        boolean active = Prefs.tracking(this) && hb > 0 && now - hb < 120000;
        LiveStats liveNow = this.db.liveStats();
        this.homeStatus.setText((active ? "● ENGINE ACTIVE" : "○ ENGINE OFF / STALE") + "   ·   Fast stream " + Prefs.streamState(this) + "   ·   last trigger " + (se == 0 ? "—" : age(now - se)) + "\n" + (Prefs.liveArmed(this) ? "⚠ LIVE ARMED" : "LIVE disarmed") + " · real positions " + liveNow.openCount + " · live MTM $" + String.format(Locale.US, "%.2f", Double.valueOf(liveNow.openExposureUsd)));
        StringBuilder venue = new StringBuilder();
        int opens = 0;
        String[] strArr = Config.BOOKS;
        int length = strArr.length;
        double totalOpen = 0.0d;
        double totalOpen2 = 0.0d;
        double totalCash = 0.0d;
        int i = 0;
        while (i < length) {
            String b = strArr[i];
            String[] strArr2 = strArr;
            BookStats s = this.db.stats(b);
            long now2 = now;
            double total = totalCash + s.equity;
            totalOpen2 += s.cash;
            totalOpen += s.unrealized;
            opens += s.open;
            if (venue.length() > 0) {
                venue.append("\n\n");
            }
            LiveStats liveNow2 = liveNow;
            venue.append(displayBook(b)).append("   PAPER $").append(String.format(Locale.US, "%,.2f", Double.valueOf(s.equity))).append("   ").append(String.format(Locale.US, "%+.2f%%", Double.valueOf(s.roiPct))).append("   · ").append(s.open).append(" paper open").append("   · ").append(venueMode(b));
            int lo = this.db.liveOpenCount(b);
            if (lo > 0 || "LIVE".equals(venueMode(b))) {
                venue.append("\n   LIVE ").append(lo).append(" open · MTM $").append(String.format(Locale.US, "%.2f", Double.valueOf(liveExposureUsd(b)))).append(" · alloc $").append(String.format(Locale.US, "%.0f", Double.valueOf(Prefs.liveAllocationUsd(this, b))));
            }
            i++;
            strArr = strArr2;
            now = now2;
            liveNow = liveNow2;
            totalCash = total;
        }
        long now3 = now;
        double pnl = totalCash - 3000.0d;
        double roi = ((totalCash / 3000.0d) - 1.0d) * 100.0d;
        this.homeEquity.setText(String.format(Locale.US, "$%,.2f", Double.valueOf(totalCash)));
        this.homePnl.setText(String.format(Locale.US, "$%+,.2f   (%+.2f%%)", Double.valueOf(pnl), Double.valueOf(roi)));
        this.homePnl.setTextColor(pnl >= 0.0d ? GREEN : RED);
        setMetric(this.homeRoi, "ROI", String.format(Locale.US, "%+.2f%%", Double.valueOf(roi)), roi >= 0.0d ? GREEN : RED);
        setMetric(this.homeOpen, "OPEN", String.valueOf(opens), TEXT);
        setMetric(this.homeMark, "MARK", lm != 0 ? shortAge(now3 - lm) : "—", (lm <= 0 || now3 - lm >= 30000) ? AMBER : GREEN);
        this.homeVenueSummary.setText(venue.toString());
        this.homeChart.setData("Combined live equity", this.db.combinedSnapshots(700));
    }

    private void refreshSignals() {
        long now = System.currentTimeMillis();
        long se = Prefs.streamLastEvent(this);
        this.signalStatus.setText("● " + Prefs.streamState(this) + "   ·   latest program trigger " + (se == 0 ? "none yet" : age(now - se)) + "\nRaydium / PumpSwap / Pump.fun stream events: " + Prefs.streamCount(this, "RAYDIUM") + " / " + Prefs.streamCount(this, "PUMPSWAP") + " / " + Prefs.streamCount(this, "PUMPFUN"));
        this.signalSummary.removeAllViews();
        int total = this.db.totalSignals();
        String str = "PENDING";
        int pending = this.db.signalCountByState("PENDING") + this.db.signalCountByState("LIVE_PENDING");
        int mon = this.db.signalCountByState("MONITOR");
        int failed = this.db.signalCountByState("FAILED") + this.db.signalCountByState("SKIPPED") + this.db.signalCountByState("LIVE_FAILED") + this.db.signalCountByState("LIVE_BLOCKED");
        addWeight(this.signalSummary, statTile("TOTAL", String.valueOf(total), TEXT));
        addWeight(this.signalSummary, statTile("PENDING", String.valueOf(pending), AMBER));
        addWeight(this.signalSummary, statTile("MONITOR", String.valueOf(mon), GREEN));
        addWeight(this.signalSummary, statTile("FAILED", String.valueOf(failed), RED));
        this.signalList.removeAllViews();
        List<Signal> xs = this.db.recentSignals(30);
        if (xs.isEmpty()) {
            this.signalList.addView(emptyCard("No qualified signals yet."));
            return;
        }
        Iterator<Signal> it = xs.iterator();
        while (it.hasNext()) {
            final Signal s = it.next();
            LinearLayout box = card();
            List<Signal> xs2 = xs;
            Iterator<Signal> it2 = it;
            box.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout top = row();
            long se2 = se;
            TextView name = text((s.symbol == null || s.symbol.isEmpty()) ? shortAddr(s.mint) : s.symbol, 16.0f, TEXT, true);
            top.addView(name, new LinearLayout.LayoutParams(0, -2, 1.0f));
            int sc = ("FILLED".equals(s.state) || "LIVE_FILLED".equals(s.state)) ? GREEN : (str.equals(s.state) || "LIVE_PENDING".equals(s.state)) ? AMBER : "MONITOR".equals(s.state) ? Color.rgb(90, 190, 255) : RED;
            int total2 = total;
            top.addView(pill(s.state == null ? "" : s.state, Color.argb(40, Color.red(sc), Color.green(sc), Color.blue(sc)), sc));
            box.addView(top);
            box.addView(text(displayBook(s.book) + " · " + safe(s.venue) + " · " + age(System.currentTimeMillis() - s.detectedAt), 11.0f, MUTED, false));
            String metrics = String.format(Locale.US, "$%s   ·   liq $%,.0f   ·   momentum %+.2f%%   ·   buy/sell %.2f   ·   buyers %d", Db.fmt(s.signalPrice), Double.valueOf(s.liquidity), Double.valueOf(s.momentum), Double.valueOf(s.buySell), Integer.valueOf(s.buyers));
            TextView m = text(metrics, 12.0f, TEXT, false);
            m.setPadding(0, dp(6), 0, dp(4));
            box.addView(m);
            TextView addr = text(shortAddr(s.mint) + "   ⧉", 11.0f, MUTED, false);
            addr.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda51
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.lambda$refreshSignals$20(s, view);
                }
            });
            box.addView(addr);
            this.signalList.addView(box);
            pending = pending;
            xs = xs2;
            it = it2;
            se = se2;
            total = total2;
            str = str;
        }
    }

    public /* synthetic */ void lambda$refreshSignals$20(Signal s, View v) {
        copy("Token mint", s.mint);
    }

    private void refreshTrade() {
        this.tradeVenueBox.removeAllViews();
        this.tradeVenueBox.addView(venueCard("RAYDIUM", "Raydium", "AMM / CPMM / CLMM / LaunchLab", this.rayModeValue));
        this.tradeVenueBox.addView(venueCard("PUMPSWAP", "PumpSwap", "Pump.fun migrated AMM liquidity", this.psModeValue));
        this.tradeVenueBox.addView(venueCard("PUMPFUN", "Pump.fun", "Bonding-curve launchpad", this.pfModeValue));
        this.positionBox.removeAllViews();
        List<LivePosition> live = this.db.openLivePositions();
        if (!live.isEmpty()) {
            TextView h = text("REAL-MONEY LIVE POSITIONS", 12.0f, Prefs.liveArmed(this) ? RED : AMBER, true);
            h.setPadding(dp(2), dp(4), 0, dp(8));
            this.positionBox.addView(h);
            for (LivePosition p : live) {
                this.positionBox.addView(livePositionCard(p));
            }
        }
        List<Position> ps = this.db.openPositions();
        if (!ps.isEmpty()) {
            TextView h2 = text("PAPER POSITIONS", 12.0f, MUTED, true);
            h2.setPadding(dp(2), dp(10), 0, dp(8));
            this.positionBox.addView(h2);
            for (Position p2 : ps) {
                this.positionBox.addView(positionCard(p2, true));
            }
        }
        if (!live.isEmpty() || !ps.isEmpty()) {
            return;
        }
        this.positionBox.addView(emptyCard("No open positions."));
    }

    private void refreshPortfolio() {
        double wr;
        double totalUnreal = 0.0d;
        double weightedExp = 0.0d;
        int closed = 0;
        int wins = 0;
        int open = 0;
        double total = 0.0d;
        String[] strArr = Config.BOOKS;
        int length = strArr.length;
        double totalCash = 0.0d;
        int i = 0;
        while (i < length) {
            String[] strArr2 = strArr;
            BookStats s = this.db.stats(strArr[i]);
            int i2 = length;
            total += s.equity;
            totalCash += s.cash;
            double totalUnreal2 = totalUnreal + s.unrealized;
            closed += s.closed;
            wins += s.wins;
            open += s.open;
            weightedExp += s.expectancy * s.closed;
            if (Double.isFinite(s.profitFactor)) {
                double d = s.profitFactor;
            }
            i++;
            strArr = strArr2;
            length = i2;
            totalUnreal = totalUnreal2;
        }
        double pnl = total - 3000.0d;
        double roi = ((total / 3000.0d) - 1.0d) * 100.0d;
        if (closed > 0) {
            double profit = wins;
            double loss = closed;
            wr = (profit * 100.0d) / loss;
        } else {
            wr = 0.0d;
        }
        double exp = closed > 0 ? weightedExp / closed : 0.0d;
        this.portfolioHeadline.setText(String.format(Locale.US, "$%,.2f   %+.2f%%", Double.valueOf(total), Double.valueOf(roi)));
        this.portfolioHeadline.setTextColor(pnl >= 0.0d ? TEXT : RED);
        this.portfolioChart.setData("Portfolio equity", this.db.combinedSnapshots(1200));
        this.portfolioStats.removeAllViews();
        LinearLayout a = row();
        addWeight(a, statTile("PAPER P/L", String.format(Locale.US, "$%+,.2f", Double.valueOf(pnl)), pnl >= 0.0d ? GREEN : RED));
        addWeight(a, statTile("PAPER OPEN MTM", String.format(Locale.US, "$%,.2f", Double.valueOf(totalUnreal)), TEXT));
        addWeight(a, statTile("PAPER CASH", String.format(Locale.US, "$%,.2f", Double.valueOf(totalCash)), TEXT));
        this.portfolioStats.addView(a);
        LinearLayout b = row();
        addWeight(b, statTile("WIN RATE", String.format(Locale.US, "%.1f%%", Double.valueOf(wr)), GREEN));
        addWeight(b, statTile("CLOSED", String.valueOf(closed), TEXT));
        addWeight(b, statTile("EXPECTANCY", String.format(Locale.US, "%+.2f", Double.valueOf(exp)), exp >= 0.0d ? GREEN : RED));
        this.portfolioStats.addView(b);
        LiveStats ls = this.db.liveStats();
        LinearLayout live = row();
        addWeight(live, statTile("LIVE MODE", Prefs.liveArmed(this) ? "ARMED" : "DISARMED", Prefs.liveArmed(this) ? RED : MUTED));
        addWeight(live, statTile("LIVE OPEN", String.valueOf(ls.openCount), TEXT));
        addWeight(live, statTile("LIVE MTM", String.format(Locale.US, "$%.2f", Double.valueOf(ls.openExposureUsd)), TEXT));
        addWeight(live, statTile("TODAY REALIZED", String.format(Locale.US, "$%+,.2f", Double.valueOf(ls.realizedPnlTodayUsd)), ls.realizedPnlTodayUsd >= 0.0d ? GREEN : RED));
        this.portfolioStats.addView(live);
        this.portfolioRecent.removeAllViews();
        List<LivePosition> liveRecent = this.db.recentLivePositions(8);
        if (!liveRecent.isEmpty()) {
            TextView lh = text("REAL-MONEY LIVE HISTORY", 11.0f, RED, true);
            lh.setPadding(dp(2), dp(4), 0, dp(7));
            this.portfolioRecent.addView(lh);
            for (LivePosition p : liveRecent) {
                this.portfolioRecent.addView(livePositionCard(p));
            }
        }
        List<Position> recent = this.db.recentPositions(12);
        if (!recent.isEmpty()) {
            TextView ph = text("PAPER HISTORY", 11.0f, MUTED, true);
            ph.setPadding(dp(2), dp(10), 0, dp(7));
            this.portfolioRecent.addView(ph);
            for (Position p2 : recent) {
                this.portfolioRecent.addView(positionCard(p2, false));
            }
        }
        if (liveRecent.isEmpty() && recent.isEmpty()) {
            this.portfolioRecent.addView(emptyCard("No positions recorded yet."));
        }
    }

    private void refreshRuntime() {
        if (this.settingsRuntime == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long lm = Prefs.lastMark(this);
        long ld = Prefs.lastDiscovery(this);
        this.settingsRuntime.setText("Fast trigger: Helius · " + Prefs.streamState(this) + "   ·   reaction " + Prefs.effectiveDelaySec(this) + "s\nDiscovery: " + Prefs.discoverySource(this) + " · Market data: " + Prefs.marketSource(this) + "\nLatest market mark " + (lm == 0 ? "—" : age(now - lm)) + "   ·   latest discovery " + (ld != 0 ? age(now - ld) : "—") + (Prefs.lastError(this).isEmpty() ? "" : "\nLast source issue: " + Prefs.lastError(this)));
        if (this.liveWalletStatus != null) {
            this.liveWalletStatus.setText(this.botWallet.exists() ? "Bot wallet  " + shortAddr(this.botWallet.publicKey()) + "  · encrypted locally" : "No LIVE bot wallet stored");
            this.liveWalletStatus.setTextColor(this.botWallet.exists() ? GREEN : AMBER);
        }
        if (this.liveRiskStatus != null) {
            LiveStats st = this.db.liveStats();
            this.liveRiskStatus.setText((Prefs.liveArmed(this) ? "⚠ MASTER LIVE ARMED" : "MASTER LIVE DISARMED") + " · open " + st.openCount + " · MTM $" + String.format(Locale.US, "%.2f", Double.valueOf(st.openExposureUsd)) + " · today realized " + String.format(Locale.US, "$%+,.2f", Double.valueOf(st.realizedPnlTodayUsd)));
            this.liveRiskStatus.setTextColor(Prefs.liveArmed(this) ? RED : MUTED);
        }
        if (this.budgetStatus != null) {
            this.budgetStatus.setText(this.apiBudget.snapshot("HELIUS").summary() + "\n" + this.apiBudget.snapshot("COINGECKO").summary() + "\n" + this.apiBudget.snapshot("TRADING").summary());
        }
    }

    private LinearLayout venueCard(String book, String title, String subtitle, String mode) {
        BookStats s = this.db.stats(book);
        ScannerTelemetry t = this.db.telemetry(book, System.currentTimeMillis(), Prefs.minAge(this), Prefs.maxAge(this));
        LinearLayout card = card();
        LinearLayout head = row();
        TextView icon = text(book.equals("RAYDIUM") ? "◈" : book.equals("PUMPSWAP") ? "◉" : "◐", 24.0f, GREEN, true);
        icon.setGravity(17);
        head.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(1);
        names.addView(text(title, 17.0f, TEXT, true));
        names.addView(text(subtitle, 10.0f, MUTED, false));
        head.addView(names, new LinearLayout.LayoutParams(0, -2, 1.0f));
        String ss = Prefs.streamState(this);
        boolean conn = ss != null && ss.contains("CONNECTED");
        String compact = conn ? "● CONNECTED" : (ss == null || !ss.startsWith("ERROR")) ? "○ RECONNECTING" : "● STREAM ERROR";
        head.addView(pill(compact, conn ? GREEN_DARK : Color.rgb(62, 48, 15), conn ? GREEN : AMBER));
        card.addView(head);
        LinearLayout modes = row();
        modes.setPadding(0, dp(10), 0, dp(8));
        String[] strArr = Config.VENUE_MODES;
        int i = 0;
        for (int length = strArr.length; i < length; length = length) {
            LinearLayout names2 = names;
            String m = strArr[i];
            String[] strArr2 = strArr;
            TextView b = modeChoice(book, m, mode.equals(m));
            modes.addView(b, new LinearLayout.LayoutParams(0, dp(38), 1.0f));
            i++;
            compact = compact;
            names = names2;
            strArr = strArr2;
            ss = ss;
        }
        card.addView(modes);
        LinearLayout stats = row();
        addWeight(stats, miniStat("PAPER EQ", String.format(Locale.US, "$%,.2f", Double.valueOf(s.equity))));
        addWeight(stats, miniStat("PAPER POS", String.valueOf(s.open)));
        addWeight(stats, miniStat("IN WINDOW", String.valueOf(t.eligible)));
        addWeight(stats, miniStat("FILLS", String.valueOf(t.fills)));
        card.addView(stats);
        int liveOpen = this.db.liveOpenCount(book);
        double liveExp = liveExposureUsd(book);
        if ("LIVE".equals(mode) || liveOpen > 0) {
            LinearLayout liveStats = row();
            addWeight(liveStats, miniStat("LIVE ALLOC", String.format(Locale.US, "$%.0f", Double.valueOf(Prefs.liveAllocationUsd(this, book)))));
            addWeight(liveStats, miniStat("LIVE POS", String.valueOf(liveOpen)));
            addWeight(liveStats, miniStat("LIVE MTM", String.format(Locale.US, "$%.2f", Double.valueOf(liveExp))));
            addWeight(liveStats, miniStat("MASTER", Prefs.liveArmed(this) ? "ARMED" : "OFF"));
            card.addView(liveStats);
        }
        TextView block = text("Blocked now: " + t.reasons, 10.0f, MUTED, false);
        block.setPadding(0, dp(8), 0, 0);
        card.addView(block);
        return card;
    }

    private TextView modeChoice(final String book, final String mode, boolean active) {
        final boolean live = "LIVE".equals(mode);
        int fill = active ? live ? Color.rgb(110, 25, 34) : GREEN : SURFACE_2;
        int tc = active ? live ? -1 : -16777216 : MUTED;
        TextView t = text(live ? "LIVE" : mode, 10.0f, tc, true);
        t.setGravity(17);
        t.setBackground(bg(fill, 12.0f, active ? live ? RED : GREEN : BORDER, 1));
        t.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda11
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$modeChoice$21(live, book, mode, view);
            }
        });
        return t;
    }

    public /* synthetic */ void lambda$modeChoice$21(boolean live, String book, String mode, View v) {
        if (live && (!this.botWallet.exists() || Prefs.swapApi(this).trim().isEmpty())) {
            toast("Set up the encrypted bot wallet and QuickNode Swap API first.");
            switchPage(4);
        } else {
            setVenueMode(book, mode);
            refreshTrade();
        }
    }

    private void setVenueMode(String book, String mode) {
        if ("RAYDIUM".equals(book)) {
            this.rayModeValue = mode;
        } else if ("PUMPSWAP".equals(book)) {
            this.psModeValue = mode;
        } else {
            this.pfModeValue = mode;
        }
        Prefs.setVenueMode(this, book, mode);
    }

    private String venueMode(String book) {
        return "RAYDIUM".equals(book) ? this.rayModeValue : "PUMPSWAP".equals(book) ? this.psModeValue : this.pfModeValue;
    }

    private LinearLayout positionCard(final Position p, boolean detailed) {
        double x = p.entryMarketPrice > 0.0d ? p.lastPrice / p.entryMarketPrice : 0.0d;
        double markValue = p.remainingQty * p.lastPrice;
        double markPnl = (p.realizedProceeds + markValue) - p.stakeUsd;
        double exitValue = this.paperEngine.estimatedLiquidationValue(p);
        double exitPnl = (p.realizedProceeds + exitValue) - p.stakeUsd;
        double exitRoi = p.stakeUsd > 0.0d ? (100.0d * exitPnl) / p.stakeUsd : 0.0d;
        LinearLayout box = card();
        LinearLayout head = row();
        TextView nm = text((p.symbol == null || p.symbol.isEmpty()) ? shortAddr(p.mint) : p.symbol, 16.0f, TEXT, true);
        head.addView(nm, new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView pl = pill("EXIT " + String.format(Locale.US, "%+.1f%%", Double.valueOf(exitRoi)), exitPnl >= 0.0d ? GREEN_DARK : Color.rgb(64, 18, 25), exitPnl >= 0.0d ? GREEN : RED);
        head.addView(pl);
        box.addView(head);
        box.addView(text(displayBook(p.book) + " · " + safe(p.venue) + " · " + (p.state == null ? "" : p.state), 11.0f, MUTED, false));
        LinearLayout stats = row();
        addWeight(stats, miniStat("ENTRY", "$" + Db.fmt(p.entryMarketPrice)));
        addWeight(stats, miniStat("NOW", "$" + Db.fmt(p.lastPrice)));
        addWeight(stats, miniStat("X", String.format(Locale.US, "%.2fx", Double.valueOf(x))));
        box.addView(stats);
        if (detailed) {
            LinearLayout stats2 = row();
            Locale locale = Locale.US;
            double x2 = p.stakeUsd;
            addWeight(stats2, miniStat("STAKE", String.format(locale, "$%.2f", Double.valueOf(x2))));
            addWeight(stats2, miniStat("EXIT P/L", String.format(Locale.US, "$%+,.2f", Double.valueOf(exitPnl))));
            addWeight(stats2, miniStat("FLOOR", String.format(Locale.US, "%.2fx", Double.valueOf(p.floorX))));
            box.addView(stats2);
            TextView rule = text("Mark P/L " + String.format(Locale.US, "$%+,.2f", Double.valueOf(markPnl)) + " · liq $" + String.format(Locale.US, "%,.0f", Double.valueOf(p.lastLiquidityUsd)) + " · high " + String.format(Locale.US, "%.2fx", Double.valueOf(p.highX)) + " · runner " + (p.trailActive ? "ON @ " + String.format(Locale.US, "%.2fx", Double.valueOf(p.trailPeakX)) : "off") + " · age " + duration(p.entryTime), 10.0f, MUTED, false);
            rule.setPadding(0, dp(8), 0, dp(5));
            box.addView(rule);
            LinearLayout buttons = row();
            Button token = smallAction("COPY TOKEN");
            Button pool = smallAction("COPY POOL");
            Button scan = smallAction("SOLSCAN");
            addWeight(buttons, token);
            addWeight(buttons, pool);
            addWeight(buttons, scan);
            box.addView(buttons);
            token.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda35
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.lambda$positionCard$22(p, view);
                }
            });
            pool.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda36
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.lambda$positionCard$23(p, view);
                }
            });
            scan.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda37
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.lambda$positionCard$24(p, view);
                }
            });
            LinearLayout exitRow = row();
            Button checkExit = smallAction("CHECK EXIT");
            Button closeNow = smallAction("CLOSE PAPER");
            closeNow.setTextColor(RED);
            addWeight(exitRow, checkExit);
            addWeight(exitRow, closeNow);
            box.addView(exitRow);
            checkExit.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda38
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.lambda$positionCard$25(p, view);
                }
            });
            closeNow.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda39
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.lambda$positionCard$26(p, view);
                }
            });
        }
        box.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda40
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$positionCard$27(p, view);
            }
        });
        return box;
    }

    public /* synthetic */ void lambda$positionCard$22(Position p, View v) {
        copy("Token mint", p.mint);
    }

    public /* synthetic */ void lambda$positionCard$23(Position p, View v) {
        copy("Pool", p.pool);
    }

    public /* synthetic */ void lambda$positionCard$24(Position p, View v) {
        openUrl("https://solscan.io/token/" + p.mint);
    }

    public /* synthetic */ void lambda$positionCard$25(Position p, View v) {
        previewPaperExit(p);
    }

    public /* synthetic */ void lambda$positionCard$26(Position p, View v) {
        confirmPaperClose(p);
    }

    public /* synthetic */ void lambda$positionCard$27(Position p, View v) {
        showPositionDetail(p);
    }

    private void showPositionDetail(final Position p) {
        double x = p.entryMarketPrice > 0.0d ? p.lastPrice / p.entryMarketPrice : 0.0d;
        double markValue = p.remainingQty * p.lastPrice;
        double markPnl = (p.realizedProceeds + markValue) - p.stakeUsd;
        double exitValue = this.paperEngine.estimatedLiquidationValue(p);
        double exitPnl = (p.realizedProceeds + exitValue) - p.stakeUsd;
        StringBuilder append = new StringBuilder().append("Venue: ").append(displayBook(p.book)).append("\nToken: ").append(p.mint).append("\nPool: ").append(p.pool).append("\n\nEntry: $").append(Db.fmt(p.entryMarketPrice)).append("\nCurrent mark: $").append(Db.fmt(p.lastPrice)).append("\nMultiple: ").append(String.format(Locale.US, "%.3fx", Double.valueOf(x))).append("\nHigh: ");
        Locale locale = Locale.US;
        double x2 = p.highX;
        String msg = append.append(String.format(locale, "%.3fx", Double.valueOf(x2))).append("\nPool liquidity: $").append(String.format(Locale.US, "%,.2f", Double.valueOf(p.lastLiquidityUsd))).append("\nFloor: ").append(String.format(Locale.US, "%.2fx", Double.valueOf(p.floorX))).append("\nRunner trail: ").append(p.trailActive ? "ON" : "OFF").append("\nStake: $").append(String.format(Locale.US, "%.2f", Double.valueOf(p.stakeUsd))).append("\nMark P/L: ").append(String.format(Locale.US, "$%+,.2f", Double.valueOf(markPnl))).append("\nEstimated liquidatable P/L: ").append(String.format(Locale.US, "$%+,.2f", Double.valueOf(exitPnl))).append("\nRealized partial proceeds: $").append(String.format(Locale.US, "%.2f", Double.valueOf(p.realizedProceeds))).toString();
        new AlertDialog.Builder(this).setTitle(p.symbol + " · Position Details").setMessage(msg).setNeutralButton("Check exit", new DialogInterface.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda12
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.lambda$showPositionDetail$28(p, dialogInterface, i);
            }
        }).setNegativeButton("Close", (DialogInterface.OnClickListener) null).setPositiveButton("Solscan", new DialogInterface.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda13
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.lambda$showPositionDetail$29(p, dialogInterface, i);
            }
        }).show();
    }

    public /* synthetic */ void lambda$showPositionDetail$28(Position p, DialogInterface d, int w) {
        previewPaperExit(p);
    }

    public /* synthetic */ void lambda$showPositionDetail$29(Position p, DialogInterface d, int w) {
        openUrl("https://solscan.io/token/" + p.mint);
    }

    private double liveExposureUsd(String book) {
        double x = 0.0d;
        for (LivePosition p : this.db.openLivePositions()) {
            if (book.equals(p.book)) {
                double units = p.remainingRaw / Math.pow(10.0d, p.decimals);
                x += p.lastPriceUsd * units;
            }
        }
        return x;
    }

    private LinearLayout livePositionCard(final LivePosition p) {
        double units = p.remainingRaw / Math.pow(10.0d, p.decimals);
        double mtm = p.lastPriceUsd * units;
        double pnl = (p.realizedUsd + mtm) - p.stakeUsd;
        double roi = p.stakeUsd > 0.0d ? (100.0d * pnl) / p.stakeUsd : 0.0d;
        double x = p.entryPriceUsd > 0.0d ? p.lastPriceUsd / p.entryPriceUsd : 0.0d;
        LinearLayout box = card();
        box.setBackground(bg(Color.rgb(18, 13, 14), 20.0f, Color.rgb(105, 35, 44), 1));
        LinearLayout head = row();
        TextView nm = text((p.symbol == null || p.symbol.isEmpty()) ? shortAddr(p.mint) : p.symbol, 16.0f, TEXT, true);
        head.addView(nm, new LinearLayout.LayoutParams(0, -2, 1.0f));
        head.addView(pill("LIVE " + String.format(Locale.US, "%+.1f%%", Double.valueOf(roi)), pnl >= 0.0d ? GREEN_DARK : Color.rgb(76, 20, 29), pnl >= 0.0d ? GREEN : RED));
        box.addView(head);
        box.addView(text(displayBook(p.book) + " · REAL MONEY · " + duration(p.entryTime), 11.0f, RED, true));
        LinearLayout stats = row();
        addWeight(stats, miniStat("ENTRY", "$" + Db.fmt(p.entryPriceUsd)));
        addWeight(stats, miniStat("NOW", "$" + Db.fmt(p.lastPriceUsd)));
        addWeight(stats, miniStat("X", String.format(Locale.US, "%.2fx", Double.valueOf(x))));
        box.addView(stats);
        LinearLayout stats2 = row();
        addWeight(stats2, miniStat("STAKE", String.format(Locale.US, "$%.2f", Double.valueOf(p.stakeUsd))));
        addWeight(stats2, miniStat("MTM P/L", String.format(Locale.US, "$%+,.2f", Double.valueOf(pnl))));
        addWeight(stats2, miniStat("FLOOR", String.format(Locale.US, "%.2fx", Double.valueOf(p.floorX))));
        box.addView(stats2);
        TextView rule = text("High " + String.format(Locale.US, "%.2fx", Double.valueOf(p.highX)) + " · runner " + (p.trailActive ? "ON @ " + String.format(Locale.US, "%.2fx", Double.valueOf(p.trailPeakX)) : "off") + " · realized $" + String.format(Locale.US, "%.2f", Double.valueOf(p.realizedUsd)), 10.0f, MUTED, false);
        rule.setPadding(0, dp(8), 0, dp(5));
        box.addView(rule);
        LinearLayout buttons = row();
        Button token = smallAction("COPY TOKEN");
        Button buytx = smallAction("BUY TX");
        Button scan = smallAction("SOLSCAN");
        addWeight(buttons, token);
        addWeight(buttons, buytx);
        addWeight(buttons, scan);
        box.addView(buttons);
        token.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda41
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$livePositionCard$30(p, view);
            }
        });
        buytx.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda42
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$livePositionCard$31(p, view);
            }
        });
        scan.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda43
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$livePositionCard$32(p, view);
            }
        });
        if ("OPEN".equals(p.state)) {
            LinearLayout exitRow = row();
            Button checkExit = smallAction("CHECK EXIT QUOTE");
            Button closeLive = smallAction("CLOSE LIVE");
            closeLive.setTextColor(RED);
            addWeight(exitRow, checkExit);
            addWeight(exitRow, closeLive);
            box.addView(exitRow);
            checkExit.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda45
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.lambda$livePositionCard$33(p, view);
                }
            });
            closeLive.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda46
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.lambda$livePositionCard$34(p, view);
                }
            });
        }
        return box;
    }

    public /* synthetic */ void lambda$livePositionCard$30(LivePosition p, View v) {
        copy("Token mint", p.mint);
    }

    public /* synthetic */ void lambda$livePositionCard$31(LivePosition p, View v) {
        if (p.buySig == null || p.buySig.isEmpty()) {
            toast("No signature recorded.");
        } else {
            copy("Buy signature", p.buySig);
        }
    }

    public /* synthetic */ void lambda$livePositionCard$32(LivePosition p, View v) {
        openUrl("https://solscan.io/token/" + p.mint);
    }

    public /* synthetic */ void lambda$livePositionCard$33(LivePosition p, View v) {
        previewLiveExit(p);
    }

    public /* synthetic */ void lambda$livePositionCard$34(LivePosition p, View v) {
        confirmLiveClose(p);
    }

    private void previewPaperExit(final Position p) {
        toast("Calculating exitability…");
        new Thread(new Runnable() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda60
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$previewPaperExit$36(p);
            }
        }).start();
    }

    public /* synthetic */ void lambda$previewPaperExit$36(Position p) {
        final String s = this.paperEngine.previewExit(p.id);
        runOnUiThread(new Runnable() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$previewPaperExit$35(s);
            }
        });
    }

    public /* synthetic */ void lambda$previewPaperExit$35(String s) {
        new AlertDialog.Builder(this).setTitle("Paper exitability").setMessage(s).setPositiveButton("OK", (DialogInterface.OnClickListener) null).show();
    }

    private void confirmPaperClose(final Position p) {
        new AlertDialog.Builder(this).setTitle("Close PAPER position?").setMessage((p.symbol == null ? "Token" : p.symbol) + " will be closed immediately using the current market mark and a liquidity-aware constant-product exit approximation.").setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).setPositiveButton("CLOSE PAPER", new DialogInterface.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda59
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.lambda$confirmPaperClose$39(p, dialogInterface, i);
            }
        }).show();
    }

    public /* synthetic */ void lambda$confirmPaperClose$39(final Position p, DialogInterface d, int w) {
        new Thread(new Runnable() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda4
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$confirmPaperClose$38(p);
            }
        }).start();
    }

    public /* synthetic */ void lambda$confirmPaperClose$38(Position p) {
        final String result = this.paperEngine.manualClose(p.id);
        runOnUiThread(new Runnable() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda58
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$confirmPaperClose$37(result);
            }
        });
    }

    public /* synthetic */ void lambda$confirmPaperClose$37(String result) {
        toast(result);
        refresh();
    }

    private void previewLiveExit(final LivePosition p) {
        toast("Requesting executable exit quote…");
        new Thread(new Runnable() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda48
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$previewLiveExit$41(p);
            }
        }).start();
    }

    public /* synthetic */ void lambda$previewLiveExit$41(LivePosition p) {
        final String s = this.liveTrader.previewExit(p.id);
        runOnUiThread(new Runnable() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda49
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$previewLiveExit$40(s);
            }
        });
    }

    public /* synthetic */ void lambda$previewLiveExit$40(String s) {
        new AlertDialog.Builder(this).setTitle("LIVE exit quote").setMessage(s).setPositiveButton("OK", (DialogInterface.OnClickListener) null).show();
    }

    private void confirmLiveClose(final LivePosition p) {
        new AlertDialog.Builder(this).setTitle("Close REAL-MONEY position?").setMessage((p.symbol == null ? "Token" : p.symbol) + "\n\nThis will request a fresh executable sell quote, locally sign it with the encrypted bot wallet, broadcast it, and attempt to sell the ENTIRE remaining position. This action moves real funds.").setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).setPositiveButton("SELL ALL NOW", new DialogInterface.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda1
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.lambda$confirmLiveClose$44(p, dialogInterface, i);
            }
        }).show();
    }

    public /* synthetic */ void lambda$confirmLiveClose$44(final LivePosition p, DialogInterface d, int w) {
        new Thread(new Runnable() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda57
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$confirmLiveClose$43(p);
            }
        }).start();
    }

    public /* synthetic */ void lambda$confirmLiveClose$43(LivePosition p) {
        final String result = this.liveTrader.manualClose(p.id);
        runOnUiThread(new Runnable() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda55
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$confirmLiveClose$42(result);
            }
        });
    }

    public /* synthetic */ void lambda$confirmLiveClose$42(String result) {
        new AlertDialog.Builder(this).setTitle("LIVE close result").setMessage(result).setPositiveButton("OK", (DialogInterface.OnClickListener) null).show();
        refresh();
    }

    private LinearLayout budgetCard(String provider, String title) {
        ApiBudget.Snapshot s = this.apiBudget.snapshot(provider);
        LinearLayout box = card();
        box.addView(text(title, 14.0f, TEXT, true));
        EditText monthly = input(String.valueOf(s.monthly), false, "Monthly calls");
        EditText rpm = input(String.valueOf(s.planPerMinute), false, "Hard requests/min");
        EditText reset = input(String.valueOf(s.resetDay <= 0 ? 1 : s.resetDay), false, "Reset day");
        EditText reserve = input(String.valueOf(s.reservePct), false, "Reserve %");
        EditText used = input(String.valueOf(s.used), false, "Calls used this cycle");
        box.addView(twoFields("Monthly allowance", monthly, "Provider hard RPM", rpm));
        box.addView(twoFields("Billing reset day (1–28)", reset, "Reserve % kept unused", reserve));
        box.addView(fieldCard("Already used this billing cycle", used));
        TextView calc = text(s.summary(), 10.0f, MUTED, false);
        box.addView(calc);
        this.budgetInputs.put(provider, new EditText[]{monthly, rpm, reset, reserve, used});
        if ("COINGECKO".equals(provider)) {
            Button sync = action("SYNC COINGECKO PLAN / USAGE", false);
            sync.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda6
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.lambda$budgetCard$45(view);
                }
            });
            box.addView(sync);
        }
        return box;
    }

    public /* synthetic */ void lambda$budgetCard$45(View v) {
        syncCoinGeckoBudget();
    }

    private void syncCoinGeckoBudget() {
        try {
            saveLiveSettingsOnly();
            saveBudgetInputs();
            this.budgetStatus.setText("Syncing CoinGecko plan…");
            new Thread(new Runnable() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda53
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$syncCoinGeckoBudget$47();
                }
            }).start();
        } catch (Exception e) {
            toast("Save CoinGecko settings first: " + e.getMessage());
        }
    }

    public /* synthetic */ void lambda$syncCoinGeckoBudget$47() {
        final String result = this.net.syncCoinGeckoPlan();
        runOnUiThread(new Runnable() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda62
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$syncCoinGeckoBudget$46(result);
            }
        });
    }

    public /* synthetic */ void lambda$syncCoinGeckoBudget$46(String result) {
        EditText[] a = this.budgetInputs.get("COINGECKO");
        ApiBudget.Snapshot s = this.apiBudget.snapshot("COINGECKO");
        if (a != null) {
            a[0].setText(String.valueOf(s.monthly));
            a[1].setText(String.valueOf(s.planPerMinute));
            a[4].setText(String.valueOf(s.used));
        }
        this.budgetStatus.setText(result + "\n" + s.summary());
    }

    private void generateBotWallet() {
        new AlertDialog.Builder(this).setTitle("Generate dedicated LIVE bot wallet?").setMessage("This wallet can hold and move real SOL. Keep only the amount you intend to trade here. The private key will be encrypted with Android Keystore on this phone. Back it up before funding.").setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).setPositiveButton("Generate", new DialogInterface.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda63
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.lambda$generateBotWallet$48(dialogInterface, i);
            }
        }).show();
    }

    public /* synthetic */ void lambda$generateBotWallet$48(DialogInterface d, int w) {
        try {
            String pub = this.botWallet.generate();
            Prefs.setLiveArmed(this, false);
            toast("Bot wallet created. Back it up before funding.\n" + pub);
            refresh();
        } catch (Exception e) {
            toast("Wallet generation failed: " + e.getMessage());
        }
    }

    private void importBotWallet() {
        final EditText secret = input("", true, "Base58 secret key");
        secret.setInputType(129);
        new AlertDialog.Builder(this).setTitle("Import dedicated Solana bot wallet").setMessage("Enter it only here in the app. Do not use your main wallet or paste the secret into chat.").setView(secret).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).setPositiveButton("Import", new DialogInterface.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda5
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.lambda$importBotWallet$49(secret, dialogInterface, i);
            }
        }).show();
    }

    public /* synthetic */ void lambda$importBotWallet$49(EditText secret, DialogInterface d, int w) {
        try {
            String pub = this.botWallet.importBase58(secret.getText().toString().trim());
            Prefs.setLiveArmed(this, false);
            toast("Bot wallet imported: " + shortAddr(pub));
            refresh();
        } catch (Exception e) {
            toast("Import failed: " + e.getMessage());
        }
    }

    private void exportBotWallet() {
        if (this.botWallet.exists()) {
            new AlertDialog.Builder(this).setTitle("Reveal private key backup?").setMessage("Anyone with this key can spend the bot wallet. Make sure nobody can see your screen.").setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).setPositiveButton("Reveal", new DialogInterface.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda50
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.this.lambda$exportBotWallet$51(dialogInterface, i);
                }
            }).show();
        } else {
            toast("No bot wallet stored.");
        }
    }

    public /* synthetic */ void lambda$exportBotWallet$51(DialogInterface d, int w) {
        try {
            final String secret = this.botWallet.exportBase58();
            TextView v = text(secret, 12.0f, TEXT, false);
            v.setTextIsSelectable(true);
            v.setPadding(dp(16), dp(12), dp(16), dp(12));
            new AlertDialog.Builder(this).setTitle("Bot wallet secret — BACK UP SECURELY").setView(v).setNegativeButton("Close", (DialogInterface.OnClickListener) null).setPositiveButton("Copy", new DialogInterface.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda64
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.this.lambda$exportBotWallet$50(secret, dialogInterface, i);
                }
            }).show();
        } catch (Exception e) {
            toast("Export failed: " + e.getMessage());
        }
    }

    public /* synthetic */ void lambda$exportBotWallet$50(String secret, DialogInterface dd, int ww) {
        copy("BOT WALLET SECRET", secret);
    }

    private void armLive() {
        try {
            saveLiveSettingsOnly();
            if (!this.botWallet.exists()) {
                toast("Create/import the dedicated bot wallet first.");
                return;
            }
            if (Prefs.swapApi(this).trim().isEmpty()) {
                toast("Add your QuickNode Swap API endpoint first.");
                return;
            }
            if (Prefs.helius(this).trim().isEmpty()) {
                toast("Helius is required for LIVE broadcast and confirmation.");
                return;
            }
            boolean any = "LIVE".equals(this.rayModeValue) || "LIVE".equals(this.psModeValue) || "LIVE".equals(this.pfModeValue);
            if (!any) {
                toast("Select LIVE on at least one venue first.");
                switchPage(2);
                return;
            }
            String msg = "This will allow Meme Tail Lab to send REAL SOL transactions automatically for venues set to LIVE.\n\nMax trade: $" + String.format(Locale.US, "%.2f", Double.valueOf(Prefs.liveMaxTradeUsd(this))) + "\nMax total exposure: $" + String.format(Locale.US, "%.2f", Double.valueOf(Prefs.liveMaxExposureUsd(this))) + "\nDaily realized-loss cutoff: $" + String.format(Locale.US, "%.2f", Double.valueOf(Prefs.liveDailyLossUsd(this))) + "\n\nProtective exits continue even after emergency disarm.";
            new AlertDialog.Builder(this).setTitle("ARM REAL-MONEY AUTO TRADING?").setMessage(msg).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).setPositiveButton("ARM LIVE", new DialogInterface.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda52
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.this.lambda$armLive$52(dialogInterface, i);
                }
            }).show();
        } catch (Exception e) {
            toast("LIVE setup error: " + e.getMessage());
        }
    }

    public /* synthetic */ void lambda$armLive$52(DialogInterface d, int w) {
        Prefs.setLiveArmed(this, true);
        service("com.memetaillab.beta1.START");
        toast("LIVE AUTO ARMED.");
        refresh();
    }

    private void saveLiveSettingsOnly() {
        String swapApi = this.swapApi == null ? Prefs.swapApi(this) : this.swapApi.getText().toString();
        String coinGeckoKey = this.coinGeckoKey == null ? Prefs.coinGeckoKey(this) : this.coinGeckoKey.getText().toString();
        boolean z = false;
        boolean z2 = this.coinGeckoPro != null && this.coinGeckoPro.isChecked();
        if (this.coinGeckoEnabled != null && this.coinGeckoEnabled.isChecked()) {
            z = true;
        }
        Prefs.saveLive(this, swapApi, coinGeckoKey, z2, z, this.liveRayAlloc == null ? Prefs.liveAllocationUsd(this, "RAYDIUM") : dv(this.liveRayAlloc), this.livePsAlloc == null ? Prefs.liveAllocationUsd(this, "PUMPSWAP") : dv(this.livePsAlloc), this.livePfAlloc == null ? Prefs.liveAllocationUsd(this, "PUMPFUN") : dv(this.livePfAlloc), this.liveSize == null ? Prefs.liveSizePct(this) : dv(this.liveSize), this.liveMaxTrade == null ? Prefs.liveMaxTradeUsd(this) : dv(this.liveMaxTrade), this.liveMaxExposure == null ? Prefs.liveMaxExposureUsd(this) : dv(this.liveMaxExposure), this.liveDailyLoss == null ? Prefs.liveDailyLossUsd(this) : dv(this.liveDailyLoss), this.liveSlippage == null ? Prefs.liveSlippageBps(this) : iv(this.liveSlippage), this.liveMaxImpact == null ? Prefs.liveMaxImpactPct(this) : dv(this.liveMaxImpact), this.livePriorityLamports == null ? Prefs.livePriorityMaxLamports(this) : lv(this.livePriorityLamports), Prefs.livePriorityLevel(this));
    }

    private void saveBudgetInputs() {
        String[] strArr = {"HELIUS", "COINGECKO", "TRADING"};
        for (int i = 0; i < 3; i++) {
            String p = strArr[i];
            EditText[] a = this.budgetInputs.get(p);
            if (a != null) {
                this.apiBudget.configure(p, lv(a[0]), iv(a[1]), iv(a[2]), iv(a[3]), lv(a[4]));
            }
        }
    }

    private void renderDelayModes() {
        if (this.delayModeRow == null) {
            return;
        }
        this.delayModeRow.removeAllViews();
        for (final String m : Config.DELAY_MODES) {
            boolean active = m.equals(this.delayModeValue);
            TextView b = text(m.replace(" ", "\n"), 10.0f, active ? -16777216 : MUTED, true);
            b.setGravity(17);
            b.setBackground(bg(active ? GREEN : SURFACE, 14.0f, active ? GREEN : BORDER, 1));
            b.setPadding(dp(4), dp(7), dp(4), dp(7));
            b.setOnClickListener(new View.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda17
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.lambda$renderDelayModes$53(m, view);
                }
            });
            this.delayModeRow.addView(b, new LinearLayout.LayoutParams(0, dp(58), 1.0f));
        }
    }

    public /* synthetic */ void lambda$renderDelayModes$53(String m, View v) {
        this.delayModeValue = m;
        renderDelayModes();
    }

    private void saveSettings() {
        try {
            Prefs.save(this, this.rpc.getText().toString(), this.helius.getText().toString(), this.wallet.getText().toString(), this.rayApi.getText().toString(), this.raySwap.getText().toString(), iv(this.scanSec), iv(this.markSec), this.delayModeValue, iv(this.delaySec), dv(this.sizePct), iv(this.entryBps), iv(this.exitBps), dv(this.fixedFee), dv(this.minLiq), iv(this.minTrades), iv(this.minBuyers), dv(this.minRatio), dv(this.minMomentum), dv(this.minAge), dv(this.maxAge), iv(this.maxOpen), this.rayModeValue, this.psModeValue, this.pfModeValue, this.fastStreamToggle.isChecked());
            saveLiveSettingsOnly();
            saveBudgetInputs();
            Intent si = new Intent(this, (Class<?>) TrackerService.class).setAction("com.memetaillab.beta1.STREAM_RESTART");
            startForegroundService(si);
            toast("Settings, API budgets and LIVE risk limits saved.");
            refresh();
        } catch (Exception e) {
            toast("Check settings: " + e.getMessage());
        }
    }

    private void previewExecution() {
        final String in = this.exInput.getText().toString().trim();
        final String out = this.exOutput.getText().toString().trim();
        if (in.length() < 32 || out.length() < 32) {
            toast("Enter valid input and output mint addresses.");
            return;
        }
        try {
            final long amt = Long.parseLong(this.exAmount.getText().toString().trim());
            final int slip = Integer.parseInt(this.exSlip.getText().toString().trim());
            this.execResult.setText("Building Raydium quote…");
            new Thread(new Runnable() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda10
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$previewExecution$55(in, out, amt, slip);
                }
            }).start();
        } catch (Exception e) {
            toast("Check raw amount / slippage.");
        }
    }

    public /* synthetic */ void lambda$previewExecution$54(String s) {
        this.execResult.setText(s);
    }

    public /* synthetic */ void lambda$previewExecution$55(String in, String out, long amt, int slip) {
        ExecPreview p = this.exec.preview(in, out, amt, slip);
        final String s = p.message + "\n\nQUOTE\n" + p.quoteJson + "\n\nTRANSACTION BUILD\n" + p.transactionJson;
        runOnUiThread(new Runnable() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda47
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$previewExecution$54(s);
            }
        });
    }

    private void useLatestRaydium() {
        for (Position p : this.db.recentPositions(100)) {
            if ("RAYDIUM".equals(p.book)) {
                this.exOutput.setText(p.mint);
                toast("Loaded " + p.symbol + " token mint.");
                return;
            }
        }
        toast("No Raydium position yet.");
    }

    private void confirmBroadcast() {
        final String s = this.signedTx.getText().toString().trim();
        if (s.isEmpty()) {
            toast("Paste an externally signed base64 transaction first.");
        } else {
            new AlertDialog.Builder(this).setTitle("Broadcast real signed transaction?").setMessage("This can move real funds. The app will only send the transaction you already signed externally.").setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).setPositiveButton("Broadcast", new DialogInterface.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda14
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.this.lambda$confirmBroadcast$59(s, dialogInterface, i);
                }
            }).show();
        }
    }

    public /* synthetic */ void lambda$confirmBroadcast$59(final String s, DialogInterface d, int w) {
        this.execResult.setText("Broadcasting…");
        new Thread(new Runnable() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda2
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$confirmBroadcast$58(s);
            }
        }).start();
    }

    public /* synthetic */ void lambda$confirmBroadcast$56(String sig) {
        this.execResult.setText("Broadcast submitted:\n" + sig);
    }

    public /* synthetic */ void lambda$confirmBroadcast$58(String s) {
        try {
            final String sig = this.exec.broadcastSignedBase64(s);
            runOnUiThread(new Runnable() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda18
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$confirmBroadcast$56(sig);
                }
            });
        } catch (Exception e) {
            runOnUiThread(new Runnable() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda19
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$confirmBroadcast$57(e);
                }
            });
        }
    }

    public /* synthetic */ void lambda$confirmBroadcast$57(Exception e) {
        this.execResult.setText("Broadcast failed: " + e.getMessage());
    }

    private void exportCsv() {
        new Thread(new Runnable() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda16
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$exportCsv$62();
            }
        }).start();
    }

    public /* synthetic */ void lambda$exportCsv$60(String names) {
        toast("Saved to Downloads/MemeTailBeta1\n" + names);
    }

    public /* synthetic */ void lambda$exportCsv$62() {
        try {
            final String names = CsvExporter.export(this, this.db);
            runOnUiThread(new Runnable() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda22
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$exportCsv$60(names);
                }
            });
        } catch (Exception e) {
            runOnUiThread(new Runnable() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda33
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$exportCsv$61(e);
                }
            });
        }
    }

    public /* synthetic */ void lambda$exportCsv$61(Exception e) {
        toast("Export failed: " + e.getMessage());
    }

    private void confirmReset() {
        new AlertDialog.Builder(this).setTitle("Reset paper data?").setMessage("Deletes all signals, positions, fills and equity history, then restores all three paper books to $1,000.").setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).setPositiveButton("RESET", new DialogInterface.OnClickListener() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda15
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.lambda$confirmReset$63(dialogInterface, i);
            }
        }).show();
    }

    public /* synthetic */ void lambda$confirmReset$63(DialogInterface d, int w) {
        this.db.resetPaper();
        toast("Paper accounts reset.");
        refresh();
    }

    private void service(String a) {
        Intent i = new Intent(this, (Class<?>) TrackerService.class).setAction(a);
        if ("com.memetaillab.beta1.STOP".equals(a)) {
            startService(i);
        } else {
            startForegroundService(i);
        }
        Prefs.setTracking(this, !"com.memetaillab.beta1.STOP".equals(a));
        this.h.postDelayed(new Runnable() { // from class: com.memetaillab.beta1.MainActivity$$ExternalSyntheticLambda56
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.refresh();
            }
        }, 700L);
    }

    private void switchPage(int idx) {
        this.pageIndex = idx;
        int i = 0;
        while (i < this.pages.length) {
            this.pages[i].setVisibility(i == idx ? 0 : 8);
            this.nav[i].setTextColor(i == idx ? GREEN : MUTED);
            this.nav[i].setBackground(i == idx ? bg(GREEN_DARK, 14.0f, 0, 0) : null);
            i++;
        }
        refresh();
    }

    private LinearLayout wrap(LinearLayout body) {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(body, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(1);
        holder.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        return holder;
    }

    private LinearLayout pageRoot() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(1);
        l.setPadding(dp(14), dp(6), dp(14), dp(28));
        l.setBackgroundColor(BG);
        return l;
    }

    private LinearLayout titleBlock(String title, String sub) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(1);
        l.setPadding(0, dp(5), 0, dp(12));
        l.addView(text(title, 25.0f, TEXT, true));
        l.addView(text(sub, 11.0f, MUTED, false));
        return l;
    }

    private TextView sectionLabel(String s) {
        TextView t = text(s, 15.0f, TEXT, true);
        t.setPadding(dp(2), dp(18), dp(2), dp(7));
        return t;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(1);
        l.setPadding(dp(14), dp(13), dp(14), dp(13));
        l.setBackground(bg(SURFACE, 20.0f, BORDER, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        l.setLayoutParams(lp);
        return l;
    }

    private LinearLayout emptyCard(String s) {
        LinearLayout l = card();
        l.addView(text(s, 13.0f, MUTED, false));
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(0);
        l.setGravity(16);
        return l;
    }

    private void addWeight(LinearLayout l, View v) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1.0f);
        p.setMargins(dp(3), 0, dp(3), 0);
        l.addView(v, p);
    }

    private TextView metric(String label, String val) {
        TextView t = text(label + "\n" + val, 12.0f, MUTED, true);
        t.setGravity(17);
        t.setPadding(dp(5), dp(10), dp(5), dp(10));
        t.setBackground(bg(SURFACE_2, 14.0f, BORDER, 1));
        return t;
    }

    private void setMetric(TextView t, String label, String val, int color) {
        t.setText(label + "\n" + val);
        t.setTextColor(color);
    }

    private TextView statTile(String label, String value, int color) {
        TextView t = text(label + "\n" + value, 12.0f, color, true);
        t.setGravity(17);
        t.setPadding(dp(6), dp(10), dp(6), dp(10));
        t.setBackground(bg(SURFACE, 14.0f, BORDER, 1));
        return t;
    }

    private TextView miniStat(String label, String value) {
        TextView t = text(label + "\n" + value, 10.0f, TEXT, true);
        t.setGravity(17);
        t.setPadding(dp(3), dp(8), dp(3), dp(8));
        return t;
    }

    private Button action(String s, boolean primary) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(11.0f);
        b.setAllCaps(false);
        b.setTextColor(primary ? -16777216 : TEXT);
        b.setTypeface(Typeface.DEFAULT, 1);
        b.setBackground(bg(primary ? GREEN : SURFACE_2, 16.0f, primary ? GREEN : BORDER, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.setMargins(dp(3), dp(5), dp(3), dp(5));
        b.setLayoutParams(lp);
        return b;
    }

    private Button smallAction(String s) {
        Button b = action(s, false);
        b.setTextSize(9.0f);
        return b;
    }

    private EditText input(String value, boolean multiline, String hint) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(91, 108, 101));
        e.setTextColor(TEXT);
        e.setTextSize(12.0f);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setBackground(bg(SURFACE_2, 14.0f, BORDER, 1));
        e.setSingleLine(!multiline);
        if (multiline) {
            e.setInputType(131073);
        }
        return e;
    }

    private LinearLayout fieldCard(String label, EditText e) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(1);
        l.setPadding(0, 0, 0, dp(8));
        l.addView(text(label, 10.0f, MUTED, true));
        l.addView(e, new LinearLayout.LayoutParams(-1, -2));
        return l;
    }

    private LinearLayout twoFields(String a, EditText av, String b, EditText bv) {
        LinearLayout row = row();
        LinearLayout aa = fieldCard(a, av);
        LinearLayout bb = fieldCard(b, bv);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1.0f);
        p.setMargins(dp(2), 0, dp(2), 0);
        row.addView(aa, p);
        row.addView(bb, p);
        return row;
    }

    private TextView text(String str, float f, int i, boolean z) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextColor(i);
        textView.setTextSize(f);
        textView.setTypeface(Typeface.DEFAULT, z ? 1 : 0);
        return textView;
    }

    private TextView pill(String s, int fill, int color) {
        TextView t = text(s, 9.0f, color, true);
        t.setGravity(17);
        t.setPadding(dp(9), dp(5), dp(9), dp(5));
        t.setBackground(bg(fill, 999.0f, color == GREEN ? Color.rgb(22, 103, 69) : BORDER, 1));
        return t;
    }

    private GradientDrawable bg(int color, float radius, int stroke, int strokeWidth) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp((int) radius));
        if (strokeWidth > 0) {
            g.setStroke(dp(strokeWidth), stroke);
        }
        return g;
    }

    private void openBattery() {
        try {
            startActivity(new Intent("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent("android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"));
        }
    }

    private void openUrl(String u) {
        try {
            startActivity(new Intent("android.intent.action.VIEW", Uri.parse(u)));
        } catch (Exception e) {
            toast("Could not open browser.");
        }
    }

    private void copy(String label, String value) {
        ClipboardManager cm = (ClipboardManager) getSystemService("clipboard");
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, value));
            toast(label + " copied.");
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, 1).show();
    }

    private int iv(EditText e) {
        return Integer.parseInt(e.getText().toString().trim());
    }

    private double dv(EditText e) {
        return Double.parseDouble(e.getText().toString().trim());
    }

    private long lv(EditText e) {
        return Long.parseLong(e.getText().toString().trim());
    }

    private int dp(int x) {
        return (int) ((x * getResources().getDisplayMetrics().density) + 0.5f);
    }

    private String displayBook(String b) {
        return "PUMPSWAP".equals(b) ? "PumpSwap" : "PUMPFUN".equals(b) ? "Pump.fun" : "Raydium";
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String shortAddr(String s) {
        return s == null ? "" : s.length() > 14 ? s.substring(0, 7) + "…" + s.substring(s.length() - 5) : s;
    }

    private String age(long ms) {
        long s = Math.max(0L, ms / 1000);
        if (s < 60) {
            return s + "s ago";
        }
        long m = s / 60;
        return m < 60 ? m + "m ago" : (m / 60) + "h " + (m % 60) + "m ago";
    }

    private String shortAge(long ms) {
        StringBuilder append;
        String str;
        long s = Math.max(0L, ms / 1000);
        if (s < 60) {
            append = new StringBuilder().append(s);
            str = "s";
        } else {
            append = new StringBuilder().append(s / 60);
            str = "m";
        }
        return append.append(str).toString();
    }

    private String duration(long start) {
        long s = Math.max(0L, (System.currentTimeMillis() - start) / 1000);
        long h = s / 3600;
        long m = (s % 3600) / 60;
        return h + "h " + m + "m";
    }
}
