package dev.moray.app;

/** Opt-in application memory matrix using the same packaged runtime and controlled fixture. */
public final class MemoryBench {
    private MemoryBench() {}
    public static void main(String[] args) throws Exception {
        BenchmarkRun.run(BenchmarkOptions.parse(true, args));
    }
}
