package io.github.g1ocr;

import javafx.application.Application;

/** M0 bootstrap. No OCR, GT, translation data or window capture is loaded. */
public final class Main {
    private Main() { }
    public static void main(String[] args) {
        if (args.length == 1 && "--self-check".equals(args[0])) {
            System.out.println("java=" + System.getProperty("java.version"));
            System.out.println("javafx=" + System.getProperty("javafx.runtime.version", "bundled JDK 8"));
            System.out.println("javafxClass=" + Application.class.getName());
            System.out.println("Bootstrap linkage only; UI presentation and capture remain unverified.");
            return;
        }
        Application.launch(BootstrapWindow.class, args);
    }
}
