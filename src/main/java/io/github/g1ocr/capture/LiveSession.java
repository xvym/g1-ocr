package io.github.g1ocr.capture;

import io.github.g1ocr.OcrPipeline;
import io.github.g1ocr.image.GbFrame;
import io.github.g1ocr.image.ViewportResolver;
import io.github.g1ocr.ocr.PixelFont;
import io.github.g1ocr.ocr.PixelOcr;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** One worker owns native capture, calibration and OCR. The UI receives only the latest view. */
public final class LiveSession implements AutoCloseable {
    public record RegionView(PixelOcr.Result text, Integer selectedItem) {}
    public record View(long selectionId, String status, String diagnostics, BufferedImage gb, List<RegionView> regions) {}
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "g1-capture"));
    private final AtomicLong selectionId = new AtomicLong();
    private final Consumer<View> listener;
    private final OcrPipeline pipeline;
    private final ViewportResolver resolver;
    private final Map<String, OcrPipeline.StableResult> published = new LinkedHashMap<>();
    private WindowCapture capture;
    private WindowCapture.Window window;
    private long activeSelection;
    private long revision = -1;
    private GbFrame.Mapping mapping;

    public LiveSession(Consumer<View> listener) throws IOException {
        this.listener = listener;
        pipeline = new OcrPipeline();
        resolver = new ViewportResolver(new PixelFont());
        worker.scheduleWithFixedDelay(this::tick, 0, 100, TimeUnit.MILLISECONDS);
    }

    public long selectionId() { return selectionId.get(); }

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
            emit(capture == null ? "请先选择窗口并开始" : "等待重新校准", "", null, List.of());
        });
    }

    private void clearRecognition() {
        pipeline.invalidate(); resolver.reset(); published.clear(); mapping = null; revision = -1;
    }

    private void stopCapture() {
        if (capture != null) { capture.close(); capture = null; }
        clearRecognition();
    }

    private void emit(String status, String diagnostics, BufferedImage gb, List<RegionView> regions) {
        listener.accept(new View(activeSelection, status, diagnostics, gb, List.copyOf(regions)));
    }

    private void fail(Throwable error) {
        stopCapture();
        emit("捕获已停止：" + error.getMessage(), error.toString(), null, List.of());
    }

    private void tick() {
        if (capture == null || activeSelection != selectionId.get()) return;
        try {
            WindowCapture.Poll poll = capture.poll();
            String diagnostics = "HWND=0x" + Long.toHexString(window.hwnd()) + " PID=" + window.pid() + " DPI=" + poll.dpi()
                    + "\n采集代次=" + poll.revision() + " 帧=" + poll.frameId()
                    + "\n帧到达 QPC ns=" + poll.frameArrivalNanos() + "\n采样 QPC ns=" + poll.sampleTimeNanos()
                    + "\n客户区候选=" + poll.clientHints();
            if (poll.status() != WindowCapture.Status.RUNNING) {
                boolean terminal = poll.status() == WindowCapture.Status.CLOSED || poll.status() == WindowCapture.Status.ERROR;
                clearRecognition();
                if (terminal) stopCapture();
                emit(poll.detail(), diagnostics, null, List.of());
                return;
            }
            if (revision != poll.revision()) { clearRecognition(); revision = poll.revision(); }
            var resolution = resolver.resolve(poll.image(), poll.clientHints(), poll.sampleTimeNanos());
            diagnostics += "\n物理图像=" + poll.image().getWidth() + "×" + poll.image().getHeight()
                    + "\n校准=" + resolution.status() + " 候选数=" + resolution.candidates() + " 映射=" + resolution.mapping();
            if (resolution.status() != ViewportResolver.Status.READY) {
                pipeline.invalidate(); published.clear(); mapping = null;
                emit(resolution.detail(), diagnostics, null, List.of());
                return;
            }
            if (!resolution.mapping().equals(mapping)) {
                pipeline.invalidate(); published.clear(); mapping = resolution.mapping();
            }
            GbFrame frame = resolution.frame();
            String session = activeSelection + ":" + revision;
            var updates = pipeline.sample(session, mapping, poll.sampleTimeNanos(), frame);
            published.values().removeIf(value -> !pipeline.isCurrent(value.key()));
            for (var update : updates) if (pipeline.isCurrent(update.key()) && update.result().hasText())
                published.put(update.result().id(), update);
            List<RegionView> views = new ArrayList<>();
            boolean content = false;
            for (var region : pipeline.regions(frame)) {
                if (java.util.Arrays.stream(region.textPixels(frame)).anyMatch(p -> p != 0)) content = true;
                var value = published.get(region.id());
                if (value != null) views.add(new RegionView(value.result(), region.selectedItem()));
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
    }
}
