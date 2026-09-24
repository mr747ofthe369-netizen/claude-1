package com.memetaillab.beta1;


/* compiled from: Models.java */
class Position {
    long closeTime;
    double closedPnl;
    double entryEffectivePrice;
    double entryMarketPrice;
    long entryTime;
    boolean hit20;
    boolean hit7;
    long id;
    double lastLiquidityUsd;
    double lastPrice;
    long lastUpdate;
    double originalQty;
    double realizedProceeds;
    double remainingQty;
    double signalPrice;
    double stakeUsd;
    boolean trailActive;
    String book = "";
    String mint = "";
    String symbol = "";
    String name = "";
    String venue = "";
    String pool = "";
    String state = "";
    double highX = 1.0d;
    double floorX = 0.675d;
    double trailPeakX = 1.0d;

    Position() {
    }
}
