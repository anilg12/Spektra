/*
 * SPEKTRA — Theme.java
 * Çizim için ortak renk ve yazı tipleri. RF enstrümantasyonuna oturtulmuş
 * koyu arduvaz zemin, amber fosfor izi (imza), yalnızca anlamsal yeşil/kırmızı.
 * İkinci tehdit menekşe tonuyla ayrışır.
 * Anıl Gül · 2026
 */
package com.anilgul.spektra.ui;

import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public final class Theme {

    private Theme() {}

    public static final Color VOID     = Color.web("#070B12");
    public static final Color SURFACE  = Color.web("#0E1520");
    public static final Color SURFACE2 = Color.web("#131C29");
    public static final Color LINE     = Color.web("#223044");
    public static final Color GRID     = Color.rgb(120, 140, 170, 0.10);
    public static final Color GRID_STRONG = Color.rgb(120, 140, 170, 0.18);
    public static final Color TEXT     = Color.web("#D9E2EC");
    public static final Color MUTED    = Color.web("#6B7A8F");

    public static final Color AMBER      = Color.web("#F2B24B");
    public static final Color AMBER_FILL = Color.rgb(242, 178, 75, 0.22);
    public static final Color COOL       = Color.web("#4FA8FF");
    public static final Color OK         = Color.web("#35D6A4");
    public static final Color ALERT      = Color.web("#FF5468");   // Tehdit A
    public static final Color VIOLET     = Color.web("#C77DFF");   // Tehdit B

    public static final int ARGB_AMBER  = 0xFFF2B24B;
    public static final int ARGB_ALERT  = 0xFFFF5468;
    public static final int ARGB_VIOLET = 0xFFC77DFF;

    /** Tehdit indisine göre işaret rengi (0 = A, 1 = B). */
    public static Color threat(int i)     { return i == 0 ? ALERT : VIOLET; }
    public static int   threatArgb(int i) { return i == 0 ? ARGB_ALERT : ARGB_VIOLET; }

    public static Font display(double size) { return Font.font("Bahnschrift", FontWeight.SEMI_BOLD, size); }
    public static Font mono(double size)    { return Font.font("Consolas", size); }
}
