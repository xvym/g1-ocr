package io.github.g1ocr.ui;

import io.github.g1ocr.capture.LiveSession;
import io.github.g1ocr.capture.WindowCapture;
import io.github.g1ocr.ocr.PixelOcr;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/** Japanese-only development UI; no translation or arbitrary crop-selection controls. */
public final class LiveApplication extends Application {
    private final AtomicReference<LiveSession.View> latest = new AtomicReference<>();
    private final ComboBox<WindowCapture.Window> windows = new ComboBox<>();
    private final Label status = new Label("请选择 new3ds_view 窗口");
    private final TextArea text = new TextArea();
    private final TextArea diagnostics = new TextArea();
    private final ImageView preview = new ImageView();
    private LiveSession session;
    private AnimationTimer refresh;

    public static void launchApp() { Application.launch(LiveApplication.class); }

    @Override public void start(Stage stage) throws IOException {
        windows.setPromptText("选择投屏窗口");
        windows.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(windows, Priority.ALWAYS);
        Button reload = new Button("刷新窗口");
        Button start = new Button("开始");
        Button stop = new Button("停止");
        Button calibrate = new Button("重新校准");
        start.disableProperty().bind(windows.valueProperty().isNull());
        reload.setOnAction(event -> {
            reload.setDisable(true);
            var selected = windows.getValue();
            session.refreshWindows(values -> Platform.runLater(() -> {
                windows.getItems().setAll(values);
                if (selected != null) windows.setValue(values.stream()
                        .filter(w -> w.hwnd() == selected.hwnd() && w.pid() == selected.pid()).findFirst().orElse(null));
                reload.setDisable(false);
                if (values.isEmpty()) status.setText("未找到可选窗口，请先启动投屏软件");
            }), error -> Platform.runLater(() -> {
                reload.setDisable(false);
                status.setText("无法列出窗口：请检查捕获 DLL 与 Windows 环境；修复后重启应用");
                diagnostics.setText(error);
            }));
        });
        start.setOnAction(event -> { clear("等待捕获与校准"); session.start(windows.getValue()); });
        stop.setOnAction(event -> { clear("已停止"); session.stop(); });
        calibrate.setOnAction(event -> { clear("等待重新校准"); session.recalibrate(); });

        VBox toolbar = new VBox(10, new HBox(8, windows, reload, start, stop, calibrate),
                new Label("采集配置：new3ds_view · 仅上屏 · VC 原生 1:1 · 投屏点对点整数倍率"), status);
        toolbar.setPadding(new Insets(16));
        status.setWrapText(true);
        text.setEditable(false);
        text.setWrapText(true);
        text.setFont(Font.font("Meiryo", 20));
        text.setPromptText("稳定识别后的日文会显示在这里");
        preview.setFitWidth(320);
        preview.setFitHeight(288);
        preview.setPreserveRatio(true);
        preview.setSmooth(false);
        VBox imagePanel = new VBox(8, new Label("GB 画面"), preview);
        imagePanel.setPadding(new Insets(0, 16, 0, 16));
        ScrollPane imageScroll = new ScrollPane(imagePanel);
        imageScroll.setFitToWidth(true);
        imageScroll.setPrefWidth(355);
        BorderPane center = new BorderPane(text);
        center.setLeft(imageScroll);
        BorderPane.setMargin(text, new Insets(0, 16, 0, 0));
        diagnostics.setEditable(false);
        diagnostics.setPrefRowCount(8);
        diagnostics.setFont(Font.font("Consolas", 12));
        TitledPane details = new TitledPane("采集与校准详情", diagnostics);
        details.setExpanded(false);
        VBox bottom = new VBox(8, new Label("同形假名以〔候选/候选〕显示；� 表示未知字符。当前版本尚待人工验收。"), details);
        bottom.setPadding(new Insets(12, 16, 16, 16));
        BorderPane root = new BorderPane(center, toolbar, null, bottom, null);
        stage.setTitle("G1 日文 OCR — 开发版");
        stage.setScene(new Scene(root, 1060, 680));
        stage.setMinWidth(860);
        stage.setMinHeight(560);
        stage.show();
        session = new LiveSession(latest::set);
        // One atomic slot: a stalled FX thread never queues a backlog of old OCR views.
        refresh = new AnimationTimer() {
            @Override public void handle(long now) {
                LiveSession.View view = latest.getAndSet(null);
                if (view != null && view.selectionId() == session.selectionId()) render(view);
            }
        };
        refresh.start();
        reload.fire();
    }

    private void clear(String message) { status.setText(message); text.clear(); diagnostics.clear(); preview.setImage(null); }

    private String line(PixelOcr.Line line) {
        StringBuilder display = new StringBuilder();
        Integer previousX = null;
        for (var character : line.characters()) {
            if (previousX != null && character.x() > previousX + 8) display.append(' ');
            display.append(!character.unknown() && character.candidates().size() > 1
                    ? "〔" + String.join("/", character.candidates()) + "〕" : character.raw());
            previousX = character.x();
        }
        return display.toString().strip().replaceAll(" +", " ");
    }

    private void render(LiveSession.View view) {
        status.setText(view.status());
        diagnostics.setText(view.diagnostics());
        StringBuilder body = new StringBuilder();
        for (var region : view.regions()) {
            var result = region.text();
            String label = switch (result.layout()) {
                case "dialogue" -> "主对话";
                case "battle_action" -> "战斗操作";
                case "battle_moves" -> "战斗技能";
                default -> result.semanticType().equals("pc_pokemon") ? "PC 宝可梦" : "道具列表（用途未确定）";
            };
            body.append(label);
            if (region.selectedItem() != null) body.append(" · 选中第 ").append(region.selectedItem() + 1).append(" 项");
            body.append('\n');
            for (var row : result.diagnostics()) {
                if (!row.rawOcr().isEmpty()) body.append(line(row)).append('\n');
            }
            body.append('\n');
        }
        if (!text.getText().equals(body.toString())) text.setText(body.toString());
        if (view.gb() == null) preview.setImage(null);
        else {
            WritableImage image = new WritableImage(160, 144);
            int[] argb = view.gb().getRGB(0, 0, 160, 144, null, 0, 160);
            image.getPixelWriter().setPixels(0, 0, 160, 144, PixelFormat.getIntArgbInstance(), argb, 0, 160);
            preview.setImage(image);
        }
    }

    @Override public void stop() {
        if (refresh != null) refresh.stop();
        if (session != null) session.close();
    }
}
