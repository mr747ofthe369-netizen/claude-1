package com.benknight.mwsl;

import java.util.ArrayList;
import java.util.List;

/* compiled from: Models.java */
class TradeSignal {
    long detectedAt;
    String id;
    String mint;
    double postTokenQty;
    double preTokenQty;
    String side;
    String signature;
    long slot;
    long time;
    double tokenQty;
    String traderId;
    String symbol = "";
    String quoteMint = "";
    String quoteUnit = "";
    String source = "solana-rpc";
    String priceSource = "";
    double sellFraction = Double.NaN;
    double quoteAmount = Double.NaN;
    double signalPriceUsd = Double.NaN;
    final List<String> dex = new ArrayList();
    final List<String> router = new ArrayList();

    TradeSignal() {
    }
}
