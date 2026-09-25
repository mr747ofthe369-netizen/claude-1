package com.memetaillab.beta1;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;

public class BacktestActivity extends Activity implements View.OnClickListener {
    private static final int PICK_PROFILE = 7401;
    private static final String PREFS = "mtl_beta1";
    private static final int ID_LOAD = 1001;
    private static final int ID_RETURN = 1002;

    private TextView status;
    private TextView current;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(5, 10, 8));
        getWindow().setNavigationBarColor(Color.rgb(5, 10, 8));

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, dp(40));
        root.setBackgroundColor(Color.rgb(5, 10, 8));
        scroll.addView(root);

        TextView title = text("MemeGPT Delta · Research Parameters", 27, Color.rgb(241,247,244), true);
        root.addView(title);

        TextView intro = text(
                "Historical backtesting and optimisation have moved out of MemeGPT Delta. " +
                "Use the dedicated Research Lab to build, backtest and optimise a frozen historical dataset, " +
                "then load its parameter profile here.\n\n" +
                "Loading a profile changes strategy parameters only. API credentials, wallets and provider settings are not replaced. " +
                "MASTER LIVE is automatically disarmed so you can review the new profile before re-arming.",
                15, Color.rgb(170,188,179), false);
        intro.setPadding(0, dp(12), 0, dp(20));
        root.addView(intro);

        Button load = button("LOAD PARAMETER SETTINGS FILE");
        load.setId(ID_LOAD);
        load.setOnClickListener(this);
        root.addView(load);

        current = text("", 14, Color.rgb(205,219,212), false);
        current.setTypeface(Typeface.MONOSPACE);
        current.setPadding(0, dp(22), 0, dp(10));
        root.addView(current);

        status = text("No Research Lab profile loaded in this session.", 14, Color.rgb(255,190,74), true);
        status.setMovementMethod(new ScrollingMovementMethod());
        status.setPadding(0, dp(12), 0, dp(18));
        root.addView(status);

        Button back = button("RETURN TO MEMEGPT");
        back.setId(ID_RETURN);
        back.setOnClickListener(this);
        root.addView(back);

        TextView schema = text(
                "Accepted file: JSON\n" +
                "format: MemeGPT-Research-Parameters\n" +
                "schema: 1\n" +
                "Required: global parameter object\n" +
                "Optional: per-venue research blocks for RAYDIUM, PUMPSWAP, PUMPFUN and METEORA. " +
                "Per-venue values are preserved for forward compatibility; the current live engine continues to use the global profile.",
                12, Color.rgb(126,149,139), false);
        schema.setPadding(0, dp(18), 0, 0);
        root.addView(schema);

        setContentView(scroll);
        refreshCurrent();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == ID_LOAD) {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            startActivityForResult(i, PICK_PROFILE);
        } else if (v.getId() == ID_RETURN) {
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_PROFILE || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            String raw = readAll(uri);
            JSONObject root = new JSONObject(raw);
            applyProfile(root, raw);
            status.setText("PROFILE LOADED SUCCESSFULLY\nMASTER LIVE: DISARMED\n\n" + profileSummary(root));
            status.setTextColor(Color.rgb(46,242,161));
            refreshCurrent();
            Toast.makeText(this, "Research parameters loaded. LIVE is disarmed.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            status.setText("PROFILE NOT APPLIED\n" + safeMessage(e));
            status.setTextColor(Color.rgb(255,94,111));
        }
    }

    private void applyProfile(JSONObject root, String raw) throws Exception {
        String format = root.optString("format", "");
        int schema = root.optInt("schema", -1);
        if (!"MemeGPT-Research-Parameters".equals(format)) {
            throw new IllegalArgumentException("Wrong profile format. Expected MemeGPT-Research-Parameters.");
        }
        if (schema != 1) {
            throw new IllegalArgumentException("Unsupported parameter schema: " + schema);
        }
        JSONObject g = root.optJSONObject("global");
        if (g == null) {
            throw new IllegalArgumentException("Missing global parameter object.");
        }

        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        SharedPreferences.Editor e = sp.edit();

        putInt(g, e, "minTrades", 0, 100000);
        putInt(g, e, "minBuyers", 0, 100000);
        putDouble(g, e, "minRatio", 0.0, 1000.0);
        putDouble(g, e, "minMomentum", -1000.0, 1000000.0);
        putDouble(g, e, "minLiq", 0.0, 1.0e12);
        putDouble(g, e, "minAge", 0.0, 1000000.0);
        putDouble(g, e, "maxAge", 0.0, 1000000.0);
        putDouble(g, e, "sizePct", 0.01, 100.0);
        putInt(g, e, "entryBps", 0, 100000);
        putInt(g, e, "exitBps", 0, 100000);
        putDouble(g, e, "fixedFeeUsd", 0.0, 1000000.0);
        putInt(g, e, "maxOpen", 1, 100000);

        if (g.has("delayMode")) {
            String mode = g.optString("delayMode", "").trim();
            if (!mode.isEmpty()) e.putString("delayMode", mode);
        }
        if (g.has("delaySec")) {
            int x = g.getInt("delaySec");
            if (x < 0 || x > 86400) throw new IllegalArgumentException("delaySec outside safe range.");
            e.putInt("delaySec", x);
        }

        JSONObject venues = root.optJSONObject("venues");
        if (venues != null) {
            storeVenueBlock(venues, e, "RAYDIUM");
            storeVenueBlock(venues, e, "PUMPSWAP");
            storeVenueBlock(venues, e, "PUMPFUN");
            storeVenueBlock(venues, e, "METEORA");
        }

        e.putString("researchLabProfileJson", raw);
        e.putString("researchLabProfileName", root.optString("profile_name", "Research Lab profile"));
        e.putLong("researchLabProfileLoadedAt", System.currentTimeMillis());
        e.putBoolean("liveArmed", false);
        e.apply();
    }

    private void storeVenueBlock(JSONObject venues, SharedPreferences.Editor e, String venue) throws Exception {
        JSONObject v = venues.optJSONObject(venue);
        if (v == null) return;
        Iterator<String> keys = v.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            Object value = v.get(k);
            String pref = "research_" + venue + "_" + k;
            if (value instanceof Boolean) {
                e.putBoolean(pref, (Boolean) value);
            } else if (value instanceof Integer) {
                e.putInt(pref, (Integer) value);
            } else if (value instanceof Long) {
                e.putLong(pref, (Long) value);
            } else if (value instanceof Number) {
                e.putString(pref, String.valueOf(((Number) value).doubleValue()));
            } else if (value != null) {
                e.putString(pref, String.valueOf(value));
            }
        }
    }

    private void putInt(JSONObject g, SharedPreferences.Editor e, String key, int min, int max) throws Exception {
        if (!g.has(key)) return;
        int x = g.getInt(key);
        if (x < min || x > max) throw new IllegalArgumentException(key + " outside safe range.");
        e.putInt(key, x);
    }

    private void putDouble(JSONObject g, SharedPreferences.Editor e, String key, double min, double max) throws Exception {
        if (!g.has(key)) return;
        double x = g.getDouble(key);
        if (!Double.isFinite(x) || x < min || x > max) throw new IllegalArgumentException(key + " outside safe range.");
        e.putString(key, String.valueOf(x));
    }

    private String readAll(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IllegalArgumentException("Could not open selected file.");
            byte[] buf = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(buf)) >= 0) {
                total += n;
                if (total > 2 * 1024 * 1024) throw new IllegalArgumentException("Parameter file is too large.");
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private void refreshCurrent() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String name = p.getString("researchLabProfileName", "none");
        current.setText(String.format(Locale.US,
                "CURRENT STRATEGY PROFILE\n" +
                "Research profile: %s\n" +
                "trades >= %d\n" +
                "buyers >= %d\n" +
                "buy/sell >= %s\n" +
                "momentum >= %s%%\n" +
                "min liquidity $%s\n" +
                "age %s - %s min\n" +
                "position size %s%%\n" +
                "entry/exit friction %d / %d bps\n" +
                "max open %d",
                name,
                p.getInt("minTrades", 20),
                p.getInt("minBuyers", 20),
                p.getString("minRatio", "1.1"),
                p.getString("minMomentum", "0.0"),
                p.getString("minLiq", "15000"),
                p.getString("minAge", "5.0"),
                p.getString("maxAge", "25.0"),
                p.getString("sizePct", "1.0"),
                p.getInt("entryBps", 150),
                p.getInt("exitBps", 200),
                p.getInt("maxOpen", 25)));
    }

    private String profileSummary(JSONObject root) {
        String name = root.optString("profile_name", "Research Lab profile");
        String generated = root.optString("generated_at", "not supplied");
        JSONObject o = root.optJSONObject("objective");
        String objective = o == null ? "not supplied" : o.optString("primary", "not supplied");
        return "Profile: " + name + "\nGenerated: " + generated + "\nObjective: " + objective +
                "\n\nStrategy parameters are active for PAPER/MONITOR and will be used by LIVE only after you deliberately re-arm MASTER LIVE.";
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setTextColor(Color.rgb(4,24,17));
        b.setBackgroundColor(Color.rgb(46,242,161));
        b.setAllCaps(false);
        b.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64));
        lp.setMargins(0, dp(8), 0, dp(8));
        b.setLayoutParams(lp);
        return b;
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private int dp(int x) {
        return (int) (x * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String safeMessage(Exception e) {
        String m = e.getMessage();
        return m == null || m.trim().isEmpty() ? e.getClass().getSimpleName() : m;
    }
}
