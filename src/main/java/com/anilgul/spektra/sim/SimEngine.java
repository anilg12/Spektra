/*
 * SPEKTRA — SimEngine.java
 * Simülasyon çekirdeği. Fizik, kare hızından bağımsız olsun diye 1 ms'lik
 * iç alt-adımlarla ilerletilir; böylece atlama süresi ve karıştırıcı tepki
 * süresi (ms) doğru çözülür. Her alt-adımda SJNR ile link değerlendirilir,
 * PDR zaman-sabitli yumuşatma (EMA) ile tutulur. Spektrum kare başına bir
 * kez kurulur (çizim için). Doktrin modelden kendiliğinden çıkar.
 * Anıl Gül · 2026
 */
package com.anilgul.spektra.sim;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Random;

public final class SimEngine {

    private static final double SUB_DT   = 0.001;   // 1 ms iç adım
    private static final double PDR_TAU  = 1.5;     // PDR yumuşatma zaman sabiti (sn)

    public final Band band;
    public final Emitter emitter;
    public final Jammer[] jammers;                  // A ve B (katmanlı tehdit)

    public volatile double noiseFloorDbm = -112.0;
    public volatile double linkThresholdDb = 9.0;
    public volatile double baseRateKbps = 64.0;
    public volatile boolean paused = false;
    public volatile double timeScale = 1.0;

    private final double[] psdDbm;
    private double time = 0.0;
    private double sjnrDb = 0.0;
    private boolean linkUp = true;
    private double pdrEma = 1.0;

    // Görev geçmişi (kayan grafik)
    private static final int HIST_CAP = 1200;       // kare başına 1 örnek
    private final double[] histPdr  = new double[HIST_CAP];
    private final double[] histSjnr = new double[HIST_CAP];
    private int hHead = 0, hCount = 0;

    // Olay günlüğü (kare düzeyinde geçişler)
    private final ArrayDeque<String> pending = new ArrayDeque<>();
    private boolean prevInit = false, prevUp = true;

    private final Random noiseRng = new Random(1234567L);

    public SimEngine(Band band) {
        this.band = band;
        this.emitter = new Emitter(band);
        this.jammers = new Jammer[]{ new Jammer(band), new Jammer(band) };
        this.psdDbm = new double[band.bins];
    }

    /** dt kadar ilerlet. Duraklatma ve hız ölçeği çağıran tarafta uygulanır. */
    public void step(double dt) {
        if (dt <= 0) { buildSpectrum(); return; }

        int n = Math.max(1, (int) Math.ceil(dt / SUB_DT));
        double sdt = dt / n;
        for (int s = 0; s < n; s++) {
            emitter.update(sdt);
            for (Jammer j : jammers) j.update(sdt, emitter.currentFreqMHz(), time);
            evaluateLink();
            double a = sdt / PDR_TAU;
            if (a > 1) a = 1;
            pdrEma += ((linkUp ? 1.0 : 0.0) - pdrEma) * a;
            time += sdt;
        }

        buildSpectrum();     // son duruma göre çizim spektrumu
        pushHistory();       // kare başına bir örnek
        detectEvents();      // kare sonu durumundan geçiş
    }

    private void buildSpectrum() {
        final double binW = band.binWidthMHz();
        final double noiseLin = Jammer.dbmToMw(noiseFloorDbm);
        final double sigPeak = Jammer.dbmToMw(emitter.powerDbm);
        final double fc = emitter.currentFreqMHz();
        final double twoSig2 = 2.0 * emitter.sigmaMHz() * emitter.sigmaMHz();

        for (int i = 0; i < band.bins; i++) {
            double f = band.binToFreq(i);
            double jitter = 1.0 + 0.35 * (noiseRng.nextDouble() - 0.5);
            double lin = noiseLin * jitter;
            double d = f - fc;
            lin += sigPeak * Math.exp(-(d * d) / twoSig2);
            for (Jammer j : jammers) lin += j.contributionLin(f, binW);
            psdDbm[i] = 10.0 * Math.log10(Math.max(lin, 1e-18));
        }
    }

    private void evaluateLink() {
        double sigBw = emitter.bwKHz / 1000.0;
        double sLin = Jammer.dbmToMw(emitter.powerDbm);
        double nLin = Jammer.dbmToMw(noiseFloorDbm);
        double jLin = 0.0;
        for (Jammer j : jammers) jLin += j.inBandLin(emitter.currentFreqMHz(), sigBw);

        double sjnr = sLin / (nLin + jLin);
        sjnrDb = 10.0 * Math.log10(Math.max(sjnr, 1e-18));
        linkUp = sjnrDb >= linkThresholdDb;
    }

    private void pushHistory() {
        histPdr[hHead]  = pdrEma;
        histSjnr[hHead] = sjnrDb;
        hHead = (hHead + 1) % HIST_CAP;
        if (hCount < HIST_CAP) hCount++;
    }

    private void detectEvents() {
        if (!prevInit) { prevUp = linkUp; prevInit = true; return; }
        if (linkUp != prevUp) {
            String t = stamp();
            pending.add(linkUp ? t + "  ▲ LINK GERİ KAZANILDI" : t + "  ▼ LINK KAYBEDİLDİ");
            prevUp = linkUp;
            if (pending.size() > 400) pending.pollFirst();
        }
    }

    /** Kullanıcı olayını günlüğe düş (bant/tehdit değişimi gibi). */
    public void log(String msg) {
        pending.add(stamp() + "  · " + msg);
        if (pending.size() > 400) pending.pollFirst();
    }

    private String stamp() {
        return String.format(Locale.US, "%02d:%02d", (int) time / 60, (int) time % 60);
    }

    public boolean hasEvents() { return !pending.isEmpty(); }
    public String pollEvent()  { return pending.poll(); }

    /** Bandı değiştir; bant-dışı frekansları ortala ve durumu sıfırla. */
    public void applyBand(double fMin, double fMax, String label) {
        band.retune(fMin, fMax);
        Scenario.clampFreqsToBand(this);
        resetState();
        log("Bant: " + label);
    }

    /** Durumu temizle (parametreler korunur). */
    public void resetState() {
        time = 0; sjnrDb = 0; linkUp = true; pdrEma = 1.0;
        hHead = hCount = 0;
        prevInit = false; prevUp = true;
        pending.clear();
    }

    // --- Salt-okunur erişim ---
    public double[] psd()   { return psdDbm; }
    public double time()    { return time; }
    public double sjnrDb()  { return sjnrDb; }
    public boolean linkUp() { return linkUp; }

    public double pdr()               { return pdrEma; }
    public double denialRatio()       { return 1.0 - pdrEma; }
    public double effectiveRateKbps() { return baseRateKbps * pdrEma; }

    public int histCount() { return hCount; }
    public int histCap()   { return HIST_CAP; }
    public double pdrAt(int i)  { return histPdr[idx(i)]; }
    public double sjnrAt(int i) { return histSjnr[idx(i)]; }
    private int idx(int i) { return ((hHead - hCount + i) % HIST_CAP + HIST_CAP) % HIST_CAP; }
}
