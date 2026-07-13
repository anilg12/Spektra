/*
 * SPEKTRA — ControlPanel.java
 * Sol kontrol paneli: dostu emitör, iki tehdit (sekmeli) ve ortam ayarları.
 * Bant değişince App tarafından yeniden kurulur; böylece frekans kaydırıcıları
 * her zaman aktif bandın sınırlarında olur. Duraklatma araç çubuğunda.
 * Anıl Gül · 2026
 */
package com.anilgul.spektra.ui;

import com.anilgul.spektra.sim.Emitter;
import com.anilgul.spektra.sim.Jammer;
import com.anilgul.spektra.sim.SimEngine;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.Locale;
import java.util.function.DoubleConsumer;

public final class ControlPanel extends VBox {

    private final SimEngine engine;

    public ControlPanel(SimEngine engine) {
        this.engine = engine;
        setSpacing(12);
        setPadding(new Insets(12));
        setFillWidth(true);
        getStyleClass().add("control-panel");

        getChildren().addAll(emitterCard(), threatCard(), environmentCard());
    }

    // ---------- Emitör ----------
    private VBox emitterCard() {
        VBox card = card("DOSTU EMİTÖR");
        Emitter m = engine.emitter;

        ToggleGroup grp = new ToggleGroup();
        ToggleButton fixed = seg("SABİT", grp);
        ToggleButton fhss  = seg("FHSS", grp);
        (m.mode == Emitter.Mode.FIXED ? fixed : fhss).setSelected(true);
        HBox modeRow = new HBox(fixed, fhss);
        modeRow.getStyleClass().add("segmented");
        HBox.setHgrow(fixed, Priority.ALWAYS);
        HBox.setHgrow(fhss, Priority.ALWAYS);

        VBox power = slider("Güç", "dBm", -60, -10, m.powerDbm, 0, v -> m.powerDbm = v);
        VBox bw    = slider("Bant Genişliği", "kHz", 50, 1000, m.bwKHz, 0, v -> m.bwKHz = v);
        VBox fx    = slider("Sabit Frekans", "MHz", engine.band.fMin(), engine.band.fMax(),
                            clampBand(m.fixedFreqMHz), 2, v -> m.fixedFreqMHz = v);
        VBox hop   = slider("Atlama Hızı", "atlama/sn", 5, 500, m.hopRate, 0, v -> m.hopRate = v);
        VBox chan  = slider("Kanal Sayısı", "", 16, 256, m.hopChannels, 0, v -> m.hopChannels = (int) v);

        Runnable refresh = () -> {
            boolean fh = fhss.isSelected();
            fx.setDisable(fh);
            hop.setDisable(!fh);
            chan.setDisable(!fh);
        };
        grp.selectedToggleProperty().addListener((o, was, now) -> {
            if (now == null) { if (was != null) was.setSelected(true); return; }
            m.mode = fhss.isSelected() ? Emitter.Mode.FHSS : Emitter.Mode.FIXED;
            refresh.run();
        });
        refresh.run();

        card.getChildren().addAll(modeRow, power, bw, fx, hop, chan);
        return card;
    }

    // ---------- Tehditler ----------
    private VBox threatCard() {
        VBox card = card("TEHDİT KARIŞTIRICILARI");
        TabPane tabs = new TabPane();
        tabs.getStyleClass().add("threat-tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(jammerTab("TEHDİT A", engine.jammers[0]),
                              jammerTab("TEHDİT B", engine.jammers[1]));
        card.getChildren().add(tabs);
        return card;
    }

