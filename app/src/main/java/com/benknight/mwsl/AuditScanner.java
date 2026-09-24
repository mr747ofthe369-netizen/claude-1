package com.benknight.mwsl;

import com.benknight.mwsl.AuditScanner;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;
import org.json.JSONArray;
import org.json.JSONObject;

class AuditScanner {

    interface Progress {
        void onProgress(String str);
    }

    private static class ReplayPos {
        double cost;
        double lastPrice;
        double qty;

        private ReplayPos() {
        }
    }

    static class ReplayTrade {
        TradeSignal e;
        double price;
        String priceSource;
        String symbol;

        private ReplayTrade() {
            this.price = Double.NaN;
            this.symbol = "";
            this.priceSource = "";
        }
    }

    static String scan(Network net, Db db, String label, String wallet, int requestedLimit, String trackedTraderId, int slippageBps, int baseFeeBps, Progress progress) {
        int target = Math.max(1, Math.min(100, requestedLimit));
        int maxSignatures = Math.min(2500, Math.max(500, target * 100));
        int maxSuccessfulPayloads = Math.min(300, Math.max(100, target * 10));
        StringBuilder detail = new StringBuilder();
        StringBuilder misses = new StringBuilder();
        int detailedMisses = 0;
        Map<String, Integer> missReasons = new LinkedHashMap<>();
        Map<String, Integer> venues = new LinkedHashMap<>();
        Map<String, Integer> linkedOwners = new LinkedHashMap<>();
        Map<String, Integer> unknownPrograms = new LinkedHashMap<>();
        Map<String, Market> marketCache = new HashMap<>();
        Set<String> tokens = new HashSet<>();
        List<ReplayTrade> replayTrades = new ArrayList<>();
        int signaturesScanned = 0;
        int failedSignatures = 0;
        int successfulPayloads = 0;
        int payloadUnavailable = 0;
        int txErrors = 0;
        int tradeSignals = 0;
        int buys = 0;
        int sells = 0;
        int parserMisses = 0;
        Map<String, JSONObject> heliusBySig = new HashMap<>();
        Map<String, JSONObject> cieloBySig = new HashMap<>();
        String enhancedStatus = "";
        try {
            if (progress != null) {
                progress.onProgress("Loading enhanced sources for " + label + "…");
            }
            try {
                JSONArray hs = net.heliusEnhanced(wallet, Math.min(100, Math.max(target, 20)));
                for (int i = 0; i < hs.length(); i++) {
                    JSONObject x = hs.optJSONObject(i);
                    if (x != null && !x.optString("signature", "").isEmpty()) {
                        heliusBySig.put(x.optString("signature"), x);
                    }
                }
                if (hs.length() > 0) {
                    enhancedStatus = enhancedStatus + "Helius " + hs.length() + " tx";
                }
            } catch (Exception e) {
                enhancedStatus = enhancedStatus + "Helius error: " + trim(e.getMessage());
            }
            try {
                JSONArray cs = net.cieloSwaps(wallet, Math.min(100, Math.max(target, 20)));
                for (int i = 0; i < cs.length(); i++) {
                    JSONObject x = cs.optJSONObject(i);
                    if (x != null && !x.optString("tx_hash", "").isEmpty()) {
                        cieloBySig.put(x.optString("tx_hash"), x);
                    }
                }
                if (cs.length() > 0) {
                    enhancedStatus = enhancedStatus + (enhancedStatus.isEmpty() ? "" : " · ") + "Cielo " + cs.length() + " swap(s)";
                }
            } catch (Exception e) {
                enhancedStatus = enhancedStatus + (enhancedStatus.isEmpty() ? "" : " · ") + "Cielo error: " + trim(e.getMessage());
            }
            if (enhancedStatus.isEmpty()) {
                enhancedStatus = "enhanced sources not configured";
            }
            String before = null;
            boolean done = false;
            boolean adaptiveStopped = false;
            String adaptiveReason = "";
            while (!done && tradeSignals < target && successfulPayloads < maxSuccessfulPayloads && signaturesScanned < maxSignatures) {
                int pageSize = Math.min(100, maxSignatures - signaturesScanned);
                if (progress != null) {
                    progress.onProgress("Searching " + label + " for actual trades… " + tradeSignals + "/" + target + " trades · " + successfulPayloads + " successful payloads · " + signaturesScanned + " signatures");
                }
                JSONArray page = signaturePage(net, wallet, before, pageSize);
                if (page.length() == 0) {
                    break;
                }
                for (int i = 0; i < page.length(); i++) {
                    JSONObject sigInfo = page.optJSONObject(i);
                    if (sigInfo == null) {
                        continue;
                    }
                    String sig = sigInfo.optString("signature", "");
                    if (sig.isEmpty()) {
                        continue;
                    }
                    signaturesScanned++;
                    if (!sigInfo.isNull("err") && sigInfo.opt("err") != JSONObject.NULL) {
                        failedSignatures++;
                        if (signaturesScanned >= maxSignatures) {
                            break;
                        }
                        continue;
                    }
                    try {
                        JSONObject tx = net.transaction(sig);
                        if (tx == null) {
                            payloadUnavailable++;
                            if (signaturesScanned >= maxSignatures) {
                                break;
                            }
                            continue;
                        }
                        JSONObject meta = tx.optJSONObject("meta");
                        if (meta != null && !meta.isNull("err") && meta.opt("err") != JSONObject.NULL) {
                            failedSignatures++;
                            continue;
                        }
                        successfulPayloads++;
                        if (progress != null && successfulPayloads % 10 == 0) {
                            progress.onProgress("Scanning " + label + "… " + tradeSignals + "/" + target + " trades found after " + successfulPayloads + " successful tx / " + signaturesScanned + " signatures");
                        }
                        List<TradeSignal> trades = SolanaParser.parseTrades(wallet, sigInfo, tx);
                        if (trades.isEmpty()) {
                            parserMisses++;
                            String reason = SolanaParser.diagnose(wallet, tx);
                            missReasons.put(reason, Integer.valueOf(missReasons.containsKey(reason) ? missReasons.get(reason).intValue() + 1 : 1));
                            for (Map.Entry<String, Integer> en : SolanaParser.changedTokenOwners(tx, wallet).entrySet()) {
                                linkedOwners.put(en.getKey(), Integer.valueOf(linkedOwners.containsKey(en.getKey()) ? linkedOwners.get(en.getKey()).intValue() + en.getValue().intValue() : en.getValue().intValue()));
                            }
                            for (String pid : SolanaParser.unknownProgramIds(tx)) {
                                unknownPrograms.put(pid, Integer.valueOf(unknownPrograms.containsKey(pid) ? unknownPrograms.get(pid).intValue() + 1 : 1));
                            }
                            JSONObject h = heliusBySig.get(sig);
                            JSONObject ci = cieloBySig.get(sig);
                            String enhanced = "";
                            if (h != null) {
                                String type = h.optString("type", "UNKNOWN");
                                enhanced = enhanced + "Helius=" + type;
                                if ("SWAP".equalsIgnoreCase(type)) {
                                    enhanced = enhanced + " [RAW PARSER MISSED SWAP]";
                                }
                            }
                            if (ci != null) {
                                if (!enhanced.isEmpty()) {
                                    enhanced = enhanced + " · ";
                                }
                                enhanced = enhanced + "Cielo=SWAP " + cieloRoute(ci) + " [RAW PARSER MISSED SWAP]";
                            }
                            if (detailedMisses < 40) {
                                misses.append(formatTime(sigInfo.optLong("blockTime", 0L) * 1000)).append(" tx ").append(Db.shortAddr(sig)).append("\n  ").append(reason);
                                if (!enhanced.isEmpty()) {
                                    misses.append("\n  ").append(enhanced);
                                }
                                misses.append("\n");
                                detailedMisses++;
                            }
                        } else {
                            for (TradeSignal e : trades) {
                                tradeSignals++;
                                if ("BUY".equals(e.side)) {
                                    buys++;
                                } else if ("SELL".equals(e.side)) {
                                    sells++;
                                }
                                tokens.add(e.mint);
                                String dex = PaperEngine.join(e.dex);
                                String router = PaperEngine.join(e.router);
                                String venue = PaperEngine.venue(router, dex);
                                venues.put(venue, Integer.valueOf(venues.containsKey(venue) ? venues.get(venue).intValue() + 1 : 1));
                                ReplayTrade rt = new ReplayTrade();
                                rt.e = e;
                                JSONObject ci = cieloBySig.get(sig);
                                double cieloPrice = cieloPriceForMint(ci, e.mint);
                                if (Double.isFinite(cieloPrice) && cieloPrice > 0.0d) {
                                    rt.price = cieloPrice;
                                    rt.priceSource = "Cielo transaction USD";
                                    rt.symbol = cieloSymbolForMint(ci, e.mint);
                                } else if (Double.isFinite(e.signalPriceUsd) && e.signalPriceUsd > 0.0d) {
                                    rt.price = e.signalPriceUsd;
                                    rt.priceSource = "transaction stablecoin quote";
                                } else if ("SOL".equalsIgnoreCase(e.quoteUnit) && Double.isFinite(e.quoteAmount) && e.quoteAmount > 0.0d && e.tokenQty > 0.0d) {
                                    double solUsd = net.historicalSolUsd(e.time);
                                    if (Double.isFinite(solUsd) && solUsd > 0.0d) {
                                        rt.price = (e.quoteAmount * solUsd) / e.tokenQty;
                                        rt.priceSource = "transaction SOL quote × historical SOL/USDT";
                                    } else {
                                        double derived = net.deriveUsdFromSignal(e);
                                        if (Double.isFinite(derived) && derived > 0.0d) {
                                            rt.price = derived;
                                            rt.priceSource = "transaction SOL quote × current SOL/USD (fallback)";
                                        }
                                    }
                                }
                                if (!Double.isFinite(rt.price) || rt.price <= 0.0d) {
                                    Market m = marketCache.get(e.mint);
                                    if (m == null) {
                                        m = net.tokenMarket(e.mint, false);
                                        marketCache.put(e.mint, m);
                                    }
                                    if (Double.isFinite(m.priceUsd) && m.priceUsd > 0.0d) {
                                        rt.price = m.priceUsd;
                                        rt.priceSource = "current DexScreener fallback (not historical)";
                                    }
                                }
                                if (rt.symbol == null || rt.symbol.isEmpty()) {
                                    Market m2 = marketCache.get(e.mint);
                                    if (m2 == null) {
                                        m2 = net.tokenMarket(e.mint, false);
                                        marketCache.put(e.mint, m2);
                                    }
                                    rt.symbol = m2.symbol;
                                }
                                if (rt.symbol == null || rt.symbol.isEmpty()) {
                                    rt.symbol = Db.shortAddr(e.mint);
                                }
                                replayTrades.add(rt);
                            }
                        }
                    } catch (Exception ex) {
                        txErrors++;
                        misses.append("TX ERROR ").append(Db.shortAddr(sig)).append(" — ").append(trim(ex.getMessage())).append("\n");
                    }
                    if (signaturesScanned >= 500 && successfulPayloads < 5 && failedSignatures >= Math.floor(signaturesScanned * 0.95d)) {
                        adaptiveStopped = true;
                        adaptiveReason = "stopped early: >95% failed signatures and fewer than 5 successful payloads after 500 signatures";
                        done = true;
                        break;
                    }
                    if (successfulPayloads >= 100 && tradeSignals == 0) {
                        adaptiveStopped = true;
                        adaptiveReason = "stopped early: 100 successful payloads produced no directly attributable DEX trade";
                        done = true;
                        break;
                    }
                    if (tradeSignals >= target || successfulPayloads >= maxSuccessfulPayloads || signaturesScanned >= maxSignatures) {
                        break;
                    }
                }
                before = page.optJSONObject(page.length() - 1) != null ? page.optJSONObject(page.length() - 1).optString("signature", null) : null;
                if (before == null || before.isEmpty() || page.length() < pageSize) {
                    done = true;
                }
            }
            Collections.sort(replayTrades, Comparator.comparingLong(rt -> rt.e.time));
            double cash = 1000.0d;
            double realized = 0.0d;
            double peak = 1000.0d;
            double maxDd = 0.0d;
            int replayFills = 0;
            int replaySkips = 0;
            int replayWins = 0;
            int replayLosses = 0;
            Map<String, ReplayPos> pos = new HashMap<>();
            Map<String, Double> lastPx = new HashMap<>();
            detail.append("Chronological isolated $1,000 replay (oldest → newest)\n");
            detail.append("Uses 1% equity per BUY; live slippage/fee settings; SELLs mirror on-chain sell fraction when available.\n\n");
            for (ReplayTrade rt : replayTrades) {
                TradeSignal e = rt.e;
                String venue = PaperEngine.venue(PaperEngine.join(e.router), PaperEngine.join(e.dex));
                String outcome;
                if (!Double.isFinite(rt.price) || rt.price <= 0.0d) {
                    replaySkips++;
                    outcome = "SKIP — no usable transaction/current fallback price";
                } else if ("BUY".equals(e.side)) {
                    double equity = virtualEquity(cash, pos, lastPx);
                    double gross = Math.max(0.01d, equity * 0.01d);
                    double feeRate = feeRate(venue, baseFeeBps);
                    double fee = gross * feeRate;
                    if (gross + fee > cash) {
                        gross = Math.max(0.0d, cash / (feeRate + 1.0d));
                    }
                    if (gross < 0.01d) {
                        replaySkips++;
                        outcome = "SKIP — virtual cash exhausted";
                    } else {
                        double fill = rt.price * ((slippageBps / 10000.0d) + 1.0d);
                        ReplayPos p = pos.get(e.mint);
                        if (p == null) {
                            p = new ReplayPos();
                            pos.put(e.mint, p);
                        }
                        double qty = gross / fill;
                        p.qty += qty;
                        p.cost += gross + fee;
                        p.lastPrice = rt.price;
                        lastPx.put(e.mint, Double.valueOf(rt.price));
                        cash -= gross + fee;
                        replayFills++;
                        outcome = String.format(Locale.US, "FILL $%.2f @ %.0f bps slip", Double.valueOf(gross), Double.valueOf(slippageBps));
                    }
                } else {
                    ReplayPos p2 = pos.get(e.mint);
                    if (p2 == null || p2.qty <= 0.0d) {
                        replaySkips++;
                        outcome = "SKIP — no replay position";
                    } else {
                        double frac = Double.isFinite(e.sellFraction) ? Math.max(0.001d, Math.min(1.0d, e.sellFraction)) : 1.0d;
                        double qty2 = frac > 0.995d ? p2.qty : p2.qty * frac;
                        double fill2 = rt.price * (1.0d - (slippageBps / 10000.0d));
                        double gross2 = qty2 * fill2;
                        double fee2 = gross2 * feeRate(venue, baseFeeBps);
                        double proceeds = gross2 - fee2;
                        double cost = p2.cost * (qty2 / p2.qty);
                        double pnl = proceeds - cost;
                        realized += pnl;
                        cash += proceeds;
                        p2.qty -= qty2;
                        p2.cost -= cost;
                        p2.lastPrice = rt.price;
                        lastPx.put(e.mint, Double.valueOf(rt.price));
                        replayFills++;
                        if (pnl > 0.0d) {
                            replayWins++;
                        } else if (pnl < 0.0d) {
                            replayLosses++;
                        }
                        outcome = String.format(Locale.US, "FILL %.1f%% · PnL %+.2f", Double.valueOf(100.0d * frac), Double.valueOf(pnl));
                        if (p2.qty < 1.0E-12d || frac > 0.995d) {
                            pos.remove(e.mint);
                        }
                    }
                }
                double eq = virtualEquity(cash, pos, lastPx);
                peak = Math.max(peak, eq);
                if (peak > 0.0d) {
                    maxDd = Math.max(maxDd, (peak - eq) / peak);
                }
                detail.append(formatTime(e.time)).append("  ").append(e.side).append("  ").append(rt.symbol).append("  ").append(venue).append("\n  ").append(outcome).append(" · market px ").append(Double.isFinite(rt.price) ? String.format(Locale.US, "$%.10f", Double.valueOf(rt.price)) : "n/a").append(" · ").append(rt.priceSource.isEmpty() ? "no price source" : rt.priceSource).append("\n");
            }
            double finalEq = virtualEquity(cash, pos, lastPx);
            int enhancedMisses = 0;
            for (String sig2 : cieloBySig.keySet()) {
                boolean seen = false;
                for (ReplayTrade rt2 : replayTrades) {
                    if (sig2.equals(rt2.e.signature)) {
                        seen = true;
                        break;
                    }
                }
                if (!seen) {
                    enhancedMisses++;
                }
            }
            for (Map.Entry<String, JSONObject> en2 : heliusBySig.entrySet()) {
                if (!"SWAP".equalsIgnoreCase(en2.getValue().optString("type", ""))) {
                    continue;
                }
                boolean seen2 = false;
                for (ReplayTrade rt3 : replayTrades) {
                    if (en2.getKey().equals(rt3.e.signature)) {
                        seen2 = true;
                        break;
                    }
                }
                if (!seen2) {
                    enhancedMisses++;
                }
            }
            int pricedTrades = 0;
            int sizedSells = 0;
            for (ReplayTrade rt4 : replayTrades) {
                if (Double.isFinite(rt4.price) && rt4.price > 0.0d) {
                    pricedTrades++;
                }
                if ("SELL".equals(rt4.e.side) && Double.isFinite(rt4.e.sellFraction)) {
                    sizedSells++;
                }
            }
            double successPct = signaturesScanned > 0 ? (successfulPayloads * 100.0d) / signaturesScanned : 0.0d;
            double decodePct = successfulPayloads > 0 ? Math.min(100.0d, (tradeSignals * 100.0d) / successfulPayloads) : 0.0d;
            double pricePct = tradeSignals > 0 ? (pricedTrades * 100.0d) / tradeSignals : 0.0d;
            double sellSizePct = sells > 0 ? (sizedSells * 100.0d) / sells : tradeSignals > 0 ? 100 : 0;
            int trackability = (int) Math.round((0.2d * successPct) + (0.4d * decodePct) + (0.25d * pricePct) + (0.15d * sellSizePct));
            if (tradeSignals == 0) {
                trackability = Math.min(trackability, 20);
            }
            String trackLabel = trackability >= 80 ? "HIGH" : trackability >= 60 ? "MEDIUM" : trackability >= 35 ? "LOW" : "UNSUITABLE";
            StringBuilder out = new StringBuilder();
            out.append("Technical trackability: ").append(trackability).append("/100 ").append(trackLabel).append(" · success ").append(String.format(Locale.US, "%.1f%%", Double.valueOf(successPct))).append(" · decoded trades ").append(String.format(Locale.US, "%.1f%%", Double.valueOf(decodePct))).append(" · priced ").append(String.format(Locale.US, "%.1f%%", Double.valueOf(pricePct))).append(" · sell sizing ").append(String.format(Locale.US, "%.1f%%", Double.valueOf(sellSizePct))).append("\n");
            if (trackedTraderId != null) {
                TraderDef td = Config.trader(trackedTraderId);
                if (td != null) {
                    out.append("Current role: ").append(td.role).append(" · paper copy ").append(td.paperEnabled ? "ENABLED" : "DISABLED").append("\n");
                }
            }
            out.append("Trade search: scanned ").append(signaturesScanned).append(" signatures · successful payloads ").append(successfulPayloads).append(" · trade signals found ").append(tradeSignals).append("/").append(target).append(" · failed signatures skipped ").append(failedSignatures).append(" · unavailable payloads ").append(payloadUnavailable).append("\n");
            if (adaptiveStopped) {
                out.append("Adaptive stop: ").append(adaptiveReason).append("\n");
            }
            out.append("Summary: ").append(tradeSignals).append(" raw trade signal(s) · ").append(buys).append(" buys / ").append(sells).append(" sells · ").append(tokens.size()).append(" unique token(s) · ").append(parserMisses).append(" successful non-swap/parser miss(es) · ").append(txErrors).append(" tx error(s)\n");
            out.append("Enhanced cross-check: ").append(enhancedStatus).append(" · enhanced SWAP evidence missed by raw parser ≈ ").append(enhancedMisses).append("\n");
            out.append("Venues: ").append(venueSummary(venues)).append("\n");
            out.append("Unknown program IDs: ").append(programSummary(unknownPrograms)).append("\n");
            out.append("Recurring changed token owners (possible execution/subwallets; not confirmed): ").append(ownerSummary(linkedOwners, wallet)).append("\n");
            out.append(String.format(Locale.US, "Replay: $1000.00 → $%.2f (%+.2f%%) · realized %+.2f · fills %d · skips %d · wins %d · losses %d · max DD %.2f%% · open %d\n\n", Double.valueOf(finalEq), Double.valueOf(((finalEq / 1000.0d) - 1.0d) * 100.0d), Double.valueOf(realized), Integer.valueOf(replayFills), Integer.valueOf(replaySkips), Integer.valueOf(replayWins), Integer.valueOf(replayLosses), Double.valueOf(100.0d * maxDd), Integer.valueOf(pos.size())));
            out.append(label).append("\nWallet: ").append(wallet).append("\nTarget trade signals: ").append(target).append(" (caps: ").append(maxSignatures).append(" signatures / ").append(maxSuccessfulPayloads).append(" successful payloads).\n\n");
            out.append((CharSequence) detail);
            if (misses.length() > 0 || !missReasons.isEmpty()) {
                out.append("\nRaw parser misses / diagnostics\n");
                for (Map.Entry<String, Integer> en3 : missReasons.entrySet()) {
                    out.append("• ").append(en3.getValue()).append(" × ").append(en3.getKey()).append("\n");
                }
                if (misses.length() > 0) {
                    out.append("\n").append((CharSequence) misses);
                }
            }
            return out.toString();
        } catch (Exception e) {
            return label + " scan failed: " + trim(e.getMessage());
        }
    }

