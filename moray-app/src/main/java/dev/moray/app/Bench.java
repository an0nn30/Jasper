package dev.moray.app;

/** Opt-in native throughput entry point; never starts Main or reads user configuration. */
public final class Bench {
    private Bench() {}
    public static void main(String[] args) throws Exception {
        BenchmarkRun.run(BenchmarkOptions.parse(false, args));
    }
}
