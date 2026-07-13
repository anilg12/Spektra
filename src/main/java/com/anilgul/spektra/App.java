/*
 * SPEKTRA — App.java
 * Uygulama gövdesi: menü + araç çubuğu, dört enstrüman (spektrum, şelale,
 * zaman çizelgesi, metrik + olay günlüğü) ve simülasyon döngüsü.
 * Fizik SimEngine'de; burası yalnızca yerleşim, girdi ve çizim zamanlaması.
 * Anıl Gül · 2026
 */
package com.anilgul.spektra;

import com.anilgul.spektra.sim.*;
import com.anilgul.spektra.ui.*;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.Locale;

public class App extends Application {

    private static final double[] SPEEDS = {0.25, 0.5, 1.0, 2.0, 4.0};
    private static final String[] SPEED_LABELS = {"0.25×", "0.5×", "1×", "2×", "4×"};

    private SimEngine engine;
    private SpectrumView spectrum;
    private WaterfallView waterfall;
    private TimelineView timeline;
    private MetricsPanel metrics;
    private EventLogView eventLog;

    private ScrollPane leftScroll;
    private ComboBox<String> bandCombo;
    private ComboBox<String> scenarioCombo;
    private ComboBox<String> speedCombo;
    private Button pauseBtn;
    private MenuItem pauseItem;
    private Label clockLabel;
    private Label fpsLabel;

    private boolean suppress = false;
    private long lastNs = 0, fpsClock = 0;
    private int frames = 0;

    @Override
    public void start(Stage stage) {
        engine = new SimEngine(new Band(30, 88, 600));
        Scenario.applyPreset(engine, 0);

        spectrum = new SpectrumView();
        waterfall = new WaterfallView(engine.band);
        timeline = new TimelineView();
        metrics = new MetricsPanel();
        eventLog = new EventLogView();

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");
        root.setTop(new VBox(buildMenuBar(stage), buildToolBar(stage)));
        root.setLeft(buildLeft());
        root.setCenter(buildCenter());
        root.setRight(buildRight());
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, 1320, 840);
        scene.getStylesheets().add(getClass().getResource("/com/anilgul/spektra/spektra.css").toExternalForm());

        stage.setTitle("SPEKTRA — Elektronik Harp Spektrum Simülatörü");
        stage.setScene(scene);
        stage.setMinWidth(1120);
        stage.setMinHeight(720);
        stage.show();

