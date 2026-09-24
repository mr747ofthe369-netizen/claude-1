package com.memetaillab.beta1;


/* compiled from: Models.java */
class LivePosition {
    long closeTime;
    double closedPnlUsd;
    int decimals;
    long entryLamports;
    double entryPriceUsd;
    long entryTime;
    boolean hit20;
    boolean hit7;
    long id;
    double lastPriceUsd;
    long lastUpdate;
    long originalRaw;
    long realizedLamports;
    double realizedUsd;
    long remainingRaw;
    double stakeUsd;
    boolean trailActive;
    String book = "";
    String mint = "";
    String symbol = "";
    String venue = "";
    String pool = "";
    String state = "";
    String buySig = "";
    String lastSellSig = "";
    double highX = 1.0d;
    double floorX = 0.675d;
    double trailPeakX = 1.0d;

    LivePosition() {
    }
}
