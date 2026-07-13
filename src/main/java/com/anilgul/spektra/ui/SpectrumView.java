/*
 * SPEKTRA — SpectrumView.java
 * Genlik/frekans spektrum analizörü. Amber iz (imza), yeşil/kırmızı link
 * işareti, tehdit başına renkli işaretler (A kırmızı, B menekşe) ve imleç
 * altında canlı frekans/dBm okuması.
 * Anıl Gül · 2026
 */
package com.anilgul.spektra.ui;

import com.anilgul.spektra.sim.Band;
import com.anilgul.spektra.sim.Jammer;
import com.anilgul.spektra.sim.SimEngine;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.Glow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.TextAlignment;

import java.util.Locale;

public final class SpectrumView extends Pane {

    private static final double PAD_L = 10, PAD_R = 46, PAD_T = 26, PAD_B = 24;
    private static final double DBM_TOP = -30, DBM_BOT = -122;

    private final Canvas canvas = new Canvas();
    private double mouseX = Double.NaN;

    public SpectrumView() {
        setMinSize(200, 160);
        setPrefSize(900, 420);
        getStyleClass().add("instrument");
        canvas.setManaged(false);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        getChildren().add(canvas);

        setOnMouseMoved(e -> mouseX = e.getX());
        setOnMouseExited(e -> mouseX = Double.NaN);
    }

    private double mapDbm(double dbm, double top, double h) {
        double d = Math.max(DBM_BOT, Math.min(DBM_TOP, dbm));
        double frac = (d - DBM_BOT) / (DBM_TOP - DBM_BOT);
        return top + h - frac * h;
    }

