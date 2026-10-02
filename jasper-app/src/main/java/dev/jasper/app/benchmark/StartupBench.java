package dev.jasper.app.benchmark;

/** Opt-in macOS start-up benchmark of a packaged image; run it through {@code tools/benchmarks/run-macos.sh}. */
public final class StartupBench {
    private StartupBench() { }

    public static void main(String[] args) throws Exception {
        StartupBenchOptions options;
        try { options = StartupBenchOptions.parse(args); }
        catch (IllegalArgumentException failure) { System.err.println(failure.getMessage()); System.exit(2); return; }
        System.exit(new StartupRuns(options, StartupRuns.Host.system()).run());
    }
}
