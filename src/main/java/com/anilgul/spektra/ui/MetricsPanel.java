/*
 * SPEKTRA — MetricsPanel.java
 * Sağ ölçüm paneli: link durumu, SJNR ve PDR göstergeleri, telemetri ve
 * iki tehdide göre okunan sade bir taktik değerlendirme.
 * Anıl Gül · 2026
 */
package com.anilgul.spektra.ui;

import com.anilgul.spektra.sim.Emitter;
import com.anilgul.spektra.sim.Jammer;
import com.anilgul.spektra.sim.SimEngine;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MetricsPanel extends VBox {

    private final Label status = new Label("—");
    private final Label statusSub = new Label("");
    private final VBox tile = new VBox(2, status, statusSub);

    private final SimpleDoubleProperty sjnrFrac = new SimpleDoubleProperty(0);
    private final SimpleDoubleProperty pdrFrac  = new SimpleDoubleProperty(0);
    private final Label sjnrVal = new Label("—");
    private final Label pdrVal  = new Label("—");

    private final Label rate = valueLabel();
    private final Label hops = valueLabel();
    private final Label clock = valueLabel();
    private final Label freq = valueLabel();
    private final Label chan = valueLabel();
    private final Label threats = new Label("—");
    private final Label verdict = new Label("");

    public MetricsPanel() {
        setSpacing(11);
        setPadding(new Insets(12));
        setFillWidth(true);
        getStyleClass().add("metrics-panel");

        status.getStyleClass().add("status-big");
        statusSub.getStyleClass().add("status-sub");
        tile.getStyleClass().addAll("card", "status-tile");
        tile.setAlignment(Pos.CENTER);
        tile.setPadding(new Insets(14, 12, 14, 12));

        VBox meters = card("BAĞLANTI KALİTESİ");
        meters.getChildren().addAll(
                meterRow("SJNR", sjnrVal, sjnrFrac, "sjnr-fill"),
                meterRow("PDR (paket geçiş)", pdrVal, pdrFrac, "pdr-fill"));

        VBox tele = card("TELEMETRİ");
        tele.getChildren().addAll(
                teleRow("Efektif Hız", rate),
                teleRow("Toplam Atlama", hops),
                teleRow("Sim Süresi", clock),
                teleRow("Emitör Frekansı", freq),
                teleRow("Aktif Kanal", chan));

        VBox threat = card("AKTİF TEHDİT");
        threats.getStyleClass().add("threat-line");
        threats.setWrapText(true);
        threat.getChildren().add(threats);

        VBox verdictCard = card("DEĞERLENDİRME");
        verdict.getStyleClass().add("verdict");
        verdict.setWrapText(true);
        verdictCard.getChildren().add(verdict);

        getChildren().addAll(tile, meters, tele, threat, verdictCard);
    }

    public void update(SimEngine e) {
        boolean up = e.linkUp();
        status.setText(up ? "LINK AKTİF" : "LINK ENGELLENDİ");
        statusSub.setText(up ? "haberleşme sürüyor" : "veri akışı kesildi");
        tile.getStyleClass().removeAll("up", "down");
        tile.getStyleClass().add(up ? "up" : "down");

        double sjnr = e.sjnrDb();
        sjnrFrac.set(clamp01((sjnr + 20) / 65.0));
        sjnrVal.setText(fmt(sjnr, 1) + " dB");
        pdrFrac.set(clamp01(e.pdr()));
        pdrVal.setText(fmt(e.pdr() * 100, 0) + " %");

        rate.setText(fmt(e.effectiveRateKbps(), 1) + " kbps");
        hops.setText(Long.toString(e.emitter.hopCount()));
        clock.setText(String.format(Locale.US, "%02d:%02d", (int) e.time() / 60, (int) e.time() % 60));
        freq.setText(fmt(e.emitter.currentFreqMHz(), 3) + " MHz");
        chan.setText(e.emitter.mode == Emitter.Mode.FHSS ? "#" + e.emitter.currentChannel() : "sabit");

        threats.setText(threatLine(e));
        verdict.setText(verdictText(e));
    }

    // ---------- metin üretimi ----------
    private static String threatLine(SimEngine e) {
        String a = shortName(e.jammers[0].type);
        String b = shortName(e.jammers[1].type);
        return "A: " + a + "     B: " + b;
    }

    private static String shortName(Jammer.Type t) {
        return switch (t) {
            case OFF -> "—";
            case BARRAGE -> "Barrage";
            case SPOT -> "Spot";
            case SWEEP -> "Sweep";
            case FOLLOWER -> "Takip";
        };
    }

    private static String verdictText(SimEngine e) {
        List<Jammer.Type> act = new ArrayList<>();
        for (Jammer j : e.jammers) if (j.type != Jammer.Type.OFF) act.add(j.type);
        if (act.isEmpty()) return "Tehdit yok. Link temiz — sinyal tam güçte iletiliyor.";

        double p = e.pdr();
        String base;
        if (p > 0.9) base = "Link büyük ölçüde ayakta.";
        else if (p > 0.6) base = "Link zorlanıyor ama kopmuyor.";
        else if (p > 0.2) base = "Link ağır baskı altında; iletişim aralıklı.";
        else base = "Link etkin biçimde engellendi.";

        boolean fhss = e.emitter.mode == Emitter.Mode.FHSS;
        String hint;
        if (act.contains(Jammer.Type.FOLLOWER)) {
            hint = fhss
                ? (p > 0.6 ? "Atlama hızı, takip eden tehdidin tepki süresini geçiyor."
                           : "Atlama takip edene yetişemiyor; atlama hızını artır ya da kanal sayısını çoğalt.")
                : "Sabit kanaldasın; takip eden tehdit kolayca kilitleniyor — FHSS'e geç.";
        } else if (act.contains(Jammer.Type.SPOT)) {
            hint = (!fhss && p < 0.4)
                ? "Sabit kanal nokta karıştırmaya kilitlenmiş; FHSS ile bu tuzaktan çık."
                : "Nokta karıştırma tek frekansı vuruyor; atlama onu boşa düşürüyor.";
        } else if (act.contains(Jammer.Type.BARRAGE)) {
            hint = p > 0.6 ? "Barrage enerjiyi tüm banda yayıyor; kanal başına etkisi zayıf."
                           : "Barrage tüm bandı bastıracak kadar güçlü; ancak bu çok güç ister.";
        } else {
            hint = "Tarayan tehdit sinyali yalnızca üzerinden geçerken kısa süre yakalıyor.";
        }
        return base + " " + hint;
    }

    // ---------- yardımcılar ----------
    private VBox card(String title) {
        VBox v = new VBox(9);
        v.getStyleClass().add("card");
        v.setPadding(new Insets(11, 12, 12, 12));
        Label t = new Label(title);
        t.getStyleClass().add("panel-title");
        v.getChildren().add(t);
        return v;
    }

    private HBox meterRow(String name, Label val, SimpleDoubleProperty frac, String fillClass) {
        Label n = new Label(name);
        n.getStyleClass().add("meter-name");
        val.getStyleClass().add("meter-val");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(n, spacer, val);
        head.setAlignment(Pos.CENTER_LEFT);

        Region fill = new Region();
        fill.getStyleClass().addAll("meter-fill", fillClass);
        Pane track = new Pane(fill);
        track.getStyleClass().add("meter-track");
        track.setPrefHeight(8);
        track.setMinHeight(8);
        fill.prefHeightProperty().bind(track.heightProperty());
        fill.prefWidthProperty().bind(track.widthProperty().multiply(frac));

        VBox row = new VBox(4, head, track);
        return new HBox(row) {{ setFillHeight(true); HBox.setHgrow(row, Priority.ALWAYS); }};
    }

    private HBox teleRow(String key, Label val) {
        Label k = new Label(key);
        k.getStyleClass().add("tele-key");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(k, spacer, val);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("tele-row");
        return row;
    }

    private static Label valueLabel() { Label l = new Label("—"); l.getStyleClass().add("tele-val"); return l; }

    private static double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
    private static String fmt(double v, int dp) { return String.format(Locale.US, "%." + dp + "f", v); }
}