    public void render(SimEngine engine) {
        double w = getWidth(), h = getHeight();
        if (w < 40 || h < 40) return;

        GraphicsContext g = canvas.getGraphicsContext2D();
        Band band = engine.band;
        double[] psd = engine.psd();

        double plotL = PAD_L, plotT = PAD_T;
        double plotW = w - PAD_L - PAD_R;
        double plotH = h - PAD_T - PAD_B;
        double plotB = plotT + plotH;

        g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Theme.SURFACE), new Stop(1, Theme.VOID)));
        g.fillRect(0, 0, w, h);

        // dB ızgara + sağ ölçek
        g.setFont(Theme.mono(10));
        g.setTextAlign(TextAlignment.LEFT);
        for (int dbm = -30; dbm >= -120; dbm -= 15) {
            double y = mapDbm(dbm, plotT, plotH);
            g.setStroke(dbm == -30 ? Theme.GRID_STRONG : Theme.GRID);
            g.setLineWidth(1);
            g.strokeLine(plotL, y, plotL + plotW, y);
            g.setFill(Theme.MUTED);
            g.fillText(dbm + "", plotL + plotW + 6, y + 3);
        }

        // Frekans ızgara + alt etiketler
        g.setTextAlign(TextAlignment.CENTER);
        double step = niceFreqStep(band.widthMHz());
        double f0 = Math.ceil(band.fMin() / step) * step;
        for (double f = f0; f <= band.fMax() + 1e-6; f += step) {
            double x = plotL + band.freqToFraction(f) * plotW;
            g.setStroke(Theme.GRID);
            g.setLineWidth(1);
            g.strokeLine(x, plotT, x, plotB);
            g.setFill(Theme.MUTED);
            g.fillText(fmt(f, f >= 100 ? 0 : 1), x, plotB + 15);
        }

        // Barrage tehditleri: bant boyu hafif renk yıkaması
        for (int k = 0; k < engine.jammers.length; k++) {
            if (engine.jammers[k].type == Jammer.Type.BARRAGE) {
                Color c = Theme.threat(k);
                g.setFill(Color.color(c.getRed(), c.getGreen(), c.getBlue(), 0.06));
                g.fillRect(plotL, plotT, plotW, plotH);
            }
        }

        // İz: dolgu + parıltılı çizgi
        int n = band.bins;
        g.beginPath();
        g.moveTo(plotL, plotB);
        for (int i = 0; i < n; i++) {
            double x = plotL + (i / (double) (n - 1)) * plotW;
            g.lineTo(x, mapDbm(psd[i], plotT, plotH));
        }
        g.lineTo(plotL + plotW, plotB);
        g.closePath();
        g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Theme.AMBER_FILL), new Stop(1, Color.rgb(242, 178, 75, 0.0))));
        g.fill();

        g.save();
        g.setEffect(new Glow(0.55));
        g.beginPath();
        for (int i = 0; i < n; i++) {
            double x = plotL + (i / (double) (n - 1)) * plotW;
            double y = mapDbm(psd[i], plotT, plotH);
            if (i == 0) g.moveTo(x, y); else g.lineTo(x, y);
        }
        g.setStroke(Theme.AMBER);
        g.setLineWidth(1.4);
        g.stroke();
        g.restore();

        // Tehdit işaretleri (nokta/tarama/takip)
        for (int k = 0; k < engine.jammers.length; k++) {
            Jammer.Type t = engine.jammers[k].type;
            if (t == Jammer.Type.SPOT || t == Jammer.Type.SWEEP || t == Jammer.Type.FOLLOWER) {
                double xj = plotL + band.freqToFraction(engine.jammers[k].currentCenterMHz()) * plotW;
                g.save();
                g.setLineDashes(5, 5);
                g.setStroke(Theme.threat(k).deriveColor(0, 1, 1, 0.85));
                g.setLineWidth(1.3);
                g.strokeLine(xj, plotT, xj, plotB);
                g.restore();
                tag(g, xj, plotT + 2, k == 0 ? "TEHDİT A" : "TEHDİT B", Theme.threat(k), false);
            }
        }

        // Link işareti (yeşil/kırmızı)
        double xs = plotL + band.freqToFraction(engine.emitter.currentFreqMHz()) * plotW;
        Color sc = engine.linkUp() ? Theme.OK : Theme.ALERT;
        g.setStroke(sc);
        g.setLineWidth(1.6);
        g.strokeLine(xs, plotT, xs, plotB);
        g.setFill(sc);
        g.fillPolygon(new double[]{xs, xs + 5, xs, xs - 5},
                      new double[]{plotT - 6, plotT, plotT + 6, plotT}, 4);
        tag(g, xs, plotB - 15, "LINK " + fmt(engine.emitter.currentFreqMHz(), 2) + " MHz", sc, true);

        // İmleç okuması
        if (!Double.isNaN(mouseX) && mouseX > plotL && mouseX < plotL + plotW) {
            double frac = (mouseX - plotL) / plotW;
            double f = band.fMin() + frac * band.widthMHz();
            double dbm = psd[band.freqToBin(f)];
            g.setStroke(Theme.COOL.deriveColor(0, 1, 1, 0.6));
            g.setLineWidth(1);
            g.strokeLine(mouseX, plotT, mouseX, plotB);
            String s = fmt(f, 2) + " MHz   " + fmt(dbm, 1) + " dBm";
            g.setFont(Theme.mono(11));
            double tw = s.length() * 6.6 + 12;
            double bx = Math.min(mouseX + 8, plotL + plotW - tw);
            g.setFill(Color.rgb(7, 11, 18, 0.85));
            g.fillRect(bx, plotT + 4, tw, 18);
            g.setStroke(Theme.LINE);
            g.strokeRect(bx, plotT + 4, tw, 18);
            g.setFill(Theme.COOL);
            g.setTextAlign(TextAlignment.LEFT);
            g.fillText(s, bx + 6, plotT + 16);
        }

        // Köşe yazıları
        g.setTextAlign(TextAlignment.LEFT);
        g.setFont(Theme.display(11));
        g.setFill(Theme.MUTED);
        g.fillText("SPEKTRUM ANALİZÖRÜ", plotL + 2, 15);
        g.setTextAlign(TextAlignment.RIGHT);
        g.fillText("dBm", w - 6, 15);
    }

    private void tag(GraphicsContext g, double x, double y, String s, Color c, boolean below) {
        g.setFont(Theme.mono(10));
        g.setTextAlign(TextAlignment.CENTER);
        double tw = s.length() * 6.0 + 8;
        double ty = below ? y : y + 10;
        g.setFill(Color.rgb(7, 11, 18, 0.72));
        g.fillRect(x - tw / 2, ty - 9, tw, 13);
        g.setFill(c);
        g.fillText(s, x, ty + 1);
    }

    private static double niceFreqStep(double span) {
        double raw = span / 8.0;
        double[] steps = {1, 2, 2.5, 5, 10, 20, 25, 50, 100, 200, 250, 500};
        for (double s : steps) if (s >= raw) return s;
        return 500;
    }

    private static String fmt(double v, int dp) { return String.format(Locale.US, "%." + dp + "f", v); }
}
