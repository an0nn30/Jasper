package dev.jasper.editor.testsupport;

import dev.jasper.editor.syntax.Languages;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.text.Segment;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMaker;
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.modes.JavaTokenMaker;

/** Instruments the real Java lexer without a production test hook or a timing acceptance deadline. */
public final class MarkdownFixture {
    public static final String LANGUAGE = "jasper-measured-java";
    private static final String STYLE = "text/" + LANGUAGE;

    private MarkdownFixture() { }

    /** Lexical-entry/exit measurements; the optional gate is inside the real lexer's first addToken call. */
    public static final class Probe {
        public final AtomicInteger passes = new AtomicInteger();
        public final List<Integer> initialStates = new ArrayList<>();
        public final CountDownLatch firstToken = new CountDownLatch(1);
        public final CountDownLatch release = new CountDownLatch(1);
        public final AtomicBoolean sawToken = new AtomicBoolean();
        public volatile long lexerReturned;
        public volatile long lexicalNanos;
        public volatile long barrierNanos;
        private final boolean gate;

        public Probe(boolean gate) { this.gate = gate; }
    }

    /** Installs one test-only language for this scope; tests restore the prior factory even on failure. */
    public static AutoCloseable lexer(Probe probe) {
        Languages.register();
        var previous = TokenMakerFactory.getDefaultInstance();
        TokenMakerFactory.setDefaultInstance(new TokenMakerFactory() {
            @Override protected TokenMaker getTokenMakerImpl(String key) { return new MeasuredJavaLexer(probe); }
            @Override public Set<String> keySet() { return Set.of(STYLE); }
        });
        return () -> TokenMakerFactory.setDefaultInstance(previous);
    }

    private static final class MeasuredJavaLexer extends JavaTokenMaker {
        private final Probe probe;
        MeasuredJavaLexer(Probe probe) { this.probe = probe; }

        @Override public Token getTokenList(Segment text, int initialTokenType, int startOffset) {
            probe.passes.incrementAndGet();
            probe.initialStates.add(initialTokenType);
            long started = System.nanoTime();
            try { return super.getTokenList(text, initialTokenType, startOffset); }
            finally {
                long finished = System.nanoTime();
                probe.lexerReturned = finished;
                probe.lexicalNanos += finished - started;
            }
        }

        @Override public void addToken(char[] array, int start, int end, int type, int offset, boolean hyperlink) {
            super.addToken(array, start, end, type, offset, hyperlink);
            if (probe.sawToken.compareAndSet(false, true)) {
                long started = System.nanoTime();
                probe.firstToken.countDown();
                if (probe.gate) {
                    boolean interrupted = false;
                    try {
                        while (true) {
                            try { probe.release.await(); break; }
                            catch (InterruptedException ignored) { interrupted = true; }
                        }
                    } finally {
                        probe.barrierNanos = System.nanoTime() - started;
                        if (interrupted) Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }
    /** Independently released worker/UI work, including deterministic rejection. */
    public static final class ManualExecutor implements java.util.concurrent.Executor {
        private final java.util.ArrayDeque<Runnable> tasks = new java.util.ArrayDeque<>();
        public boolean reject;
        @Override public void execute(Runnable task) { if (reject) throw new java.util.concurrent.RejectedExecutionException("executor unavailable"); tasks.add(task); }
        public int size() { return tasks.size(); }
        public void runNext() { tasks.remove().run(); }
        public void drain() { while (!tasks.isEmpty()) runNext(); }
    }
}
