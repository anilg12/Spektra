/*
 * SPEKTRA — TimelineView.java
 * Görev zaman çizelgesi: son ~20 sn boyunca PDR (%) ve SJNR (dB) kayan
 * çizgiler halinde. Eşik çizgisi linkin kırıldığı SJNR seviyesini gösterir.
 * Anıl Gül · 2026
 */
package com.anilgul.spektra.ui;

import com.anilgul.spektra.sim.SimEngine;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.text.TextAlignment;

public final class TimelineView extends Pane {

    private static final double PAD_L = 30, PAD_R = 34, PAD_T = 18, PAD_B = 14;
    private static final double SJNR_LO = -20, SJNR_HI = 45;

    private final Canvas canvas = new Canvas();

    public TimelineView() {
        setMinSize(180, 120);
        setPrefHeight(150);
        getStyleClass().add("instrument");
        canvas.setManaged(false);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        getChildren().add(canvas);
    }

    public void render(SimEngine engine) {
        double w = getWidth(), h = getHeight();
        if (w < 40 || h < 40) return;
        GraphicsContext g = canvas.getGraphicsContext2D();

        g.setFill(Theme.SURFACE);
        g.fillRect(0, 0, w, h);

        double L = PAD_L, T = PAD_T, R = w - PAD_R, B = h - PAD_B;
        double pw = R - L, ph = B - T;

        // Izgara: PDR %0/50/100 (sol)
        g.setFont(Theme.mono(9));
        g.setTextAlign(TextAlignment.RIGHT);
        for (int p = 0; p <= 100; p += 50) {
            double y = B - (p / 100.0) * ph;
            g.setStroke(Theme.GRID);
            g.strokeLine(L, y, R, y);
            g.setFill(Theme.AMBER.deriveColor(0, 1, 1, 0.8));
            g.fillText(p + "", L - 4, y + 3);
        }
        // SJNR eşiği (sağ eksende)
        double yThr = B - clamp01((engine.linkThresholdDb - SJNR_LO) / (SJNR_HI - SJNR_LO)) * ph;
        g.setStroke(Theme.MUTED.deriveColor(0, 1, 1, 0.5));
        g.setLineDashes(4, 4);
        g.strokeLine(L, yThr, R, yThr);
        g.setLineDashes();
        g.setFill(Theme.MUTED);
        g.setTextAlign(TextAlignment.LEFT);
        g.fillText("eşik", L + 2, yThr - 2);

        int n = engine.histCount();
        if (n >= 2) {
            // PDR çizgisi (amber)
            g.beginPath();
            for (int i = 0; i < n; i++) {
                double x = L + (i / (double) (n - 1)) * pw;
                double y = B - clamp01(engine.pdrAt(i)) * ph;
                if (i == 0) g.moveTo(x, y); else g.lineTo(x, y);
            }
            g.setStroke(Theme.AMBER);
            g.setLineWidth(1.6);
            g.stroke();

            // SJNR çizgisi (mavi)
            g.beginPath();
            for (int i = 0; i < n; i++) {
                double x = L + (i / (double) (n - 1)) * pw;
                double s = clamp01((engine.sjnrAt(i) - SJNR_LO) / (SJNR_HI - SJNR_LO));
                double y = B - s * ph;
                if (i == 0) g.moveTo(x, y); else g.lineTo(x, y);
            }
            g.setStroke(Theme.COOL);
            g.setLineWidth(1.2);
            g.stroke();
        }

        // Sağ eksen etiketleri (SJNR)
        g.setTextAlign(TextAlignment.LEFT);
        g.setFill(Theme.COOL.deriveColor(0, 1, 1, 0.85));
        g.fillText((int) SJNR_HI + "", R + 4, T + 8);
        g.fillText((int) SJNR_LO + "", R + 4, B);

        // Başlık + lejant
        g.setTextAlign(TextAlignment.LEFT);
        g.setFont(Theme.display(11));
        g.setFill(Theme.MUTED);
        g.fillText("GÖREV ZAMAN ÇİZELGESİ", L, 12);
        g.setFont(Theme.mono(9));
        g.setFill(Theme.AMBER);
        g.fillText("■ PDR", R - 78, 12);
        g.setFill(Theme.COOL);
        g.fillText("■ SJNR", R - 40, 12);
    }

    private static double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
}
