/*
 * SPEKTRA — Emitter.java
 * Dostu haberleşme telsizi: sabit frekans ya da frekans atlamalı (FHSS).
 * Atlama dizisi tohumlu PRNG ile üretilir; aynı tohum aynı deseni verir
 * (gerçek transec anahtarı gibi: belirli ama öngörülemez).
 * Anıl Gül · 2026
 */
package com.anilgul.spektra.sim;

import java.util.Random;

public final class Emitter {

    public enum Mode { FIXED, FHSS }

    public volatile Mode mode = Mode.FHSS;
    public volatile double powerDbm = -35.0;
    public volatile double bwKHz = 250.0;
    public volatile double fixedFreqMHz = 51.0;
    public volatile double hopRate = 100.0;
    public volatile int hopChannels = 128;
    public volatile long seed = 0x5EEDC0DEL;

    private final Band band;
    private Random rng;
    private long activeSeed;
    private double hopTimer = 0.0;
    private int currentChannel = 0;
    private double currentFreqMHz;
    private long hopCount = 0;

    public Emitter(Band band) {
        this.band = band;
        reseed();
        this.currentChannel = rng.nextInt(Math.max(1, hopChannels));
        this.currentFreqMHz = channelCentre(currentChannel);
    }

    private void reseed() {
        this.activeSeed = seed;
        this.rng = new Random(seed);
    }

    /** Atlama kanalının merkez frekansı (banda eşit aralıklı yayılmış). */
    public double channelCentre(int ch) {
        int n = Math.max(1, hopChannels);
        int c = ((ch % n) + n) % n;
        double spacing = band.widthMHz() / n;
        return band.fMin() + (c + 0.5) * spacing;
    }

    /** dt kadar ilerlet; bu adımda en az bir atlama olduysa true döner. */
    public boolean update(double dt) {
        if (seed != activeSeed) reseed();

        if (mode == Mode.FIXED) {
            currentFreqMHz = clampToBand(fixedFreqMHz);
            return false;
        }

        boolean hopped = false;
        double dwell = 1.0 / Math.max(1e-3, hopRate);
        hopTimer += dt;
        int guard = 0;
        while (hopTimer >= dwell && guard < 10_000) {   // aşırı dt / yüksek hız koruması
            hopTimer -= dwell;
            currentChannel = rng.nextInt(Math.max(1, hopChannels));
            hopCount++;
            hopped = true;
            guard++;
        }
        currentFreqMHz = channelCentre(currentChannel);
        return hopped;
    }

    private double clampToBand(double f) {
        if (f < band.fMin()) return band.fMin();
        if (f > band.fMax()) return band.fMax();
        return f;
    }

    public double currentFreqMHz() { return currentFreqMHz; }
    public int currentChannel()    { return currentChannel; }
    public long hopCount()         { return hopCount; }

    /** Yayılan lobun standart sapması (MHz). -3 dB bandını ~2.355σ sayıyoruz. */
    public double sigmaMHz() {
        return Math.max(1e-4, (bwKHz / 1000.0) / 2.355);
    }
}
