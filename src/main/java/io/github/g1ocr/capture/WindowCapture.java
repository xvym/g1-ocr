package io.github.g1ocr.capture;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** JNI/WGC ownership is confined to one worker thread; no desktop capture fallback. */
public final class WindowCapture implements AutoCloseable {
    public enum Status { WAITING, RUNNING, MINIMIZED, CLOSED, ERROR }
    public record Window(long hwnd, int pid, String title) {
        @Override public String toString() { return title + " [PID " + pid + "]"; }
    }
    /** Client rectangles are geometric hypotheses until image evidence confirms them. */
    public record Bounds(int x, int y, int width, int height) {}
    public record Poll(Status status, String detail, long revision, long frameId, long frameArrivalNanos,
                       long sampleTimeNanos, BufferedImage image, List<Bounds> clientHints, int dpi) {}
    private record NativeSnapshot(int status, String detail, long revision, long frameId, long frameArrivalNanos,
                                  long sampleTimeNanos, int width, int height, int[] pixels, int[] clientHints, int dpi) {}

    // Lazy loading lets the existing offline CLI run without a capture DLL.
    private static final class Library {
        static {
            System.load(Path.of(System.getProperty("g1ocr.capture.dll", "output/native/Release/g1capture.dll"))
                    .toAbsolutePath().toString());
        }
        static void load() {}
    }

    private long handle;
    private long revision = -1;
    private long frameId = -1;
    private BufferedImage image;

    public static List<Window> windows() { Library.load(); return List.of(windows0()); }

    public WindowCapture(Window window) {
        Library.load();
        handle = open0(window.hwnd(), window.pid());
    }

    public Poll poll() {
        NativeSnapshot snapshot = poll0(handle, revision, frameId);
        Status status = Status.values()[snapshot.status()];
        if (snapshot.revision() != revision || status != Status.RUNNING) image = null;
        if (snapshot.pixels() != null) {
            image = new BufferedImage(snapshot.width(), snapshot.height(), BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, snapshot.width(), snapshot.height(), snapshot.pixels(), 0, snapshot.width());
        }
        revision = snapshot.revision();
        frameId = snapshot.frameId();
        List<Bounds> hints = new ArrayList<>();
        for (int i = 0; i < snapshot.clientHints().length; i += 4) {
            int[] rect = snapshot.clientHints();
            Bounds bounds = new Bounds(rect[i], rect[i + 1], rect[i + 2], rect[i + 3]);
            if (bounds.width() > 0 && bounds.height() > 0 && !hints.contains(bounds)) hints.add(bounds);
        }
        return new Poll(status, snapshot.detail(), revision, frameId, snapshot.frameArrivalNanos(),
                snapshot.sampleTimeNanos(), image, List.copyOf(hints), snapshot.dpi());
    }

    @Override public void close() {
        if (handle != 0) { close0(handle); handle = 0; image = null; }
    }

    private static native Window[] windows0();
    private static native long open0(long hwnd, int pid);
    private static native NativeSnapshot poll0(long handle, long previousRevision, long previousFrameId);
    private static native void close0(long handle);
}