        startLoop();
    }

    // ---------------- Menü ----------------
    private MenuBar buildMenuBar(Stage stage) {
        Menu file = new Menu("Dosya");
        MenuItem save = item("Senaryo Kaydet…", "Shortcut+S", () -> saveScenario(stage));
        MenuItem load = item("Senaryo Yükle…", "Shortcut+O", () -> loadScenario(stage));
        MenuItem png  = item("PNG Dışa Aktar…", "Shortcut+E", () -> exportPng(stage));
        MenuItem quit = item("Çıkış", "Shortcut+Q", stage::close);
        file.getItems().addAll(save, load, new SeparatorMenuItem(), png, new SeparatorMenuItem(), quit);

        Menu view = new Menu("Görünüm");
        pauseItem = item("Duraklat", "Shortcut+P", this::togglePause);
        MenuItem step  = item("Bir Adım İlerle", "Shortcut+Period", this::stepOnce);
        MenuItem reset = item("Sıfırla", "Shortcut+R", this::resetAll);
        view.getItems().addAll(pauseItem, step, new SeparatorMenuItem(), reset);

        Menu help = new Menu("Yardım");
        MenuItem usage = item("Kullanım", "F1", () -> AboutDialog.openHelp(stage));
        MenuItem about = item("Hakkında", null, () -> AboutDialog.openAbout(stage));
        help.getItems().addAll(usage, about);

        MenuBar bar = new MenuBar(file, view, help);
        bar.getStyleClass().add("app-menu");
        return bar;
    }

    private MenuItem item(String text, String accel, Runnable action) {
        MenuItem mi = new MenuItem(text);
        if (accel != null) mi.setAccelerator(KeyCombination.keyCombination(accel));
        mi.setOnAction(e -> action.run());
        return mi;
    }

    // ---------------- Araç çubuğu ----------------
    private ToolBar buildToolBar(Stage stage) {
        HBox brand = brand();

        bandCombo = new ComboBox<>();
        bandCombo.getItems().addAll(Band.PRESET_NAMES);
        bandCombo.getSelectionModel().select(0);
        bandCombo.setPrefWidth(158);

        scenarioCombo = new ComboBox<>();
        scenarioCombo.getItems().addAll(Scenario.PRESET_LABELS);
        scenarioCombo.getSelectionModel().select(0);
        scenarioCombo.setPrefWidth(220);

        speedCombo = new ComboBox<>();
        speedCombo.getItems().addAll(SPEED_LABELS);
        speedCombo.getSelectionModel().select(2);
        speedCombo.setPrefWidth(78);

        // dinleyiciler (ilk seçimden sonra bağlanır)
        bandCombo.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> {
            if (suppress || b.intValue() < 0) return;
            int i = b.intValue();
            engine.applyBand(Band.PRESETS[i][0], Band.PRESETS[i][1], Band.PRESET_NAMES[i]);
            waterfall.clear();
            rebuildControls();
        });
        scenarioCombo.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> {
            if (suppress || b.intValue() < 0) return;
            Scenario.applyPreset(engine, b.intValue());
            waterfall.clear();
            rebuildControls();
            syncBandCombo();
        });
        speedCombo.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> {
            if (b.intValue() >= 0) engine.timeScale = SPEEDS[b.intValue()];
        });

        pauseBtn = new Button("⏸  Duraklat");
        pauseBtn.getStyleClass().add("tool-btn");
        pauseBtn.setOnAction(e -> togglePause());

        Button stepBtn = new Button("⏭  Adım");
        stepBtn.getStyleClass().add("tool-btn");
        stepBtn.setOnAction(e -> stepOnce());

        Button resetBtn = new Button("↺  Sıfırla");
        resetBtn.getStyleClass().add("tool-btn");
        resetBtn.setOnAction(e -> resetAll());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ToolBar bar = new ToolBar(
                brand, new Separator(),
                tag("BANT"), bandCombo,
                tag("SENARYO"), scenarioCombo,
                tag("HIZ"), speedCombo,
                spacer, pauseBtn, stepBtn, resetBtn);
        bar.getStyleClass().add("app-toolbar");
        return bar;
    }

    private HBox brand() {
        Canvas logo = new Canvas(26, 22);
        drawLogo(logo.getGraphicsContext2D());
        Label name = new Label("SPEKTRA");
        name.getStyleClass().add("brand-name");
        Label sub = new Label("EW SPECTRUM SUITE");
        sub.getStyleClass().add("brand-sub");
        VBox txt = new VBox(-2, name, sub);
        txt.setAlignment(Pos.CENTER_LEFT);
        HBox box = new HBox(8, logo, txt);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(0, 6, 0, 2));
        return box;
    }

    private void drawLogo(GraphicsContext g) {
        g.setStroke(Theme.AMBER);
        g.setLineWidth(1.6);
        double base = 18;
        double[] hs = {4, 11, 6, 16, 8, 13, 5};
        for (int i = 0; i < hs.length; i++) {
            double x = 2 + i * 3.4;
            g.strokeLine(x, base, x, base - hs[i]);
        }
        g.setStroke(Theme.OK);
        g.setLineWidth(1.0);
        g.strokeLine(2, base + 2, 26, base + 2);
    }

    private Label tag(String s) { Label l = new Label(s); l.getStyleClass().add("tool-tag"); return l; }

    // ---------------- Yerleşim ----------------
    private ScrollPane buildLeft() {
        leftScroll = new ScrollPane(new ControlPanel(engine));
        leftScroll.setFitToWidth(true);
        leftScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        leftScroll.getStyleClass().add("side-scroll");
        leftScroll.setMinWidth(330);
        leftScroll.setPrefWidth(346);
        return leftScroll;
    }

    private Region buildCenter() {
        VBox.setVgrow(spectrum, Priority.ALWAYS);
        waterfall.setPrefHeight(288);
        waterfall.setMinHeight(180);
        VBox center = new VBox(10, spectrum, waterfall);
        center.setPadding(new Insets(10));
        center.getStyleClass().add("center-stack");
        return center;
    }

    private ScrollPane buildRight() {
        metrics.setPadding(Insets.EMPTY);
        VBox col = new VBox(11, metrics, timeline, eventLog);
        col.setPadding(new Insets(12));
        col.getStyleClass().add("right-col");
        ScrollPane sp = new ScrollPane(col);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.getStyleClass().add("side-scroll");
        sp.setMinWidth(326);
        sp.setPrefWidth(340);
        return sp;
    }

    private HBox buildStatusBar() {
        Label credit = new Label("ANIL GÜL · SPEKTRA v2");
        credit.getStyleClass().add("credit");
        clockLabel = new Label("00:00");
        clockLabel.getStyleClass().add("status-metric");
        fpsLabel = new Label("FPS —");
        fpsLabel.getStyleClass().add("status-metric");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(12, credit, spacer, fpsLabel, sep(), clockLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(5, 12, 5, 12));
        bar.getStyleClass().add("status-bar");
        return bar;
    }

    private Label sep() { Label l = new Label("·"); l.getStyleClass().add("status-metric"); return l; }

    // ---------------- Eylemler ----------------
    private void togglePause() {
        engine.paused = !engine.paused;
        pauseBtn.setText(engine.paused ? "▶  Devam" : "⏸  Duraklat");
        pauseItem.setText(engine.paused ? "Devam Et" : "Duraklat");
    }

    private void stepOnce() {
        engine.step(1.0 / 60.0);
        waterfall.push(engine);
    }

    private void resetAll() {
        suppress = true;
        Scenario.applyPreset(engine, 0);
        bandCombo.getSelectionModel().select(0);
        scenarioCombo.getSelectionModel().select(0);
        speedCombo.getSelectionModel().select(2);
        engine.timeScale = 1.0;
        suppress = false;
        waterfall.clear();
        eventLog.clear();
        rebuildControls();
    }

    private void rebuildControls() {
        leftScroll.setContent(new ControlPanel(engine));
    }

    private void syncBandCombo() {
        double lo = engine.band.fMin();
        int match = 0;
        for (int i = 0; i < Band.PRESETS.length; i++)
            if (Math.abs(Band.PRESETS[i][0] - lo) < 0.5) { match = i; break; }
        suppress = true;
        bandCombo.getSelectionModel().select(match);
        suppress = false;
    }

    private void saveScenario(Stage stage) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Senaryo Kaydet");
        fc.setInitialFileName("senaryo.spx");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("SPEKTRA senaryo (*.spx)", "*.spx"));
        File f = fc.showSaveDialog(stage);
        if (f == null) return;
        try {
            Files.writeString(f.toPath(), Scenario.capture(engine));
            engine.log("Senaryo kaydedildi: " + f.getName());
        } catch (Exception ex) {
            error("Senaryo kaydedilemedi", ex.getMessage());
        }
    }

    private void loadScenario(Stage stage) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Senaryo Yükle");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("SPEKTRA senaryo (*.spx)", "*.spx"));
        File f = fc.showOpenDialog(stage);
        if (f == null) return;
        try {
            Scenario.apply(engine, Files.readString(f.toPath()));
            waterfall.clear();
            rebuildControls();
            syncBandCombo();
        } catch (Exception ex) {
            error("Senaryo yüklenemedi", ex.getMessage());
        }
    }

    private void exportPng(Stage stage) {
        FileChooser fc = new FileChooser();
        fc.setTitle("PNG Dışa Aktar");
        fc.setInitialFileName("spektra.png");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG görüntü (*.png)", "*.png"));
        File f = fc.showSaveDialog(stage);
        if (f == null) return;
        try {
            SnapshotParameters sp = new SnapshotParameters();
            sp.setFill(Theme.VOID);
            WritableImage img = spectrum.getParent().snapshot(sp, null);
            BufferedImage bi = javafx.embed.swing.SwingFXUtils.fromFXImage(img, null);
            ImageIO.write(bi, "png", f);
            engine.log("Görüntü kaydedildi: " + f.getName());
        } catch (Exception ex) {
            error("Dışa aktarma başarısız", ex.getMessage());
        }
    }

    private void error(String header, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Hata");
        a.setHeaderText(header);
        a.setContentText(msg == null ? "Bilinmeyen hata." : msg);
        a.showAndWait();
    }

    // ---------------- Döngü ----------------
    private void startLoop() {
        AnimationTimer timer = new AnimationTimer() {
            @Override public void handle(long now) {
                if (lastNs == 0) { lastNs = now; fpsClock = now; return; }
                double dt = (now - lastNs) / 1e9;
                lastNs = now;
                if (dt > 0.05) dt = 0.05;

                if (!engine.paused) {
                    engine.step(dt * engine.timeScale);
                    waterfall.push(engine);
                }

                spectrum.render(engine);
                waterfall.render(engine);
                timeline.render(engine);
                metrics.update(engine);
                eventLog.drain(engine);

                frames++;
                if (now - fpsClock >= 1_000_000_000L) {
                    fpsLabel.setText("FPS " + frames);
                    frames = 0;
                    fpsClock = now;
                }
                clockLabel.setText(String.format(Locale.US, "%02d:%02d",
                        (int) engine.time() / 60, (int) engine.time() % 60));
            }
        };
        timer.start();
    }

    public static void main(String[] args) { launch(args); }
}
