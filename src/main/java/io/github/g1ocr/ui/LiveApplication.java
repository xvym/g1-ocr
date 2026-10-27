package io.github.g1ocr.ui;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.g1ocr.capture.LiveSession;
import io.github.g1ocr.capture.WindowCapture;
import io.github.g1ocr.ocr.PixelOcr;
import io.github.g1ocr.text.TextCorpus;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Local translation with the original visual OCR available in a separate tab. */
public final class LiveApplication extends Application {
    private static final String AUTOMATIC_PLAYER_NAME = "自动识别（未指定）";
    private static final DateTimeFormatter HISTORY_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");
    private final AtomicReference<LiveSession.View> latest = new AtomicReference<>();
    private final ComboBox<WindowCapture.Window> windows = new ComboBox<>();
    private final ComboBox<String> playerNames = new ComboBox<>();
    private final Path playerNamesFile = Path.of("config", "player-names.json");
    private final JsonMapper settingsJson = JsonMapper.builder().build();
    private boolean updatingPlayerNames;
    private final Label status = new Label("请选择 new3ds_view 窗口");
    private final TextArea text = new TextArea();
    private final TextArea chinese = new TextArea();
    private final TextArea dialogueText = new TextArea();
    private final TextArea diagnostics = new TextArea();
    private final ListView<LiveSession.HistoryEntry> historyList = new ListView<>();
    private final TextArea historyJapanese = new TextArea();
    private final TextArea historyChinese = new TextArea();
    private final TextArea historyStatus = new TextArea();
    private boolean updatingHistory;
    private final ImageView preview = new ImageView();
    private final Spinner<Integer> fontSize = new Spinner<>(12, 36, 20);
    private final ToggleButton compactMode = new ToggleButton("简洁模式");
    private final Label dialogueHeading = new Label("对话");
    private final Label listHeading = new Label("列表与操作");
    private final VBox dialoguePanel = new VBox(6, dialogueHeading, dialogueText);
    private final VBox listPanel = new VBox(6, listHeading, chinese);
    private final SplitPane translation = new SplitPane();
    private final Tab translationTab = new Tab("中文 / 状态");
    private final BorderPane compactRoot = new BorderPane();
    private final BorderPane detachedRoot = new BorderPane();
    private Stage mainStage;
    private Stage listStage;
    private Stage historyStage;
    private Scene normalScene;
    private Scene compactScene;
    private Rectangle2D normalBounds;
    private boolean normalMaximized;
    private boolean compactView;
    private boolean splitView;
    private double dividerPosition = 0.55;
    private LiveSession session;
    private AnimationTimer refresh;
    private TextCorpus corpus;

    public static void launchApp() { Application.launch(LiveApplication.class); }

    @Override public void init() throws IOException { corpus = new TextCorpus(); }

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

        playerNames.getItems().add(AUTOMATIC_PLAYER_NAME);
        playerNames.setValue(AUTOMATIC_PLAYER_NAME);
        playerNames.setCellFactory(list -> playerNameCell());
        playerNames.setButtonCell(playerNameCell());
        playerNames.setPrefWidth(320);
        Label playerNameLabel = new Label("当前玩家");
        playerNameLabel.setLabelFor(playerNames);
        Button addPlayer = new Button("添加");
        Button editPlayer = new Button("修改");
        Button deletePlayer = new Button("删除");
        addPlayer.setOnAction(event -> editPlayerName(true));
        editPlayer.setOnAction(event -> editPlayerName(false));
        editPlayer.disableProperty().bind(playerNames.valueProperty().isEqualTo(AUTOMATIC_PLAYER_NAME));
        deletePlayer.disableProperty().bind(playerNames.valueProperty().isEqualTo(AUTOMATIC_PLAYER_NAME));
        deletePlayer.setOnAction(event -> {
            updatingPlayerNames = true;
            playerNames.getItems().remove(playerNames.getValue());
            playerNames.setValue(AUTOMATIC_PLAYER_NAME);
            updatingPlayerNames = false;
            updatePlayerName();
        });

