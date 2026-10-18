package io.github.g1ocr;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public final class BootstrapWindow extends Application {
    @Override public void start(Stage stage) {
        stage.setTitle("G1 OCR — 工程检查");
        stage.setScene(new Scene(new StackPane(new Label("工程已启动。采集、识别与翻译尚未启用。")), 560, 180));
        stage.show();
    }
}