    private static JSONArray signaturePage(Network net2, String wallet, String before, int limit) throws Exception {
        Exception last = null;
        long[] waits = {1000, 2000, 4000, 8000, 12000};
        for (long j : waits) {
            try {
                return net2.signatures(wallet, null, before, limit);
            } catch (Exception e) {
                last = e;
                try {
                    Thread.sleep(j);
                } catch (InterruptedException e2) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (last == null) {
            throw new Exception("signature lookup failed");
        }
        throw last;
    }

    private static String programSummary(Map<String, Integer> map) {
        if (map.isEmpty()) {
            return "none";
        }
        List<Map.Entry<String, Integer>> xs = new ArrayList<>(map.entrySet());
        Collections.sort(xs, new Comparator() { // from class: com.benknight.mwsl.AuditScanner$$ExternalSyntheticLambda2
            @Override // java.util.Comparator
            public final int compare(Object obj, Object obj2) {
                int compare;
                compare = Integer.compare(((Integer) ((Map.Entry) obj2).getValue()).intValue(), ((Integer) ((Map.Entry) obj).getValue()).intValue());
                return compare;
            }
        });
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (Map.Entry<String, Integer> e : xs) {
            int n2 = n + 1;
            if (n >= 8) {
                break;
            }
            if (b.length() > 0) {
                b.append(" | ");
            }
            b.append(e.getKey()).append(" ×").append(e.getValue());
            n = n2;
        }
        return b.toString();
    }

    private static String ownerSummary(Map<String, Integer> map, String watched) {
        if (map.isEmpty()) {
            return "none";
        }
        List<Map.Entry<String, Integer>> xs = new ArrayList<>(map.entrySet());
        Collections.sort(xs, new Comparator() { // from class: com.benknight.mwsl.AuditScanner$$ExternalSyntheticLambda1
            @Override // java.util.Comparator
            public final int compare(Object obj, Object obj2) {
                int compare;
                compare = Integer.compare(((Integer) ((Map.Entry) obj2).getValue()).intValue(), ((Integer) ((Map.Entry) obj).getValue()).intValue());
                return compare;
            }
        });
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (Map.Entry<String, Integer> e : xs) {
            if (!e.getKey().equals(watched)) {
                int n2 = n + 1;
                if (n >= 8) {
                    break;
                }
                if (b.length() > 0) {
                    b.append(" | ");
                }
                b.append(e.getKey()).append(" ×").append(e.getValue());
                n = n2;
            }
        }
        return b.length() == 0 ? "none" : b.toString();
    }

    private static double feeRate(String venue, int baseFeeBps) {
        double bps = Math.max(0, baseFeeBps);
        String dex = venue == null ? "" : venue;
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

    private static double virtualEquity(double cash, Map<String, ReplayPos> pos, Map<String, Double> lastPx) {
        double eq = cash;
        for (Map.Entry<String, ReplayPos> e : pos.entrySet()) {
            double px = lastPx.containsKey(e.getKey()) ? lastPx.get(e.getKey()).doubleValue() : e.getValue().lastPrice;
            if (Double.isFinite(px) && px > 0.0d) {
                eq += e.getValue().qty * px;
            }
        }
        return eq;
    }

    private static double cieloPriceForMint(JSONObject c, String mint) {
        if (c == null || mint == null) {
            return Double.NaN;
        }
        if (mint.equals(c.optString("token0_address", ""))) {
            return c.optDouble("token0_price_usd", Double.NaN);
        }
        if (mint.equals(c.optString("token1_address", ""))) {
            return c.optDouble("token1_price_usd", Double.NaN);
        }
        return Double.NaN;
    }

    private static String cieloSymbolForMint(JSONObject c, String mint) {
        if (c == null || mint == null) {
            return "";
        }
        if (mint.equals(c.optString("token0_address", ""))) {
            return c.optString("token0_symbol", "");
        }
        return mint.equals(c.optString("token1_address", "")) ? c.optString("token1_symbol", "") : "";
    }

    private static String cieloRoute(JSONObject c) {
        return c == null ? "" : c.optString("token0_symbol", "?") + " → " + c.optString("token1_symbol", "?");
    }

    private static String venueSummary(Map<String, Integer> map) {
        if (map.isEmpty()) {
            return "none detected";
        }
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            if (b.length() > 0) {
                b.append(" · ");
            }
            b.append(e.getKey()).append(": ").append(e.getValue());
        }
        return b.toString();
    }

    private static String formatTime(long t) {
        return t <= 0 ? "unknown time" : new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date(t));
    }

    private static String trim(String s) {
        if (s == null) {
            return "unknown";
        }
        String s2 = s.replace('\n', ' ');
        return s2.length() > 140 ? s2.substring(0, 137) + "…" : s2;
    }

    private AuditScanner() {
    }
}
