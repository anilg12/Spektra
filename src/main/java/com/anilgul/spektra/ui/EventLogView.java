/*
 * SPEKTRA — EventLogView.java
 * Olay günlüğü. Motorun ürettiği zaman damgalı olayları (link kaybı/geri
 * kazanımı, senaryo/bant değişimi) her karede boşaltıp listeye ekler.
 * Anıl Gül · 2026
 */
package com.anilgul.spektra.ui;

import com.anilgul.spektra.sim.SimEngine;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class EventLogView extends VBox {

    private static final int CAP = 200;

    private final ObservableList<String> items = FXCollections.observableArrayList();
    private final ListView<String> list = new ListView<>(items);

    public EventLogView() {
        setSpacing(4);
        setPadding(new Insets(2, 0, 0, 0));

        Label title = new Label("OLAY GÜNLÜĞÜ");
        title.getStyleClass().add("panel-title");

        list.getStyleClass().add("event-log");
        list.setPrefHeight(150);
        list.setFocusTraversable(false);
        list.setPlaceholder(muted("— olay bekleniyor —"));
        VBox.setVgrow(list, Priority.NEVER);

        getChildren().addAll(title, list);
    }

    /** Motorda biriken olayları listeye aktar ve en yeniye kaydır. */
    public void drain(SimEngine engine) {
        boolean added = false;
        while (engine.hasEvents()) {
            items.add(engine.pollEvent());
            added = true;
        }
        while (items.size() > CAP) items.remove(0);
        if (added && !items.isEmpty()) list.scrollTo(items.size() - 1);
    }

    public void clear() { items.clear(); }

    private static Label muted(String s) {
        Label l = new Label(s);
        l.getStyleClass().add("muted");
        return l;
    }
}
