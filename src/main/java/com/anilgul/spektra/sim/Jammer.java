/*
 * SPEKTRA — Jammer.java
 * Düşman karıştırıcı. Dört doktrin: barrage (geniş bant), spot (nokta),
 * sweep (tarama), follower (takip/repeater — sonlu tepki gecikmesiyle).
 * Takip edeni geçmenin yolu: karıştırıcının tepkisinden hızlı atlamak.
 * Anıl Gül · 2026
 */
package com.anilgul.spektra.sim;

public final class Jammer {

    public enum Type { OFF, BARRAGE, SPOT, SWEEP, FOLLOWER }

    public volatile Type type = Type.OFF;
    public volatile double powerDbm = -20.0;
    public volatile double bwKHz = 400.0;
    public volatile double centerFreqMHz = 51.0;
    public volatile double sweepRateMHzPerS = 40.0;
    public volatile double reactionMs = 12.0;

    private final Band band;
    private double currentCenterMHz;
    private int sweepDir = 1;

    // Hedefin son (zaman, frekans) izi — takip edenin gecikmeli okuması için.
    private static final int HIST = 4096;
    private final double[] histT = new double[HIST];
    private final double[] histF = new double[HIST];
    private int head = 0;
    private int count = 0;

    public Jammer(Band band) {
        this.band = band;
        this.currentCenterMHz = centerFreqMHz;
    }

    public void update(double dt, double targetFreqMHz, double time) {
        histT[head] = time;
        histF[head] = targetFreqMHz;
        head = (head + 1) % HIST;
        if (count < HIST) count++;

        switch (type) {
            case OFF:
            case BARRAGE:
                currentCenterMHz = centerFreqMHz;
                break;
            case SPOT:
                currentCenterMHz = clamp(centerFreqMHz);
                break;
            case SWEEP:
                currentCenterMHz += sweepDir * sweepRateMHzPerS * dt;
                if (currentCenterMHz >= band.fMax()) { currentCenterMHz = band.fMax(); sweepDir = -1; }
                if (currentCenterMHz <= band.fMin()) { currentCenterMHz = band.fMin(); sweepDir =  1; }
                break;
            case FOLLOWER:
                currentCenterMHz = targetAt(time - reactionMs / 1000.0, targetFreqMHz);
                break;
        }
    }

    /** tQuery anındaki (veya hemen öncesindeki) hedef frekansı — yeniden eskiye tarar. */
    private double targetAt(double tQuery, double fallback) {
        for (int k = 0; k < count; k++) {
            int idx = ((head - 1 - k) % HIST + HIST) % HIST;
            if (histT[idx] <= tQuery) return histF[idx];
        }
        return fallback;
    }

    /** Bir bine düşen lineer güç (mW). */
    public double contributionLin(double freqMHz, double binWidthMHz) {
        if (type == Type.OFF) return 0.0;
        double pLin = dbmToMw(powerDbm);
        if (type == Type.BARRAGE) return pLin / band.bins;          // banda düz yayılmış taban

        double sigma = Math.max(1e-4, (bwKHz / 1000.0) / 2.355);
        double d = freqMHz - currentCenterMHz;
        return pLin * Math.exp(-0.5 * (d * d) / (sigma * sigma));
    }

    /** sigFreq'e ayarlı alıcıya sunulan bant-içi karıştırma gücü (mW). */
    public double inBandLin(double sigFreqMHz, double sigBwMHz) {
        if (type == Type.OFF) return 0.0;
        double pLin = dbmToMw(powerDbm);
        if (type == Type.BARRAGE) {
            double frac = Math.min(1.0, sigBwMHz / band.widthMHz());
            return pLin * frac;
        }
        double sigma = Math.max(1e-4, (bwKHz / 1000.0) / 2.355);
        double d = sigFreqMHz - currentCenterMHz;
        return pLin * Math.exp(-0.5 * (d * d) / (sigma * sigma));
    }

    public double currentCenterMHz() { return currentCenterMHz; }

    private double clamp(double f) {
        if (f < band.fMin()) return band.fMin();
        if (f > band.fMax()) return band.fMax();
        return f;
    }

    static double dbmToMw(double dbm) { return Math.pow(10.0, dbm / 10.0); }
}
