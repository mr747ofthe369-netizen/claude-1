package com.memetaillab.beta1;

import android.content.Context;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.json.JSONArray;
import org.json.JSONObject;

class FastStream {
    private final Context c;
    private final Listener listener;
    private volatile WebSocket ws;
    private final ScheduledExecutorService reconnect = Executors.newSingleThreadScheduledExecutor();
    private final Map<Integer, String> requestBook = new HashMap();
    private final Map<Long, String> subscriptionBook = new HashMap();
    private volatile boolean wanted = false;
    private volatile long lastTrigger = 0;
    private final OkHttpClient client;

    interface Listener {
        void onFastEvent(String str, String str2);

        void onState(String str);
    }

    FastStream(Context c, Listener listener) {
        this.c = c.getApplicationContext();
        this.listener = listener;
        this.client = new OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).build();
    }

    synchronized void start() {
        this.wanted = true;
        if (this.ws != null) {
            return;
        }
        String key = Prefs.helius(this.c).trim();
        if (key.isEmpty()) {
            state("WAITING FOR HELIUS KEY");
            return;
        }
        try {
            String url = "wss://mainnet.helius-rpc.com/?api-key=" + URLEncoder.encode(key, "UTF-8");
            Request r = new Request.Builder().url(url).build();
            state("CONNECTING");
            this.ws = this.client.newWebSocket(r, new WebSocketListener() { // from class: com.memetaillab.beta1.FastStream.1
                @Override // okhttp3.WebSocketListener
                public void onOpen(WebSocket w, Response response) {
                    FastStream.this.state("CONNECTED · processed logs · auto-reconnect");
                    FastStream.this.subscribe(w, 1, "675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8", "RAYDIUM");
                    FastStream.this.subscribe(w, 2, "CPMMoo8L3F4NbTegBCKVNunggL7H1ZpdTHKxQB5qKP1C", "RAYDIUM");
                    FastStream.this.subscribe(w, 3, "CAMMCzo5YL8w4VFF8KVHrK22GGUsp5VTaW7grrKgrWqK", "RAYDIUM");
                    FastStream.this.subscribe(w, 4, "LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj", "RAYDIUM");
                    FastStream.this.subscribe(w, 5, "pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA", "PUMPSWAP");
                    FastStream.this.subscribe(w, 6, "6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P", "PUMPFUN");
                }

                @Override // okhttp3.WebSocketListener
                public void onMessage(WebSocket w, String text) {
                    FastStream.this.handle(text);
                }

                @Override // okhttp3.WebSocketListener
                public void onClosed(WebSocket w, int code, String reason) {
                    synchronized (FastStream.this) {
                        FastStream.this.ws = null;
                    }
                    FastStream.this.state("DISCONNECTED " + code);
                    FastStream.this.retry();
                }

                @Override // okhttp3.WebSocketListener
                public void onFailure(WebSocket w, Throwable t, Response response) {
                    synchronized (FastStream.this) {
                        FastStream.this.ws = null;
                    }
                    FastStream.this.state("ERROR · " + FastStream.shortMsg(t));
                    FastStream.this.retry();
                }
            });
        } catch (Exception e) {
            state("ERROR · " + shortMsg(e));
            retry();
        }
    }

    synchronized void restart() {
        stopSocket();
        if (this.wanted) {
            start();
        }
    }

    synchronized void stop() {
        this.wanted = false;
        stopSocket();
        state("OFF");
    }

    String state() {
        return Prefs.streamState(this.c);
    }

    private synchronized void stopSocket() {
        WebSocket x = this.ws;
        this.ws = null;
        if (x != null) {
            try {
                x.close(1000, "stop");
            } catch (Exception e) {
            }
        }
        this.requestBook.clear();
        this.subscriptionBook.clear();
    }

    public void retry() {
        if (this.wanted) {
            this.reconnect.schedule(new Runnable() { // from class: com.memetaillab.beta1.FastStream$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() {
                    FastStream.this.lambda$retry$0();
                }
            }, 5L, TimeUnit.SECONDS);
        }
    }

    public /* synthetic */ void lambda$retry$0() {
        if (this.wanted) {
            start();
        }
    }

    public void subscribe(WebSocket w, int id, String program, String book) {
        try {
            this.requestBook.put(Integer.valueOf(id), book);
            JSONObject filter = new JSONObject().put("mentions", new JSONArray().put(program));
            JSONObject opts = new JSONObject().put("commitment", "processed");
            JSONObject req = new JSONObject().put("jsonrpc", "2.0").put("id", id).put("method", "logsSubscribe").put("params", new JSONArray().put(filter).put(opts));
            w.send(req.toString());
        } catch (Exception e) {
        }
    }

    public void handle(String text) {
        boolean creation;
        try {
            JSONObject j = new JSONObject(text);
            if (j.has("id") && j.has("result")) {
                int id = j.optInt("id", -1);
                String book = this.requestBook.get(Integer.valueOf(id));
                Object r = j.opt("result");
                if (book != null && (r instanceof Number)) {
                    this.subscriptionBook.put(Long.valueOf(((Number) r).longValue()), book);
                    return;
                }
                return;
            }
            JSONObject params = j.optJSONObject("params");
            if (params == null) {
                return;
            }
            long sub = params.optLong("subscription", -1L);
            String book2 = this.subscriptionBook.get(Long.valueOf(sub));
            if (book2 == null) {
                return;
            }
            JSONObject result = params.optJSONObject("result");
            JSONObject value = result == null ? null : result.optJSONObject("value");
            JSONArray logs = value != null ? value.optJSONArray("logs") : null;
            if (logs == null) {
                return;
            }
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < logs.length(); i++) {
                b.append(logs.optString(i, "")).append(' ');
            }
            String x = b.toString().toLowerCase(Locale.US);
            if (!x.contains("instruction: create") && !x.contains("instruction: initialize") && !x.contains("initialize2") && !x.contains("migrate") && !x.contains("graduate")) {
                creation = false;
                if (creation) {
                    return;
                }
                long now = System.currentTimeMillis();
                if (now - this.lastTrigger < 500) {
                    return;
                }
                this.lastTrigger = now;
                String signature = value.optString("signature", "");
                Prefs.streamEvent(this.c, book2);
                if (this.listener != null) {
                    this.listener.onFastEvent(book2, signature);
                    return;
                }
                return;
            }
            creation = true;
            if (creation) {
            }
        } catch (Exception e) {
        }
    }

    public void state(String s) {
        Prefs.streamState(this.c, s);
        if (this.listener != null) {
            this.listener.onState(s);
        }
    }

    public static String shortMsg(Throwable t) {
        String s = t == null ? "unknown" : t.getMessage();
        if (s == null) {
            s = t.getClass().getSimpleName();
        }
        return s.length() > 120 ? s.substring(0, 120) : s;
    }
}