        fontSize.setPrefWidth(85);
        Label fontSizeLabel = new Label("译文字号");
        fontSizeLabel.setLabelFor(fontSize);
        fontSize.valueProperty().addListener((observable, previous, size) -> {
            chinese.setFont(Font.font("Microsoft YaHei", size));
            dialogueText.setFont(Font.font("Microsoft YaHei", size));
        });
        compactMode.setOnAction(event -> setCompactMode(compactMode.isSelected()));
        Button split = new Button("拆分文本框");
        split.setOnAction(event -> splitTexts());
        Button historyButton = new Button("识别历史");
        historyButton.setOnAction(event -> showHistory());
        VBox toolbar = new VBox(10, new HBox(8, windows, reload, start, stop, calibrate),
                new HBox(8, playerNameLabel, playerNames, addPlayer, editPlayer, deletePlayer),
                new HBox(8, fontSizeLabel, fontSize, compactMode, split, historyButton),
                new Label("采集配置：new3ds_view · 仅上屏 · VC 原生 1:1 · 投屏点对点整数倍率"), status);
        toolbar.setPadding(new Insets(16));
        status.setWrapText(true);
        text.setEditable(false);
        text.setWrapText(true);
        text.setFont(Font.font("Meiryo", 20));
        text.setPromptText("稳定识别后的日文会显示在这里");
        for (var area : java.util.List.of(chinese, dialogueText)) {
            area.setEditable(false);
            area.setWrapText(true);
            area.setFont(Font.font("Microsoft YaHei", fontSize.getValue()));
            area.setPromptText("中文条件满足后显示译文；其余情况保留日文与状态");
            VBox.setVgrow(area, Priority.ALWAYS);
            area.setMinSize(0, 0);
            ContextMenu menu = new ContextMenu();
            MenuItem mode = new MenuItem();
            mode.setOnAction(event -> setCompactMode(!compactView));
            MenuItem layout = new MenuItem();
            layout.setOnAction(event -> {
                if (splitView) mergeTexts();
                else splitTexts();
            });
            Menu sizeMenu = new Menu("译文字号");
            MenuItem smaller = new MenuItem("缩小");
            MenuItem larger = new MenuItem("放大");
            smaller.setOnAction(event -> fontSize.decrement());
            larger.setOnAction(event -> fontSize.increment());
            sizeMenu.getItems().addAll(smaller, larger);
            MenuItem copy = new MenuItem("复制");
            copy.setOnAction(event -> area.copy());
            MenuItem selectAll = new MenuItem("全选");
            selectAll.setOnAction(event -> area.selectAll());
            MenuItem historyItem = new MenuItem("识别历史（最近10条）");
            historyItem.setOnAction(event -> showHistory());
            menu.getItems().addAll(mode, layout, sizeMenu, historyItem, copy, selectAll);
            menu.setOnShowing(event -> {
                mode.setText(compactView ? "退出简洁模式" : "进入简洁模式");
                layout.setText(splitView ? "合并文本框" : "拆分为独立窗口");
                sizeMenu.setText("译文字号（" + fontSize.getValue() + "）");
                smaller.setDisable(fontSize.getValue() == 12);
                larger.setDisable(fontSize.getValue() == 36);
                copy.setDisable(area.getSelectedText().isEmpty());
            });
            area.setContextMenu(menu);
        }
        preview.setFitWidth(320);
        preview.setFitHeight(288);
        preview.setPreserveRatio(true);
        preview.setSmooth(false);
        VBox imagePanel = new VBox(8, new Label("GB 画面"), preview);
        imagePanel.setPadding(new Insets(0, 16, 0, 16));
        ScrollPane imageScroll = new ScrollPane(imagePanel);
        imageScroll.setFitToWidth(true);
        imageScroll.setPrefWidth(355);
        dialoguePanel.setMinSize(0, 0);
        listPanel.setMinSize(0, 0);
        translation.getItems().setAll(dialoguePanel, listPanel);
        translation.setOrientation(Orientation.VERTICAL);
        translation.setDividerPositions(0.55);
        translationTab.setContent(translation);
        TabPane content = new TabPane(translationTab, new Tab("原始日文", text));
        content.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        BorderPane center = new BorderPane(content);
        center.setLeft(imageScroll);
        BorderPane.setMargin(content, new Insets(0, 16, 0, 0));
        diagnostics.setEditable(false);
        diagnostics.setPrefRowCount(8);
        diagnostics.setFont(Font.font("Consolas", 12));
        TitledPane details = new TitledPane("采集与校准详情", diagnostics);
        details.setExpanded(false);
        VBox bottom = new VBox(8, new Label("同形假名以〔候选/候选〕显示；� 表示未知字符。当前版本尚待人工验收。"), details);
        bottom.setPadding(new Insets(12, 16, 16, 16));
        BorderPane root = new BorderPane(center, toolbar, null, bottom, null);
        mainStage = stage;
        normalScene = new Scene(root, 1060, 680);
        compactScene = new Scene(compactRoot, 560, 380);
        listStage = new Stage();
        listStage.initOwner(stage);
        listStage.setTitle("G1 OCR — 列表与操作");
        listStage.setScene(new Scene(detachedRoot, 420, 300));
        listStage.setMinWidth(220);
        listStage.setMinHeight(140);
        listStage.setOnCloseRequest(event -> {
            event.consume();
            mergeTexts();
        });
        historyList.setPlaceholder(new Label("暂无识别历史"));
        historyList.setMinWidth(180);
        historyList.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(LiveSession.HistoryEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                setText(empty || entry == null ? null : "#" + entry.sequence() + " · "
                        + entry.recordedAt().format(HISTORY_TIME) + "\n"
                        + regionName(entry.layout(), entry.semanticType()) + " · "
                        + entry.rawJapanese().replace('\n', ' '));
            }
        });
        historyList.getSelectionModel().selectedItemProperty().addListener((observable, previous, entry) -> {
            if (!updatingHistory) showHistoryEntry(entry);
        });
        for (var area : java.util.List.of(historyJapanese, historyChinese, historyStatus)) {
            area.setEditable(false);
            area.setWrapText(true);
            area.setMinHeight(0);
            area.setPromptText("选择左侧历史记录查看");
        }
        historyJapanese.setFont(Font.font("Meiryo", 18));
        historyChinese.setFont(Font.font("Microsoft YaHei", 18));
        historyStatus.setFont(Font.font("Microsoft YaHei", 14));
        historyStatus.setPrefRowCount(4);
        VBox.setVgrow(historyJapanese, Priority.ALWAYS);
        VBox.setVgrow(historyChinese, Priority.ALWAYS);
        VBox historyDetail = new VBox(6, new Label("原始日文"), historyJapanese,
                new Label("中文结果（当时记录）"), historyChinese, new Label("匹配与显示状态"), historyStatus);
        historyDetail.setMinWidth(0);
        SplitPane historyContent = new SplitPane(historyList, historyDetail);
        historyContent.setDividerPositions(0.33);
        BorderPane historyRoot = new BorderPane(historyContent);
        historyRoot.setTop(new Label("最近10条稳定识别 · 包含全部识别区域 · 仅本次运行保留"));
        historyRoot.setPadding(new Insets(12));
        historyStage = new Stage();
        historyStage.initOwner(stage);
        historyStage.setTitle("G1 OCR — 识别历史");
        historyStage.setScene(new Scene(historyRoot, 940, 620));
        historyStage.setMinWidth(700);
        historyStage.setMinHeight(440);
        stage.setTitle("G1 OCR 翻译 — 开发版");
        stage.setScene(normalScene);
        stage.setMinWidth(860);
        stage.setMinHeight(560);
        stage.show();
        session = new LiveSession(latest::set, corpus);
        boolean initializePresets = !Files.exists(playerNamesFile);
        if (!initializePresets) {
            try {
                var saved = settingsJson.readTree(playerNamesFile.toFile());
                saved.path("names").forEach(name -> playerNames.getItems().add(name.asText()));
                String selected = saved.path("selected").asText();
                if (playerNames.getItems().contains(selected)) playerNames.setValue(selected);
                initializePresets = !saved.path("presetsInitialized").asBoolean();
            } catch (IOException error) {
                showPlayerNameError("无法读取已保存的玩家名：" + error.getMessage());
            }
        }
        if (initializePresets) {
            playerNames.getItems().addAll(corpus.presetNames().stream()
                    .filter(name -> !playerNames.getItems().contains(name)).toList());
            savePlayerNames();
        }
        session.selectPlayerName(playerNames.getValue().equals(AUTOMATIC_PLAYER_NAME) ? null : playerNames.getValue());
        playerNames.valueProperty().addListener((observable, previous, selected) -> {
            if (!updatingPlayerNames) updatePlayerName();
        });
        // One atomic slot: a stalled FX thread never queues a backlog of old OCR views.
        refresh = new AnimationTimer() {
            @Override public void handle(long now) {
                LiveSession.View view = latest.getAndSet(null);
                if (view != null) {
                    if (!historyList.getItems().equals(view.history())) {
                        var selected = historyList.getSelectionModel().getSelectedItem();
                        updatingHistory = true;
                        historyList.getItems().setAll(view.history());
                        if (selected != null && view.history().contains(selected))
                            historyList.getSelectionModel().select(selected);
                        else historyList.getSelectionModel().selectFirst();
                        updatingHistory = false;
                        var current = historyList.getSelectionModel().getSelectedItem();
                        if (current != selected) showHistoryEntry(current);
                    }
                    if (view.selectionId() == session.selectionId()
                            && view.playerNameRevision() == session.playerNameRevision()) render(view);
                }
            }
        };
        refresh.start();
        reload.fire();
    }

    private void showHistory() {
        historyStage.show();
        historyStage.toFront();
    }

    private void showHistoryEntry(LiveSession.HistoryEntry entry) {
        if (entry == null) {
            historyJapanese.clear(); historyChinese.clear(); historyStatus.clear();
            return;
        }
        historyJapanese.setText(entry.rawJapanese());
        StringBuilder chinese = new StringBuilder();
        StringBuilder states = new StringBuilder("#" + entry.sequence() + " · "
                + entry.recordedAt().format(HISTORY_TIME) + " · "
                + regionName(entry.layout(), entry.semanticType()));
        for (var unit : entry.translation()) {
            chinese.append(unit.chinese().isEmpty() ? "〔暂无可用译文〕" : unit.chinese()).append('\n');
            String match = switch (unit.matchStatus()) {
                case "unique" -> "唯一匹配";
                case "equivalent" -> "多来源一致";
                case "ambiguous" -> "候选不一致";
                case "missing" -> "未收录";
                case "unreliable" -> "字符不可靠";
                default -> unit.matchStatus();
            };
            String message = translationMessage(unit.displayStatus());
            if (message.isEmpty()) message = unit.chinese().isEmpty() ? "无中文内容" : "已翻译";
            states.append("\n\n").append(unit.rawJapanese()).append("\n匹配：").append(match)
                    .append("；显示：").append(message);
        }
        historyChinese.setText(chinese.toString());
        historyStatus.setText(states.toString());
    }

    private ListCell<String> playerNameCell() {
        return new ListCell<>() {
            @Override protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                if (empty || name == null) { setText(null); return; }
                if (name.equals(AUTOMATIC_PLAYER_NAME)) { setText(name); return; }
                var chinese = corpus.nameTranslations(name);
                String translation = chinese.isEmpty() ? "暂无译名"
                        : (chinese.size() > 1 ? "多译：" : "") + String.join("／", chinese);
                setText(name + "（" + translation + "）");
            }
        };
    }

    private void editPlayerName(boolean adding) {
        String previous = playerNames.getValue();
        TextInputDialog dialog = new TextInputDialog(adding ? "" : previous);
        dialog.initOwner(mainStage);
        dialog.setTitle(adding ? "添加玩家名" : "修改玩家名");
        dialog.setHeaderText("填写游戏中的日文玩家名；中文沿用现有词库，无对应时保留原名。");
        dialog.setContentText("日文玩家名：");
        var entered = dialog.showAndWait();
        if (entered.isEmpty()) return;
        String name = entered.get().strip();
        if (name.isEmpty()) { showPlayerNameError("玩家名不能为空。"); return; }
        if (playerNames.getItems().contains(name) && (adding || !name.equals(previous))) {
            showPlayerNameError("该玩家名已存在，请从下拉框选择。");
            return;
        }
        updatingPlayerNames = true;
        if (adding) playerNames.getItems().add(name);
        else playerNames.getItems().set(playerNames.getItems().indexOf(previous), name);
        playerNames.setValue(name);
        updatingPlayerNames = false;
        updatePlayerName();
    }

    private void updatePlayerName() {
        String selected = playerNames.getValue().equals(AUTOMATIC_PLAYER_NAME) ? null : playerNames.getValue();
        latest.set(null);
        clear("玩家名已更新，等待识别刷新");
        session.selectPlayerName(selected);
        savePlayerNames();
    }

    private void savePlayerNames() {
        String selected = playerNames.getValue();
        try {
            Files.createDirectories(playerNamesFile.getParent());
            settingsJson.writerWithDefaultPrettyPrinter().writeValue(playerNamesFile.toFile(),
                    Map.of("names", playerNames.getItems().stream().skip(1).toList(),
                            "selected", selected.equals(AUTOMATIC_PLAYER_NAME) ? "" : selected,
                            "presetsInitialized", true));
        } catch (IOException error) {
            showPlayerNameError("无法保存玩家名，本次更改仅在当前运行生效：" + error.getMessage());
        }
    }

    private void showPlayerNameError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(mainStage);
        alert.setTitle("玩家名设置");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void setCompactMode(boolean enabled) {
        if (compactView == enabled) return;
        if (!enabled) mergeTexts();
        compactView = enabled;
        compactMode.setSelected(enabled);
        dialogueHeading.setVisible(!enabled);
        dialogueHeading.setManaged(!enabled);
        listHeading.setVisible(!enabled);
        listHeading.setManaged(!enabled);
        if (enabled) {
            normalBounds = new Rectangle2D(mainStage.getX(), mainStage.getY(),
                    mainStage.getWidth(), mainStage.getHeight());
            normalMaximized = mainStage.isMaximized();
            mainStage.setMaximized(false);
            translationTab.setContent(null);
            compactRoot.setCenter(translation);
            mainStage.setMinWidth(220);
            mainStage.setMinHeight(140);
            mainStage.setScene(compactScene);
            mainStage.setWidth(560);
            mainStage.setHeight(380);
            mainStage.setTitle("G1 OCR — 简洁模式（右键设置）");
        } else {
            compactRoot.setCenter(null);
            translationTab.setContent(translation);
            mainStage.setMaximized(false);
            mainStage.setScene(normalScene);
            mainStage.setMinWidth(860);
            mainStage.setMinHeight(560);
            mainStage.setX(normalBounds.getMinX());
            mainStage.setY(normalBounds.getMinY());
            mainStage.setWidth(normalBounds.getWidth());
            mainStage.setHeight(normalBounds.getHeight());
            mainStage.setMaximized(normalMaximized);
            mainStage.setTitle("G1 OCR 翻译 — 开发版");
        }
    }

    private void splitTexts() {
        setCompactMode(true);
        dividerPosition = translation.getDividerPositions()[0];
        compactRoot.setCenter(null);
        translation.getItems().clear();
        compactRoot.setCenter(dialoguePanel);
        detachedRoot.setCenter(listPanel);
        splitView = true;
        mainStage.setTitle("G1 OCR — 对话（右键设置）");
        listStage.setX(mainStage.getX() + 40);
        listStage.setY(mainStage.getY() + 40);
        listStage.show();
    }

    private void mergeTexts() {
        if (!splitView) return;
        listStage.hide();
        detachedRoot.setCenter(null);
        compactRoot.setCenter(null);
        translation.getItems().setAll(dialoguePanel, listPanel);
        compactRoot.setCenter(translation);
        translation.setDividerPositions(dividerPosition);
        splitView = false;
        mainStage.setTitle("G1 OCR — 简洁模式（右键设置）");
    }

    private void clear(String message) {
        status.setText(message); text.clear(); chinese.clear(); dialogueText.clear(); diagnostics.clear(); preview.setImage(null);
    }

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
        StringBuilder details = new StringBuilder(view.diagnostics());
        StringBuilder body = new StringBuilder();
        StringBuilder dialogues = new StringBuilder(), lists = new StringBuilder();
        for (var region : view.regions()) {
            var result = region.text();
            boolean battleAction = result.layout().equals("battle_action");
            boolean itemList = result.layout().equals("shared_list") || result.layout().equals("shop_items")
                    || result.layout().equals("battle_moves") || battleAction;
            Integer selectedUnit = battleAction ? region.selectedItem() : null;
            if (itemList && !battleAction) {
                int itemIndex = 0;
                // OCR omits empty rows; the physical cursor row must map to a nonempty body item, never a footer.
                for (int rowIndex = 0; rowIndex < result.textGrids().size(); rowIndex++) {
                    if (result.diagnostics().get(rowIndex).rawOcr().isEmpty()) continue;
                    if (java.util.Objects.equals(region.selectedItem(), rowIndex)) selectedUnit = itemIndex;
                    itemIndex++;
                }
            }
            String label = regionName(result.layout(), result.semanticType());
            if (!itemList) {
                body.append(label);
                if (region.selectedItem() != null) body.append(" · 选中第 ").append(region.selectedItem() + 1).append(" 项");
                body.append('\n');
            }
            StringBuilder translated = result.layout().equals("dialogue") ? dialogues : lists;
            for (int rowIndex = 0; rowIndex < result.diagnostics().size(); rowIndex++) {
                var row = result.diagnostics().get(rowIndex);
                if (!row.rawOcr().isEmpty()) {
                    if (battleAction) {
                        String[] options = line(row).split(" +");
                        for (int column = 0; column < options.length; column++) {
                            body.append(java.util.Objects.equals(region.selectedItem(), rowIndex * 2 + column) ? "▶ " : "  ");
                            body.append(options[column]).append('\n');
                        }
                    } else {
                        if (itemList) body.append(java.util.Objects.equals(region.selectedItem(), rowIndex) ? "▶ " : "  ");
                        body.append(line(row)).append('\n');
                    }
                }
            }
            body.append('\n');
            if (!itemList) {
                translated.append(label);
                if (region.selectedItem() != null) translated.append(" · 选中第 ").append(region.selectedItem() + 1).append(" 项");
                translated.append('\n');
            }
            for (int itemIndex = 0; itemIndex < region.translation().size(); itemIndex++) {
                var unit = region.translation().get(itemIndex);
                if (itemList) translated.append(java.util.Objects.equals(selectedUnit, itemIndex) ? "▶ " : "  ");
                translated.append(unit.display()).append('\n');
                String message = translationMessage(unit.displayStatus());
                if (!message.isEmpty()) translated.append('〔').append(message).append("〕\n");
                details.append("\n匹配=").append(unit.matchStatus()).append(" 显示=").append(unit.displayStatus());
                for (var candidate : unit.candidates()) {
                    details.append("\n").append(candidate.recordId())
                        .append(" #").append(candidate.ordinal()).append("\n").append(candidate.source())
                        .append("\n变量=").append(candidate.variables()).append(" 修订=").append(candidate.revision());
                    if (!candidate.reusedPlayerName().isEmpty())
                        details.append(playerNames.getValue().equals(AUTOMATIC_PLAYER_NAME)
                                ? "\n沿用前文玩家名=" : "\n使用所选玩家名=").append(candidate.reusedPlayerName());
                }
            }
            translated.append('\n');
        }
        if (!text.getText().equals(body.toString())) text.setText(body.toString());
        if (!chinese.getText().equals(lists.toString())) chinese.setText(lists.toString());
        if (!dialogueText.getText().equals(dialogues.toString())) dialogueText.setText(dialogues.toString());
        diagnostics.setText(details.toString());
        if (view.gb() == null) preview.setImage(null);
        else {
            WritableImage image = new WritableImage(160, 144);
            int[] argb = view.gb().getRGB(0, 0, 160, 144, null, 0, 160);
            image.getPixelWriter().setPixels(0, 0, 160, 144, PixelFormat.getIntArgbInstance(), argb, 0, 160);
            preview.setImage(image);
        }
    }

    private String regionName(String layout, String semanticType) {
        return switch (layout) {
            case "dialogue" -> "主对话";
            case "battle_action" -> "战斗操作";
            case "battle_moves" -> "战斗技能";
            case "shop_items" -> "商店商品";
            default -> semanticType.equals("pc_pokemon") ? "PC 宝可梦" : "道具列表（用途未确定）";
        };
    }

    private String translationMessage(String displayStatus) {
        return switch (displayStatus) {
            case "ready" -> "";
            case "ready_waiting" -> "后续内容等待完整显示";
            case "ambiguous" -> "候选译文或显示条件存在差异，保留日文";
            case "context_required" -> "缺少连续前文，保留日文";
            case "variables_missing" -> "译文所需变量不足（玩家名可使用所选或已确认值），保留日文";
            case "source_semantic_mismatch" -> "原汉化含义有误，暂时保留日文";
            case "wait_for_semantic_unit" -> "等待语义单元完整显示";
            case "incomplete" -> "当前文字片段尚未完整显示";
            case "unknown_character" -> "存在未识别字符";
            default -> "未收录或当前片段不可翻译";
        };
    }

    @Override public void stop() {
        if (historyStage != null) historyStage.hide();
        if (listStage != null) listStage.hide();
        if (refresh != null) refresh.stop();
        if (session != null) session.close();
    }
}
