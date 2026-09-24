package com.memetaillab.beta1;


/* compiled from: Models.java */
class PoolSnap {
    int buyersH1;
    int buysH1;
    long createdMs;
    long fetchedMs;
    double liquidityUsd;
    double momentumH1;
    int sellersH1;
    int sellsH1;
    double volumeH1;
    String pool = "";
    String mint = "";
    String symbol = "";
    String name = "";
    String quoteMint = "";
    String dexId = "";
    String venue = "";
    String book = "";
    double priceUsd = Double.NaN;

    PoolSnap() {
    }

    boolean ok() {
        return !this.pool.isEmpty() && !this.mint.isEmpty() && Double.isFinite(this.priceUsd) && this.priceUsd > 0.0d;
    }

    double ageMin(long now) {
        if (this.createdMs <= 0) {
            return Double.NaN;
        }
        return (now - this.createdMs) / 60000.0d;
    }

    double buySell() {
        return ((double) this.buysH1) / ((double) Math.max(1, this.sellsH1));
    }
}