    private Tab jammerTab(String name, Jammer j) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10, 2, 2, 2));

        ComboBox<Jammer.Type> type = new ComboBox<>();
        type.getItems().addAll(Jammer.Type.values());
        type.setValue(j.type);
        type.getStyleClass().add("type-combo");
        type.setMaxWidth(Double.MAX_VALUE);
        type.setButtonCell(typeCell());
        type.setCellFactory(v -> typeCell());

        VBox power  = slider("Güç", "dBm", -45, 0, j.powerDbm, 0, v -> j.powerDbm = v);
        VBox bw     = slider("Bant Genişliği", "kHz", 50, 3000, j.bwKHz, 0, v -> j.bwKHz = v);
        VBox center = slider("Merkez Frekans", "MHz", engine.band.fMin(), engine.band.fMax(),
                             clampBand(j.centerFreqMHz), 2, v -> j.centerFreqMHz = v);
        VBox sweep  = slider("Tarama Hızı", "MHz/sn", 5, 200, j.sweepRateMHzPerS, 0, v -> j.sweepRateMHzPerS = v);
        VBox react  = slider("Tepki Süresi", "ms", 1, 60, j.reactionMs, 0, v -> j.reactionMs = v);

        Runnable refresh = () -> {
            Jammer.Type t = type.getValue();
            center.setDisable(!(t == Jammer.Type.SPOT || t == Jammer.Type.SWEEP));
            sweep.setDisable(t != Jammer.Type.SWEEP);
            react.setDisable(t != Jammer.Type.FOLLOWER);
            boolean off = t == Jammer.Type.OFF;
            power.setDisable(off);
            bw.setDisable(off);
        };
        type.valueProperty().addListener((o, was, now) -> { if (now != null) { j.type = now; refresh.run(); } });
        refresh.run();

        Label hint = new Label(typeHint());
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);

        box.getChildren().addAll(labelFor("Karıştırma Doktrini"), type, hint, power, bw, center, sweep, react);
        Tab tab = new Tab(name, box);
        tab.setClosable(false);
        return tab;
    }

    private static String typeHint() {
        return "Barrage: bant boyu güç · Spot: tek frekans · Sweep: tarama · Takip: dinle-ve-kilitlen (gecikmeli)";
    }

    // ---------- Ortam ----------
    private VBox environmentCard() {
        VBox card = card("ORTAM");
        VBox noise = slider("Gürültü Tabanı", "dBm", -130, -90, engine.noiseFloorDbm, 0, v -> engine.noiseFloorDbm = v);
        VBox thr   = slider("Link Eşiği (SJNR)", "dB", 3, 20, engine.linkThresholdDb, 0, v -> engine.linkThresholdDb = v);
        card.getChildren().addAll(noise, thr);
        return card;
    }

    // ---------- yardımcılar ----------
    private VBox card(String title) {
        VBox v = new VBox(9);
        v.getStyleClass().add("card");
        v.setPadding(new Insets(11, 12, 12, 12));
        v.getChildren().add(labelTitle(title));
        return v;
    }

    private VBox slider(String name, String unit, double min, double max, double val,
                        int dp, DoubleConsumer sink) {
        Label nameL = new Label(name);
        nameL.getStyleClass().add("slider-name");
        Label valL = new Label(fmtVal(val, dp, unit));
        valL.getStyleClass().add("slider-val");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(nameL, spacer, valL);
        head.setAlignment(Pos.CENTER_LEFT);

        Slider s = new Slider(min, max, clamp(val, min, max));
        s.getStyleClass().add("param-slider");
        s.valueProperty().addListener((o, a, b) -> {
            double vv = b.doubleValue();
            valL.setText(fmtVal(vv, dp, unit));
            sink.accept(vv);
        });

        VBox row = new VBox(3, head, s);
        row.getStyleClass().add("slider-row");
        return row;
    }

    private ToggleButton seg(String text, ToggleGroup g) {
        ToggleButton b = new ToggleButton(text);
        b.setToggleGroup(g);
        b.getStyleClass().add("seg-btn");
        b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }

    private ListCell<Jammer.Type> typeCell() {
        return new ListCell<>() {
            @Override protected void updateItem(Jammer.Type t, boolean empty) {
                super.updateItem(t, empty);
                setText(empty || t == null ? null : label(t));
            }
        };
    }

    static String label(Jammer.Type t) {
        return switch (t) {
            case OFF -> "Kapalı";
            case BARRAGE -> "Barrage (geniş bant)";
            case SPOT -> "Spot (nokta)";
            case SWEEP -> "Sweep (tarama)";
            case FOLLOWER -> "Takip (repeater)";
        };
    }

    private Label labelTitle(String s) { Label l = new Label(s); l.getStyleClass().add("panel-title"); return l; }
    private Label labelFor(String s)   { Label l = new Label(s); l.getStyleClass().add("field-label"); return l; }

    private double clampBand(double f) { return clamp(f, engine.band.fMin(), engine.band.fMax()); }
    private static double clamp(double v, double lo, double hi) { return v < lo ? lo : (v > hi ? hi : v); }

    private static String fmtVal(double v, int dp, String unit) {
        String num = String.format(Locale.US, "%." + dp + "f", v);
        return unit.isEmpty() ? num : num + " " + unit;
    }
}
