/*
 * SPEKTRA — Band.java
 * RF bandı ve frekans/bin eşlemesi. Bin sayısı sabit tutulur ki
 * bant değişince şelale tamponunu yeniden ayırmak gerekmesin.
 * Anıl Gül · 2026
 */
package com.anilgul.spektra.sim;

public final class Band {

    // Hazır bant ön ayarları (alt-MHz, üst-MHz, kısa ad)
    public static final double[][] PRESETS = {
        { 30.0,   88.0 },     // VHF taktik muharebe telsizi
        { 225.0,  400.0 },    // UHF taktik / SATCOM
        { 960.0,  1215.0 },   // L bandı (seyrüsefer / IFF)
        { 2400.0, 2483.5 }    // ISM 2.4 GHz (İHA / WiFi)
    };
    public static final String[] PRESET_NAMES = {
        "VHF 30–88 MHz", "UHF 225–400 MHz", "L 960–1215 MHz", "ISM 2.4 GHz"
    };

    private double fMin;
    private double fMax;
    public final int bins;

    public Band(double fMin, double fMax, int bins) {
        if (bins < 2) throw new IllegalArgumentException("en az 2 bin gerekli");
        this.bins = bins;
        retune(fMin, fMax);
    }

    /** Bandı yeniden ayarla (bin sayısı değişmez). */
    public void retune(double newMin, double newMax) {
        if (newMax <= newMin) throw new IllegalArgumentException("üst < alt olamaz");
        this.fMin = newMin;
        this.fMax = newMax;
    }

    public double fMin() { return fMin; }
    public double fMax() { return fMax; }
    public double mid()  { return 0.5 * (fMin + fMax); }
    public double widthMHz()    { return fMax - fMin; }
    public double binWidthMHz() { return widthMHz() / bins; }

    public double binToFreq(int i) { return fMin + (i + 0.5) * binWidthMHz(); }

    public int freqToBin(double f) {
        int i = (int) Math.floor((f - fMin) / binWidthMHz());
        return i < 0 ? 0 : (i >= bins ? bins - 1 : i);
    }

    /** Yatay konum [0,1]. */
    public double freqToFraction(double f) {
        double x = (f - fMin) / widthMHz();
        return x < 0 ? 0 : (x > 1 ? 1 : x);
    }
}
