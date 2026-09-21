package dev.jasper.app;

/** Stable packaged executable entry point; startup ownership lives in ApplicationBootstrap. */
public final class Main {
    private Main() {}
    public static void main(String[] args) { ApplicationBootstrap.main(args); }
}
