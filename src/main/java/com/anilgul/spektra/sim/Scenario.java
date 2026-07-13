/*
 * SPEKTRA — Scenario.java
 * Senaryo yakala/uygula (.spx düz metin) ve hazır gösteri senaryoları.
 * Anıl Gül · 2026
 */
package com.anilgul.spektra.sim;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class Scenario {

    private Scenario() {}

    public static final String[] PRESET_LABELS = {
        "VHF · Takip vs Hızlı Atlama",
        "VHF · Takip vs Yavaş Atlama",
        "VHF · Spot Tuzağı (sabit)",
        "VHF · Barrage Baskısı",
        "UHF · Katmanlı Tehdit",
        "ISM 2.4 GHz · İHA / Sweep"
    };

    // ---- Hazır senaryolar ----
    public static void applyPreset(SimEngine e, int i) {
        Emitter m = e.emitter;
        Jammer a = e.jammers[0], b = e.jammers[1];
        e.noiseFloorDbm = -112; e.linkThresholdDb = 9; e.baseRateKbps = 64;
        b.type = Jammer.Type.OFF;

        switch (i) {
            case 0 -> { // takip edeni geçen hızlı link
                e.band.retune(30, 88);
                m.mode = Emitter.Mode.FHSS; m.hopRate = 300; m.hopChannels = 128; m.bwKHz = 250; m.powerDbm = -35;
                a.type = Jammer.Type.FOLLOWER; a.reactionMs = 12; a.powerDbm = -20; a.bwKHz = 400;
            }
            case 1 -> { // takibe yakalanan yavaş link
                e.band.retune(30, 88);
                m.mode = Emitter.Mode.FHSS; m.hopRate = 25; m.hopChannels = 128; m.bwKHz = 250; m.powerDbm = -35;
                a.type = Jammer.Type.FOLLOWER; a.reactionMs = 12; a.powerDbm = -20; a.bwKHz = 400;
            }
            case 2 -> { // sabit link + nokta karıştırma (çöker; FHSS'e alınca kaçar)
                e.band.retune(30, 88);
                m.mode = Emitter.Mode.FIXED; m.fixedFreqMHz = 51; m.bwKHz = 250; m.powerDbm = -35;
                a.type = Jammer.Type.SPOT; a.centerFreqMHz = 51; a.powerDbm = -15; a.bwKHz = 300;
            }
            case 3 -> { // geniş bant baskı (yeterli güçle her şeyi bastırır)
                e.band.retune(30, 88);
                m.mode = Emitter.Mode.FHSS; m.hopRate = 100; m.hopChannels = 128; m.bwKHz = 250; m.powerDbm = -35;
                a.type = Jammer.Type.BARRAGE; a.powerDbm = -11; a.bwKHz = 1200;
            }
            case 4 -> { // UHF, iki eşzamanlı tehdit — hızlı tepkili takip + barrage; link zorlanır ama ayakta
                e.band.retune(225, 400);
                m.mode = Emitter.Mode.FHSS; m.hopRate = 160; m.hopChannels = 200; m.bwKHz = 300; m.powerDbm = -32;
                a.type = Jammer.Type.FOLLOWER; a.centerFreqMHz = e.band.mid(); a.reactionMs = 5; a.powerDbm = -16; a.bwKHz = 600;
                b.type = Jammer.Type.BARRAGE;  b.powerDbm = -20; b.bwKHz = 3000;
            }
            case 5 -> { // 2.4 GHz İHA bandı, tarama
                e.band.retune(2400, 2483.5);
                m.mode = Emitter.Mode.FHSS; m.hopRate = 80; m.hopChannels = 64; m.bwKHz = 800; m.powerDbm = -34;
                a.type = Jammer.Type.SWEEP; a.centerFreqMHz = e.band.mid(); a.sweepRateMHzPerS = 60; a.powerDbm = -18; a.bwKHz = 1500;
            }
            default -> { }
        }
        // Bant-dışı kalmış frekansları ortaya çek
        clampFreqsToBand(e);
        e.resetState();
        e.log("Senaryo: " + PRESET_LABELS[Math.max(0, Math.min(i, PRESET_LABELS.length - 1))]);
    }

    static void clampFreqsToBand(SimEngine e) {
        double lo = e.band.fMin(), hi = e.band.fMax();
        if (e.emitter.fixedFreqMHz < lo || e.emitter.fixedFreqMHz > hi) e.emitter.fixedFreqMHz = e.band.mid();
        for (Jammer j : e.jammers)
            if (j.centerFreqMHz < lo || j.centerFreqMHz > hi) j.centerFreqMHz = e.band.mid();
    }

    // ---- .spx yakala ----
    public static String capture(SimEngine e) {
        Emitter m = e.emitter;
        Jammer a = e.jammers[0], b = e.jammers[1];
        StringBuilder s = new StringBuilder();
        s.append("# SPEKTRA senaryo dosyasi (.spx)\n");
        kv(s, "band.min", m6(e.band.fMin()));
        kv(s, "band.max", m6(e.band.fMax()));
        kv(s, "noise", m6(e.noiseFloorDbm));
        kv(s, "threshold", m6(e.linkThresholdDb));
        kv(s, "rate", m6(e.baseRateKbps));
        kv(s, "em.mode", m.mode.name());
        kv(s, "em.power", m6(m.powerDbm));
        kv(s, "em.bw", m6(m.bwKHz));
        kv(s, "em.fixed", m6(m.fixedFreqMHz));
        kv(s, "em.hop", m6(m.hopRate));
        kv(s, "em.chan", Integer.toString(m.hopChannels));
        captureJammer(s, "jA", a);
        captureJammer(s, "jB", b);
        return s.toString();
    }

    private static void captureJammer(StringBuilder s, String p, Jammer j) {
        kv(s, p + ".type", j.type.name());
        kv(s, p + ".power", m6(j.powerDbm));
        kv(s, p + ".bw", m6(j.bwKHz));
        kv(s, p + ".center", m6(j.centerFreqMHz));
        kv(s, p + ".sweep", m6(j.sweepRateMHzPerS));
        kv(s, p + ".react", m6(j.reactionMs));
    }

    // ---- .spx uygula ----
    public static void apply(SimEngine e, String text) {
        Map<String, String> map = new HashMap<>();
        for (String line : text.split("\\R")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            map.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
        }

        double bmin = d(map, "band.min", e.band.fMin());
        double bmax = d(map, "band.max", e.band.fMax());
        if (bmax > bmin) e.band.retune(bmin, bmax);

        e.noiseFloorDbm   = d(map, "noise", e.noiseFloorDbm);
        e.linkThresholdDb = d(map, "threshold", e.linkThresholdDb);
        e.baseRateKbps    = d(map, "rate", e.baseRateKbps);

        Emitter m = e.emitter;
        m.mode        = "FIXED".equalsIgnoreCase(map.getOrDefault("em.mode", m.mode.name()))
                        ? Emitter.Mode.FIXED : Emitter.Mode.FHSS;
        m.powerDbm    = d(map, "em.power", m.powerDbm);
        m.bwKHz       = d(map, "em.bw", m.bwKHz);
        m.fixedFreqMHz= d(map, "em.fixed", m.fixedFreqMHz);
        m.hopRate     = d(map, "em.hop", m.hopRate);
        m.hopChannels = (int) d(map, "em.chan", m.hopChannels);

        applyJammer(map, "jA", e.jammers[0]);
        applyJammer(map, "jB", e.jammers[1]);

        clampFreqsToBand(e);
        e.resetState();
        e.log("Senaryo dosyadan yüklendi");
    }

    private static void applyJammer(Map<String, String> map, String p, Jammer j) {
        j.type = parseType(map.getOrDefault(p + ".type", j.type.name()));
        j.powerDbm        = d(map, p + ".power", j.powerDbm);
        j.bwKHz           = d(map, p + ".bw", j.bwKHz);
        j.centerFreqMHz   = d(map, p + ".center", j.centerFreqMHz);
        j.sweepRateMHzPerS= d(map, p + ".sweep", j.sweepRateMHzPerS);
        j.reactionMs      = d(map, p + ".react", j.reactionMs);
    }

    private static Jammer.Type parseType(String s) {
        try { return Jammer.Type.valueOf(s.trim().toUpperCase(Locale.ROOT)); }
        catch (Exception ex) { return Jammer.Type.OFF; }
    }

    private static void kv(StringBuilder s, String k, String v) { s.append(k).append('=').append(v).append('\n'); }
    private static String m6(double v) { return String.format(Locale.US, "%.4f", v); }
    private static double d(Map<String, String> map, String k, double def) {
        try { return Double.parseDouble(map.get(k)); } catch (Exception ex) { return def; }
    }
}
