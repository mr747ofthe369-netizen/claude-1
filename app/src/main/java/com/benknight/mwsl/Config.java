package com.benknight.mwsl;

import java.util.LinkedHashMap;
import java.util.Map;

final class Config {
    static final double STARTING_BALANCE = 1000.0d;
    static final String USDC = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v";
    static final String USDT = "Es9vMFrzaCERmJfrF4H2FYDkgFdmHqkV6P5dY1Y9Fgr";
    static final String WSOL = "So11111111111111111111111111111111111111112";
    static final TraderDef[] TRADERS = {new TraderDef("jijo", "Jijo / JoJo", "4BdKaxN8G6ka4GYtQQWk4G4dZRUTX2vQH9GcXdBREFUk", "Research watchlist — raw address is not currently copyable", "RESEARCH", false), new TraderDef("cented", "Cented", "CyaE1VxvBrahnPWkqm5VsdCvyS2QmNht2UFrKJHga54o", "Research watchlist — requires cleaner/enhanced trade attribution", "RESEARCH", false), new TraderDef("kreo", "Kreo", "BCnqsPEtA1TkgednYEebRpkmwFRJDCjMQcKZMMtEdArc", "Active copy slot — directly attributable DEX activity", "ACTIVE", true), new TraderDef("yogurt", "Yogurt", "DKwybycDSWidrHfpMjaahUsT1Yid3kig86ncXPAGe7AU", "Validation — paper simulation allowed while parser quality is verified", "VALIDATION", true), new TraderDef("meech", "Meech", "831qmkeGhfL8YpcXuhrug6nHj1YdK3aXMDQUCo85Auh1", "Research watchlist — insufficient reliable activity/history", "RESEARCH", false)};
    static final Map<String, VenueDef> DEX_PROGRAMS = new LinkedHashMap();

    static {
        DEX_PROGRAMS.put("6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P", new VenueDef("Pump.fun", "dex"));
        DEX_PROGRAMS.put("pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA", new VenueDef("PumpSwap", "dex"));
        DEX_PROGRAMS.put("675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8", new VenueDef("Raydium AMM v4", "dex"));
        DEX_PROGRAMS.put("CPMMoo8L3F4NbTegBCKVN79R4WgaQgiXoKqFDEbT3c3E", new VenueDef("Raydium CPMM", "dex"));
        DEX_PROGRAMS.put("CAMMCzo5YL8w4VFF8KVHrK22GGUQpSwci4cZU5SXsQv", new VenueDef("Raydium CLMM", "dex"));
        DEX_PROGRAMS.put("LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj", new VenueDef("Raydium LaunchLab", "dex"));
        DEX_PROGRAMS.put("LBUZKhRxPF3XUpBCjp4YzTKgLccjZhTSDM9YuVaPwxo", new VenueDef("Meteora DLMM", "dex"));
        DEX_PROGRAMS.put("whirLbMiicVdio4qvUfM5KAg6Ct8VwpLnzAZKWV8xx", new VenueDef("Orca Whirlpool", "dex"));
        DEX_PROGRAMS.put("JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4", new VenueDef("Jupiter v6", "router"));
        DEX_PROGRAMS.put("PhoeNiXZ8ByJGLkxNfZRnkUfjvmuYqLR89jjFHGqdXY", new VenueDef("Phoenix", "dex"));
    }

    static int activeSlotsUsed() {
        int n = 0;
        for (TraderDef t : TRADERS) {
            if (t.activeSlot()) {
                n++;
            }
        }
        return n;
    }

    static int validationCount() {
        int n = 0;
        for (TraderDef t : TRADERS) {
            if ("VALIDATION".equals(t.role)) {
                n++;
            }
        }
        return n;
    }

    static int researchCount() {
        int n = 0;
        for (TraderDef t : TRADERS) {
            if (t.researchOnly()) {
                n++;
            }
        }
        return n;
    }

    static TraderDef trader(String id) {
        for (TraderDef t : TRADERS) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }

    static boolean paperEnabled(String id) {
        TraderDef t = trader(id);
        return t != null && t.paperEnabled;
    }

    private Config() {
    }
}
