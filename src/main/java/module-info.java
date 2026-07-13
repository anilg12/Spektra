/*
 * SPEKTRA — modül tanımı
 * JavaFX kontrol/grafik katmanı + PNG dışa aktarma için swing köprüsü ve java.desktop.
 * Anıl Gül · 2026
 */
module com.anilgul.spektra {
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.swing;   // SwingFXUtils — anlık görüntüyü PNG'ye çevirmek için
    requires java.desktop;   // ImageIO + BufferedImage — dosyaya yazma

    exports com.anilgul.spektra;
    exports com.anilgul.spektra.ui;
    exports com.anilgul.spektra.sim;

    // Gömülü stil dosyası (spektra.css) çalışma anında bu paketten okunuyor.
    opens com.anilgul.spektra;
}
