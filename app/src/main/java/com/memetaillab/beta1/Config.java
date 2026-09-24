package com.memetaillab.beta1;


final class Config {
    static final String APP = "Meme Tail Lab Beta 2";
    static final double FLOOR_AFTER_2X = 1.5d;
    static final double FLOOR_AFTER_3X = 2.0d;
    static final double FLOOR_AFTER_5X = 2.5d;
    static final String GECKO = "https://api.geckoterminal.com/api/v2";
    static final long MAX_HOLD_MS = 172800000;
    static final double PARTIAL_20X = 0.025d;
    static final double PARTIAL_7X = 0.1d;
    static final String PUMP_FUN_PROGRAM = "6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P";
    static final String PUMP_SWAP_PROGRAM = "pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA";
    static final String RAYDIUM_API = "https://api-v3.raydium.io";
    static final String RAYDIUM_SWAP = "https://transaction-v1.raydium.io";
    static final String RAY_AMM_V4 = "675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8";
    static final String RAY_CLMM = "CAMMCzo5YL8w4VFF8KVHrK22GGUsp5VTaW7grrKgrWqK";
    static final String RAY_CPMM = "CPMMoo8L3F4NbTegBCKVNunggL7H1ZpdTHKxQB5qKP1C";
    static final String RAY_LAUNCHLAB = "LanMV9sAd7wArD4vJFi2qDdfnVhFxYSUg6eADduJ3uj";
    static final double RUNNER_ACTIVATE = 7.0d;
    static final double RUNNER_TRAIL = 0.325d;
    static final double START_BALANCE = 1000.0d;
    static final double STOP_INITIAL = 0.675d;
    static final String USDC = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v";
    static final String USDT = "Es9vMFrzaCERmJfrF4H2FYDkgFdmHqkV6P5dY1Y9Fgr";
    static final String WSOL = "So11111111111111111111111111111111111111112";
    static final String[] BOOKS = {"RAYDIUM", "PUMPSWAP", "PUMPFUN"};
    static final String MODE_OFF = "OFF";
    static final String MODE_MONITOR = "MONITOR";
    static final String MODE_PAPER = "PAPER";
    static final String MODE_LIVE = "LIVE";
    static final String[] VENUE_MODES = {MODE_OFF, MODE_MONITOR, MODE_PAPER, MODE_LIVE};
    static final String DELAY_AUTO = "AUTOMATED 0s";
    static final String DELAY_HUMAN = "HUMAN SIM 15s";
    static final String DELAY_CUSTOM = "CUSTOM";
    static final String[] DELAY_MODES = {DELAY_AUTO, DELAY_HUMAN, DELAY_CUSTOM};

    static boolean quoteMint(String mint) {
        return WSOL.equals(mint) || USDC.equals(mint) || USDT.equals(mint);
    }

    static String bookForDex(String dex) {
        String d = dex == null ? "" : dex.toLowerCase();
        if (d.contains("raydium")) {
            return "RAYDIUM";
        }
        if (d.contains("pumpswap")) {
            return "PUMPSWAP";
        }
        if (!d.contains("pump-fun") && !d.equals("pumpfun") && !d.contains("pump_fun")) {
            return "";
        }
        return "PUMPFUN";
    }

    static String venueLabel(String dex) {
        String d = dex == null ? "" : dex.toLowerCase();
        if (d.contains("launchlab")) {
            return "Raydium LaunchLab";
        }
        if (d.contains("clmm")) {
            return "Raydium CLMM";
        }
        if (d.contains("cpmm")) {
            return "Raydium CPMM";
        }
        if (d.contains("raydium")) {
            return "Raydium AMM / pool";
        }
        if (d.contains("pumpswap")) {
            return "PumpSwap";
        }
        if (d.contains("pump-fun") || d.contains("pumpfun") || d.contains("pump_fun")) {
            return "Pump.fun bonding curve";
        }
        return dex == null ? "Unknown" : dex;
    }

    private Config() {
    }
}
