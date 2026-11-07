package io.github.g1ocr.capture;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.g1ocr.OcrPipeline;
import io.github.g1ocr.image.GbFrame;
import io.github.g1ocr.image.ViewportResolver;
import io.github.g1ocr.ocr.PixelFont;
import io.github.g1ocr.ocr.PixelOcr;
import io.github.g1ocr.region.StabilityDetector;
import io.github.g1ocr.text.TextCorpus;
import io.github.g1ocr.text.TextResolver;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

/** One worker owns native capture, calibration and OCR. The UI receives only the latest view. */
public final class LiveSession implements AutoCloseable {
    public record RegionView(PixelOcr.Result text, Integer selectedItem, List<TextResolver.Unit> translation) {}
    public record HistoryEntry(long sequence, LocalDateTime recordedAt, String layout, String semanticType,
                               String rawJapanese, List<TextResolver.Unit> translation) {}
    public record View(long selectionId, long playerNameRevision, String status, String diagnostics,
                       BufferedImage gb, List<RegionView> regions, List<HistoryEntry> history) {}
    private record ReportSnapshot(BufferedImage raw, BufferedImage gb, Map<String, Object> data, ReportSnapshot lastFrame) {}
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "g1-capture"));
    private final ExecutorService reportWriter = Executors.newSingleThreadExecutor(r -> new Thread(r, "g1-bugreport"));
    private final AtomicLong selectionId = new AtomicLong();
    private final AtomicLong playerNameRevision = new AtomicLong();
    private final Consumer<View> listener;
    private final OcrPipeline pipeline;
    private final ViewportResolver resolver;
    private final TextResolver translator;
    private final Map<String, OcrPipeline.StableResult> published = new LinkedHashMap<>();
    private final Map<String, List<TextResolver.Unit>> translations = new LinkedHashMap<>();
    private final java.util.Set<StabilityDetector.Key> inputWaits = new java.util.HashSet<>();
    private final ArrayDeque<HistoryEntry> history = new ArrayDeque<>();
    private final Map<String, OcrPipeline.StableResult> recordedResults = new LinkedHashMap<>();
    private long historySequence;
    private WindowCapture capture;
    private WindowCapture.Window window;
    private long activeSelection;
    private long activePlayerNameRevision;
    private long revision = -1;
    private GbFrame.Mapping mapping;
    private WindowCapture.Poll reportPoll;
    private ViewportResolver.Resolution reportResolution;
    private String pollObservedAt;
    private String errorStack = "";
    // Poll creates new images for new frames; published images and result records are never mutated.
    private volatile ReportSnapshot reportSnapshot;
    private ReportSnapshot lastFrameSnapshot;

    public LiveSession(Consumer<View> listener, TextCorpus corpus) throws IOException {
        this.listener = listener;
        pipeline = new OcrPipeline();
        resolver = new ViewportResolver(new PixelFont());
        translator = new TextResolver(corpus);
        emit("尚未开始捕获", "", null, List.of());
        worker.scheduleWithFixedDelay(this::tick, 0, 100, TimeUnit.MILLISECONDS);
    }

    public long selectionId() { return selectionId.get(); }
    public long playerNameRevision() { return playerNameRevision.get(); }

    public void selectPlayerName(String name) {
        long revision = playerNameRevision.incrementAndGet();
        worker.execute(() -> {
            if (revision != playerNameRevision.get()) return;
            translator.selectPlayerName(name);
            translations.clear();
            published.forEach((id, value) -> translations.put(id, translator.resolve(value.result(), inputWaits.contains(value.key()))));
            activePlayerNameRevision = revision;
            if (capture == null) emit("玩家名已更新，请选择窗口并开始", "", null, List.of());
        });
    }

    public void refreshWindows(Consumer<List<WindowCapture.Window>> result, Consumer<String> error) {
        worker.execute(() -> {
            try { result.accept(WindowCapture.windows()); }
            catch (RuntimeException | LinkageError e) { error.accept(e.toString()); }
        });
    }

    public void start(WindowCapture.Window selected) {
        long id = selectionId.incrementAndGet();
        worker.execute(() -> {
            if (id != selectionId.get()) return;
            stopCapture();
            lastFrameSnapshot = null;
            errorStack = "";
            activeSelection = id;
            window = selected;
            try {
                capture = new WindowCapture(selected);
                emit("等待捕获与校准", "", null, List.of());
            } catch (RuntimeException | LinkageError e) { fail(e); }
        });
    }

    public void stop() {
        long id = selectionId.incrementAndGet();
        worker.execute(() -> { stopCapture(); activeSelection = id; emit("已停止", "", null, List.of()); });
    }

    public void recalibrate() {
        long id = selectionId.incrementAndGet();
        worker.execute(() -> {
            activeSelection = id;
            clearRecognition();
            reportResolution = null;
            emit(capture == null ? "请先选择窗口并开始" : "等待重新校准", "", null, List.of());
        });
    }

    private void clearRecognition() {
        pipeline.invalidate(); resolver.reset(); published.clear(); translations.clear(); translator.reset();
        recordedResults.clear();
        inputWaits.clear();
        mapping = null; revision = -1;
    }

    private void stopCapture() {
        if (capture != null) { capture.close(); capture = null; }
        clearRecognition();
        reportPoll = null; reportResolution = null; pollObservedAt = null;
    }

    private void emit(String status, String diagnostics, BufferedImage gb, List<RegionView> regions) {
        var data = new LinkedHashMap<String, Object>();
        data.put("schemaVersion", 1);
        data.put("observedAt", OffsetDateTime.now().toString());
        data.put("window", window);
        data.put("captureActive", capture != null);
        data.put("selectionId", activeSelection);
        data.put("playerNameRevision", activePlayerNameRevision);
        data.put("status", status);
        data.put("diagnostics", diagnostics);
        data.put("errorStack", errorStack);
        data.put("pollObservedAt", pollObservedAt);
        if (reportPoll != null) {
            var poll = new LinkedHashMap<String, Object>();
            poll.put("status", reportPoll.status()); poll.put("detail", reportPoll.detail());
            poll.put("revision", reportPoll.revision()); poll.put("frameId", reportPoll.frameId());
            poll.put("frameArrivalNanos", reportPoll.frameArrivalNanos()); poll.put("sampleTimeNanos", reportPoll.sampleTimeNanos());
            poll.put("dpi", reportPoll.dpi()); poll.put("clientHints", reportPoll.clientHints());
            if (reportPoll.image() != null) {
                poll.put("width", reportPoll.image().getWidth()); poll.put("height", reportPoll.image().getHeight());
            }
            data.put("capture", poll);
        }
        if (reportResolution != null) {
            var calibration = new LinkedHashMap<String, Object>();
            calibration.put("status", reportResolution.status()); calibration.put("detail", reportResolution.detail());
            calibration.put("mapping", reportResolution.mapping()); calibration.put("candidates", reportResolution.candidates());
            if (reportResolution.frame() != null) calibration.put("threshold", reportResolution.frame().threshold());
            data.put("calibration", calibration);
        }
        data.put("regions", List.copyOf(regions));
        data.put("stableResults", List.copyOf(published.values()));
        data.put("stability", pipeline.stabilityDiagnostics());
        data.put("inputWaits", List.copyOf(inputWaits));
        data.put("translationContext", translator.diagnostics());
        data.put("history", history.stream().map(entry -> Map.of("sequence", entry.sequence(),
                "recordedAt", entry.recordedAt().toString(), "layout", entry.layout(), "semanticType", entry.semanticType(),
                "rawJapanese", entry.rawJapanese(), "translation", entry.translation())).toList());
        BufferedImage raw = reportPoll == null ? null : reportPoll.image();
        BufferedImage normalized = reportResolution == null || reportResolution.frame() == null
                ? null : reportResolution.frame().image();
        ReportSnapshot snapshot = new ReportSnapshot(raw, normalized, data,
                raw == null || !errorStack.isEmpty() ? lastFrameSnapshot : null);
        if (raw != null && errorStack.isEmpty()) lastFrameSnapshot = snapshot;
        reportSnapshot = snapshot;
        listener.accept(new View(activeSelection, activePlayerNameRevision, status, diagnostics, gb,
                List.copyOf(regions), List.copyOf(history)));
    }

    private void fail(Throwable error) {
        var trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        errorStack = trace.toString();
        var failedPoll = reportPoll;
        var failedResolution = reportResolution;
        String failedObservedAt = pollObservedAt;
        stopCapture();
        reportPoll = failedPoll; reportResolution = failedResolution; pollObservedAt = failedObservedAt;
        emit("捕获已停止：" + error.getMessage(), error.toString(), null, List.of());
    }

    /** Freeze the latest completed sample now; disk I/O never runs on the FX or capture thread. */
    public void saveBugReport(Map<String, Object> uiState, Consumer<Path> saved, Consumer<String> failed) {
        ReportSnapshot snapshot = reportSnapshot;
        ReportSnapshot previous = snapshot.lastFrame();
        OffsetDateTime requestedAt = OffsetDateTime.now();
        Map<String, Object> ui = Map.copyOf(uiState);
        reportWriter.execute(() -> {
            Path directory = null;
            try {
                Path root = Path.of("bugreport").toAbsolutePath();
                Files.createDirectories(root);
                directory = Files.createTempDirectory(root, requestedAt.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")) + "-");
                var report = new LinkedHashMap<>(snapshot.data());
                report.put("requestedAt", requestedAt.toString());
                report.put("ui", ui);
                var environment = new LinkedHashMap<String, Object>();
                for (String key : List.of("java.version", "java.vendor", "java.vm.name", "java.home", "javafx.version",
                        "os.name", "os.version", "os.arch", "file.encoding", "user.dir", "g1ocr.version"))
                    environment.put(key, System.getProperty(key));
                environment.put("availableProcessors", Runtime.getRuntime().availableProcessors());
                environment.put("maxMemory", Runtime.getRuntime().maxMemory());
                environment.put("totalMemory", Runtime.getRuntime().totalMemory());
                environment.put("freeMemory", Runtime.getRuntime().freeMemory());
                Path application = Path.of(LiveSession.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                Path dll = Path.of(System.getProperty("g1ocr.capture.dll", "output/native/Release/g1capture.dll")).toAbsolutePath();
                var artifacts = new LinkedHashMap<String, Object>();
                for (Path artifact : List.of(application, dll)) {
                    var info = new LinkedHashMap<String, Object>();
                    info.put("exists", Files.exists(artifact));
                    if (Files.isRegularFile(artifact)) {
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        try (var input = new DigestInputStream(Files.newInputStream(artifact), digest)) {
                            input.transferTo(OutputStream.nullOutputStream());
                        }
                        info.put("sha256", HexFormat.of().formatHex(digest.digest()));
                    }
                    artifacts.put(artifact.toString(), info);
                }
                environment.put("artifacts", artifacts);
                report.put("environment", environment);
                var json = JsonMapper.builder().build().writerWithDefaultPrettyPrinter();
                if (snapshot.raw() != null) ImageIO.write(snapshot.raw(), "png", directory.resolve("capture.png").toFile());
                if (snapshot.gb() != null) ImageIO.write(snapshot.gb(), "png", directory.resolve("gb.png").toFile());
                boolean includePrevious = previous != null;
                report.put("captureImage", snapshot.raw() == null ? null : "capture.png");
                report.put("gbImage", snapshot.gb() == null ? null : "gb.png");
                report.put("lastFrame", includePrevious ? "last-frame/report.json" : null);
                if (includePrevious) {
                    Path last = Files.createDirectory(directory.resolve("last-frame"));
                    ImageIO.write(previous.raw(), "png", last.resolve("capture.png").toFile());
                    if (previous.gb() != null) ImageIO.write(previous.gb(), "png", last.resolve("gb.png").toFile());
                    json.writeValue(last.resolve("report.json").toFile(), previous.data());
                }
                json.writeValue(directory.resolve("report.json").toFile(), report);
                Files.writeString(directory.resolve("README.txt"), "F1 问题现场\n"
                        + "capture.png：最近一次采样的原始捕获帧，未裁剪或缩放；gb.png：该帧校准后的 160×144 图像。\n"
                        + "report.json：对应采样的 OCR、逐字候选、翻译、来源、稳定性、上下文、历史、UI 与环境。\n"
                        + "requestedAt 是按键时间，observedAt/pollObservedAt 是现场和采样时间；stableResults 含各区域 OCR 触发时间。\n"
                        + "无有效捕获/校准时不生成对应图片；last-frame/ 如存在，是停止或故障前的历史现场，并非当前画面。\n"
                        + "环境和制品哈希在写入时采集；正常运行不依赖 Debug 日志。\n"
                        + "仅当 COMPLETE 文件存在时表示全部文件保存完成。\n");
                Files.createFile(directory.resolve("COMPLETE"));
                saved.accept(directory);
            } catch (Exception error) {
                failed.accept("保存失败：" + error + (directory == null ? "" : "\n未完成目录：" + directory));
            }
        });
    }

    private void tick() {
        if (capture == null || activeSelection != selectionId.get()) return;
        try {
            WindowCapture.Poll poll = capture.poll();
            reportPoll = poll;
            pollObservedAt = OffsetDateTime.now().toString();
            reportResolution = null;
            String diagnostics = "HWND=0x" + Long.toHexString(window.hwnd()) + " PID=" + window.pid() + " DPI=" + poll.dpi()
                    + "\n采集代次=" + poll.revision() + " 帧=" + poll.frameId()
                    + "\n帧到达 QPC ns=" + poll.frameArrivalNanos() + "\n采样 QPC ns=" + poll.sampleTimeNanos()
                    + "\n客户区候选=" + poll.clientHints();
            if (poll.status() != WindowCapture.Status.RUNNING) {
                boolean terminal = poll.status() == WindowCapture.Status.CLOSED || poll.status() == WindowCapture.Status.ERROR;
                String observedAt = pollObservedAt;
                clearRecognition();
                if (terminal) stopCapture();
                reportPoll = poll; pollObservedAt = observedAt;
                emit(poll.detail(), diagnostics, null, List.of());
                return;
            }
            if (revision != poll.revision()) { clearRecognition(); revision = poll.revision(); }
            var resolution = resolver.resolve(poll.image(), poll.clientHints(), poll.sampleTimeNanos());
            reportResolution = resolution;
            diagnostics += "\n物理图像=" + poll.image().getWidth() + "×" + poll.image().getHeight()
                    + "\n校准=" + resolution.status() + " 候选数=" + resolution.candidates() + " 映射=" + resolution.mapping();
            if (resolution.status() != ViewportResolver.Status.READY) {
                pipeline.invalidate(); published.clear(); translations.clear(); translator.reset(); mapping = null;
                emit(resolution.detail(), diagnostics, null, List.of());
                return;
            }
            if (!resolution.mapping().equals(mapping)) {
                pipeline.invalidate(); published.clear(); translations.clear(); translator.reset(); mapping = resolution.mapping();
            }
            GbFrame frame = resolution.frame();
            String session = activeSelection + ":" + revision;
            var updates = pipeline.sample(session, mapping, poll.sampleTimeNanos(), frame);
            var regions = pipeline.regions(frame);
            var visible = new java.util.HashSet<String>();
            for (var region : regions)
                if (java.util.Arrays.stream(region.textPixels(frame)).anyMatch(p -> p != 0)) visible.add(region.id());
            translator.retainRegions(visible);
            published.values().removeIf(value -> !pipeline.isCurrent(value.key()));
            translations.keySet().retainAll(published.keySet());
            recordedResults.keySet().retainAll(published.keySet());
            inputWaits.removeIf(key -> !pipeline.isCurrent(key));
            for (var update : updates) if (pipeline.isCurrent(update.key()) && update.result().hasText()) {
                published.put(update.result().id(), update);
                translations.put(update.result().id(), translator.resolve(update.result()));
            }
            List<RegionView> views = new ArrayList<>();
            boolean content = false;
            for (var region : regions) {
                if (java.util.Arrays.stream(region.textPixels(frame)).anyMatch(p -> p != 0)) content = true;
                var value = published.get(region.id());
                if (value != null) {
                    // Keep positive evidence through arrow blinking, only for this stable content generation.
                    if (pipeline.isCurrent(value.key(), frame, region) && pipeline.continuationArrow(frame, region)
                            && inputWaits.add(value.key()))
                        translations.put(region.id(), translator.resolve(value.result(), true));
                    if (inputWaits.contains(value.key())) diagnostics += "\n当前文字已观察到翻页箭头";
                    var translated = translations.get(region.id());
                    views.add(new RegionView(value.result(), region.selectedItem(), translated));
                    // Capture accepted stable results before the UI's latest-view slot can drop intermediate views.
                    if (recordedResults.get(region.id()) != value && activeSelection == selectionId.get()
                            && activePlayerNameRevision == playerNameRevision.get()) {
                        var result = value.result();
                        var lines = new ArrayList<>(result.rawOcr());
                        lines.addAll(result.footerLines());
                        history.addFirst(new HistoryEntry(++historySequence, LocalDateTime.now(), result.layout(),
                                result.semanticType(), String.join("\n", lines), List.copyOf(translated)));
                        if (history.size() > 10) history.removeLast();
                        recordedResults.put(region.id(), value);
                    }
                }
            }
            String status = views.isEmpty() ? (content ? "文字尚未稳定" : "已校准，当前无目标文字") : "识别中";
            if (views.stream().flatMap(view -> view.text().diagnostics().stream()).flatMap(line -> line.characters().stream())
                    .anyMatch(PixelOcr.CharacterResult::unknown)) status = "存在未识别字符，请检查画面像素与校准";
            emit(status, diagnostics, frame.image(), views);
        } catch (RuntimeException | LinkageError e) { fail(e); }
    }

    @Override public void close() {
        selectionId.incrementAndGet();
        worker.execute(this::stopCapture);
        worker.shutdown();
        reportWriter.shutdown();
    }
}
