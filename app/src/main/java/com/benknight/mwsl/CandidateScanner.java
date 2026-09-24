package com.benknight.mwsl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

class CandidateScanner {

    interface Progress {
        void onProgress(String str);
    }

    private static class Candidate {
        int buys;
        int score;
        int sells;
        int swaps;
        final Set<String> tokens;
        int txs;
        final Set<String> venues;
        String wallet;

        private Candidate() {
            this.venues = new LinkedHashSet();
            this.tokens = new HashSet();
        }
    }

    static String discover(Network net2, int txPerProgram, Progress progress) {
        int per = Math.max(1, Math.min(10, txPerProgram));
        Map<String, Candidate> candidates = new HashMap<>();
        Set<String> seenSig = new HashSet<>();
        Set<String> tracked = new HashSet<>();
        for (TraderDef t : Config.TRADERS) {
            tracked.add(t.wallet);
        }
        int programs = 0;
        int fetched = 0;
        int errors = 0;
        for (Map.Entry<String, VenueDef> entry : Config.DEX_PROGRAMS.entrySet()) {
            if (!"dex".equals(entry.getValue().kind)) {
                continue;
            }
            programs++;
            String program = entry.getKey();
            String name = entry.getValue().name;
            try {
                if (progress != null) {
                    progress.onProgress("Discovering wallets via " + name + "…");
                }
                JSONArray sigs = net2.signatures(program, null, null, per);
                for (int i = 0; i < sigs.length(); i++) {
                    JSONObject si = sigs.optJSONObject(i);
                    if (si == null) {
                        continue;
                    }
                    String sig = si.optString("signature", "");
                    if (sig.isEmpty() || seenSig.contains(sig)) {
                        continue;
                    }
                    seenSig.add(sig);
                    try {
                        JSONObject tx = net2.transaction(sig);
                        fetched++;
                        List<String> signers = SolanaParser.signerKeys(tx);
                        for (String wallet : signers) {
                            if (wallet == null || wallet.isEmpty() || program.equals(wallet)) {
                                continue;
                            }
                            List<TradeSignal> trades = SolanaParser.parseTrades(wallet, si, tx);
                            if (trades.isEmpty()) {
                                continue;
                            }
                            Candidate c = candidates.get(wallet);
                            if (c == null) {
                                c = new Candidate();
                                c.wallet = wallet;
                                candidates.put(wallet, c);
                            }
                            c.txs++;
                            for (TradeSignal e : trades) {
                                c.swaps++;
                                if ("BUY".equals(e.side)) {
                                    c.buys++;
                                } else if ("SELL".equals(e.side)) {
                                    c.sells++;
                                }
                                c.tokens.add(e.mint);
                                c.venues.add(PaperEngine.venue(PaperEngine.join(e.router), PaperEngine.join(e.dex)));
                            }
                        }
                        try {
                            Thread.sleep(100L);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                    } catch (Exception ex) {
                        errors++;
                    }
                }
            } catch (Exception e2) {
                errors++;
            }
        }
        List<Candidate> list = new ArrayList<>(candidates.values());
        for (Candidate c4 : list) {
            c4.score = trackabilityScore(c4);
        }
        Collections.sort(list, new Comparator<Candidate>() { // from class: com.benknight.mwsl.CandidateScanner.1
            @Override // java.util.Comparator
            public int compare(Candidate a, Candidate b) {
                int x = Integer.compare(b.score, a.score);
                if (x != 0) {
                    return x;
                }
                int x2 = Integer.compare(b.swaps, a.swaps);
                return x2 != 0 ? x2 : a.wallet.compareTo(b.wallet);
            }
        });
        StringBuilder out = new StringBuilder();
        out.append("Recent DEX signer discovery\n").append("Scanned ").append(programs).append(" known DEX programs · ").append(fetched).append(" unique transactions · ").append(errors).append(" fetch/decode error(s)\n").append("Candidates are wallets with swap-like balance changes in this small recent sample. Trackability measures technical copyability only, not profitability.\n").append("Active copy slots currently used: ").append(Config.activeSlotsUsed()).append("/5 · open replacement slots: ").append(Math.max(0, 5 - Config.activeSlotsUsed())).append("\n\n");
        if (list.isEmpty()) {
            out.append("No candidate signer wallets decoded in this sample. Try again later or increase transactions per DEX.");
            return out.toString();
        }
        int shown = Math.min(30, list.size());
        for (int i2 = 0; i2 < shown; i2++) {
            Candidate c5 = list.get(i2);
            out.append(i2 + 1).append(". ").append(c5.wallet);
            if (tracked.contains(c5.wallet)) {
                out.append("  [already tracked]");
            }
            out.append("\n   provisional trackability ").append(c5.score).append("/100 ").append(trackabilityLabel(c5.score)).append(" · observed swaps ").append(c5.swaps).append(" · buys ").append(c5.buys).append(" · sells ").append(c5.sells).append(" · tokens ").append(c5.tokens.size()).append(" · tx ").append(c5.txs).append("\n   venues: ");
            if (c5.venues.isEmpty()) {
                out.append("unknown");
            } else {
                int n = 0;
                for (String v : c5.venues) {
                    int n2 = n + 1;
                    if (n > 0) {
                        out.append(" | ");
                    }
                    out.append(v);
                    n = n2;
                }
            }
            out.append("\n\n");
        }
        return out.toString();
    }

    private static int trackabilityScore(Candidate c) {
        int bothSides = 8;
        int activity = Math.min(40, c.swaps * 8);
        if (c.buys > 0 && c.sells > 0) {
            bothSides = 20;
        } else if (c.swaps <= 0) {
            bothSides = 0;
        }
        int diversity = Math.min(20, c.tokens.size() * 7);
        int venue = Math.min(20, c.venues.size() * 10);
        return Math.min(100, activity + bothSides + diversity + venue);
    }

    private static String trackabilityLabel(int score) {
        return score >= 80 ? "HIGH" : score >= 60 ? "MEDIUM" : score >= 35 ? "LOW" : "UNSUITABLE";
    }

    private CandidateScanner() {
    }
}
