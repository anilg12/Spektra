/*
 * SPEKTRA — WaterfallView.java
 * Zaman/frekans şelalesi (inferno renk haritası). En yeni spektrum üste yazılır,
 * eskiler aşağı akar; sinyal (amber) ve tehdit izleri (A kırmızı, B menekşe) iz
 * bırakır — takip eden tehdit, sinyali bir adım geriden kovalarken görünür.
 * Anıl Gül · 2026
 */
package com.anilgul.spektra.ui;

import com.anilgul.spektra.sim.Band;
import com.anilgul.spektra.sim.Jammer;
import com.anilgul.spektra.sim.SimEngine;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

public final class WaterfallView extends Pane {

    private static final double DBM_LO = -120, DBM_HI = -42;

    private final int bufW, bufH;
    private final int[] buf;
    private final WritableImage img;
    private final Canvas canvas = new Canvas();

    public WaterfallView(Band band) {
        this.bufW = band.bins;
        this.bufH = 260;
        this.buf = new int[bufW * bufH];
        this.img = new WritableImage(bufW, bufH);
        java.util.Arrays.fill(buf, 0xFF000004);
        flush();

        setMinSize(200, 150);
        setPrefSize(900, 280);
        getStyleClass().add("instrument");
        canvas.setManaged(false);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        getChildren().add(canvas);
    }

    /** Bir spektrum satırı bas ve sinyal/tehdit işaretlerini üst satıra işle. */
    public void push(SimEngine engine) {
        double[] psd = engine.psd();
        System.arraycopy(buf, 0, buf, bufW, bufW * (bufH - 1));
        for (int i = 0; i < bufW; i++) {
            double t = (psd[i] - DBM_LO) / (DBM_HI - DBM_LO);
            buf[i] = inferno(t);
        }
        Band band = engine.band;
        int xs = (int) (band.freqToFraction(engine.emitter.currentFreqMHz()) * (bufW - 1));
        paintMark(xs, Theme.ARGB_AMBER);
        for (int k = 0; k < engine.jammers.length; k++) {
            Jammer.Type t = engine.jammers[k].type;
            if (t == Jammer.Type.SPOT || t == Jammer.Type.SWEEP || t == Jammer.Type.FOLLOWER) {
                int xj = (int) (band.freqToFraction(engine.jammers[k].currentCenterMHz()) * (bufW - 1));
                paintMark(xj, Theme.threatArgb(k));
            }
        }
        flush();
    }

    /** Şelaleyi temizle (bant/senaryo değişiminde eski izler kalmasın). */
    public void clear() {
        java.util.Arrays.fill(buf, 0xFF000004);
        flush();
    }

    private void paintMark(int x, int argb) {
        if (x < 0) x = 0;
        if (x >= bufW) x = bufW - 1;
        buf[x] = argb;
        if (x > 0) buf[x - 1] = argb;
    }

    private void flush() {
        img.getPixelWriter().setPixels(0, 0, bufW, bufH, PixelFormat.getIntArgbInstance(), buf, 0, bufW);
    }

    public void render(SimEngine engine) {
        double w = getWidth(), h = getHeight();
        if (w < 40 || h < 40) return;
        GraphicsContext g = canvas.getGraphicsContext2D();

        double barW = 14;
        double plotW = w - barW - 8;
        if (plotW < 10) return;

        g.setImageSmoothing(false);
        g.drawImage(img, 0, 0, plotW, h);

        Band band = engine.band;
        double step = band.widthMHz() > 120 ? (band.widthMHz() > 600 ? 200 : 50) : 10;
        double f0 = Math.ceil(band.fMin() / step) * step;
        for (double f = f0; f <= band.fMax(); f += step) {
            double x = band.freqToFraction(f) * plotW;
            g.setStroke(Color.rgb(0, 0, 0, 0.18));
            g.setLineWidth(1);
            g.strokeLine(x, 0, x, h);
        }

        for (int yy = 0; yy < (int) h; yy++) {
            double t = 1.0 - yy / h;
            g.setFill(colorOf(inferno(t)));
            g.fillRect(plotW + 8, yy, barW - 2, 1);
        }
        g.setFont(Theme.mono(9));
        g.setFill(Theme.MUTED);
        g.setTextAlign(TextAlignment.RIGHT);
        g.fillText((int) DBM_HI + "", w - 1, 9);
        g.fillText((int) DBM_LO + "", w - 1, h - 2);

        g.setTextAlign(TextAlignment.LEFT);
        g.setFont(Theme.display(11));
        g.setFill(Theme.MUTED);
        g.fillText("ŞELALE — ZAMAN / FREKANS", 4, 14);
        g.setFont(Theme.mono(9));
        g.fillText("↓ geçmiş", 4, h - 5);
    }

    // inferno renk haritası: t∈[0,1] -> paketli ARGB
    private static final double[][] STOPS = {
            {0.00,   0,   0,   4}, {0.15,  40,  11,  84}, {0.35, 101,  21, 110},
            {0.55, 159,  42,  99}, {0.75, 212,  72,  66}, {0.90, 245, 125,  21},
            {1.00, 252, 255, 164}
    };

    static int inferno(double t) {
        if (t <= 0) t = 0;
        if (t >= 1) t = 1;
        for (int i = 1; i < STOPS.length; i++) {
            if (t <= STOPS[i][0]) {
                double[] a = STOPS[i - 1], b = STOPS[i];
                double f = (t - a[0]) / (b[0] - a[0]);
                int r = (int) (a[1] + f * (b[1] - a[1]));
                int gg = (int) (a[2] + f * (b[2] - a[2]));
                int bl = (int) (a[3] + f * (b[3] - a[3]));
                return 0xFF000000 | (r << 16) | (gg << 8) | bl;
            }
        }
        return 0xFFFCFFA4;
    }

    private static Color colorOf(int argb) {
        return Color.rgb((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF);
    }
}
