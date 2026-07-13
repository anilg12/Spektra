/*
 * SPEKTRA — AboutDialog.java
 * "Hakkında" ve "Kullanım" için sade modal pencereler.
 * Anıl Gül · 2026
 */
package com.anilgul.spektra.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class AboutDialog {

    private AboutDialog() {}

    public static void openAbout(Window owner) {
        VBox body = new VBox(6);
        body.getChildren().addAll(
                h1("SPEKTRA"),
                sub("Elektronik Harp · Spektrum Simülasyon & Karıştırma Analiz Platformu"),
                gap(6),
                p("Taktik VHF/UHF bandında dostu bir haberleşme telsizi ile onu "
                  + "karıştırmaya çalışan tehditler arasındaki mücadeleyi gerçek zamanlı "
                  + "olarak RF düzeyinde modelleyip görselleştirir."),
                gap(4),
                kv("Doktrinler", "Barrage · Spot · Sweep · Takip (repeater)"),
                kv("Savunma", "FHSS frekans atlama, TRANSEC anahtarı, kanal çeşitliliği"),
                kv("Fizik", "SJNR, PDR, takip gecikmesi — 1 ms alt-adımlı çözüm"),
                gap(8),
                credit());
        show(owner, body, "Hakkında");
    }

    public static void openHelp(Window owner) {
        VBox body = new VBox(6);
        body.getChildren().addAll(
                h1("Kullanım"),
                sub("Hızlı gösteri adımları"),
                gap(6),
                step("1", "Araç çubuğundan bir hazır senaryo seçerek başla."),
                step("2", "Emitörü FHSS yap, Tehdit A'yı ‘Takip’ olarak ayarla."),
                step("3", "Atlama Hızını düşür → link engellenir (tehdit yetişti)."),
                step("4", "Atlama Hızını yükselt → link geri gelir (tehdidi geçtin)."),
                step("5", "Tehdit B'yi de açıp katmanlı baskıyı dene."),
                gap(6),
                p("İz renkleri — amber: sinyal · yeşil/kırmızı: link · kırmızı iz: Tehdit A · "
                  + "menekşe iz: Tehdit B. Şelalede takip eden tehdit sinyali bir adım geriden kovalar."),
                gap(8),
                credit());
        show(owner, body, "Kullanım");
    }

    private static void show(Window owner, VBox body, String title) {
        body.setPadding(new Insets(20, 22, 18, 22));
        body.getStyleClass().add("dialog-body");
        body.setPrefWidth(440);

        Button close = new Button("Kapat");
        close.getStyleClass().add("dialog-close");
        VBox root = new VBox(body, close);
        VBox.setMargin(close, new Insets(0, 22, 18, 22));
        root.setAlignment(Pos.CENTER_RIGHT);
        root.getStyleClass().add("dialog-root");

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(title);
        Scene scene = new Scene(root);
        scene.getStylesheets().add(AboutDialog.class.getResource("/com/anilgul/spektra/spektra.css").toExternalForm());
        stage.setScene(scene);
        stage.setResizable(false);
        close.setOnAction(e -> stage.close());
        stage.showAndWait();
    }

    private static Label h1(String s)  { Label l = new Label(s); l.getStyleClass().add("dialog-h1"); return l; }
    private static Label sub(String s) { Label l = new Label(s); l.getStyleClass().add("dialog-sub"); return l; }
    private static Label p(String s)   { Label l = new Label(s); l.getStyleClass().add("dialog-p"); l.setWrapText(true); return l; }

    private static Region gap(double h) { Region r = new Region(); r.setMinHeight(h); return r; }

    private static VBox kv(String k, String v) {
        Label kl = new Label(k); kl.getStyleClass().add("dialog-k");
        Label vl = new Label(v); vl.getStyleClass().add("dialog-v"); vl.setWrapText(true);
        return new VBox(1, kl, vl);
    }

    private static Label step(String n, String s) {
        Label l = new Label(n + ".  " + s);
        l.getStyleClass().add("dialog-p");
        l.setWrapText(true);
        return l;
    }

    private static Label credit() {
        Label l = new Label("Anıl Gül · 2026   —   Java 21 · JavaFX 21");
        l.getStyleClass().add("dialog-credit");
        return l;
    }
}
