package dev.jasper.editor.markdown;

import dev.jasper.editor.testsupport.MarkdownFixture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("try") // The scope exists to restore the global vendor factory.
class CodeHighlighterTest {
    @Test void oneLexicalPassPerLinePreservesContinuationAndResetsAfterTheComment() throws Exception {
        var probe = new MarkdownFixture.Probe(false);
        var out = new MarkdownLimits.Html();
        try (var ignored = MarkdownFixture.lexer(probe)) {
            var highlighter = new CodeHighlighter(MarkdownFixture.LANGUAGE, MarkdownRendererTest.palette());
            highlighter.line("/* start", out);
            highlighter.line("end */ int x = 1;", out);
            highlighter.line("int y = 2;", out);
        }
        assertThat(probe.passes.get()).isEqualTo(3);
        assertThat(probe.initialStates).containsExactly(TokenTypes.NULL, TokenTypes.COMMENT_MULTILINE, TokenTypes.NULL);
        assertThat(out.toString()).contains("color=\"#1b283c\">/* start", "color=\"#1b283c\">end */",
            "color=\"#15283c\">int");
    }

    @Test void interruptionDuringTheRealLexerCancelsBeforeMarkupEmission() throws Exception {
        var probe = new MarkdownFixture.Probe(true);
        var failure = new AtomicReference<Throwable>();
        var completed = new AtomicLong();
        String source = "```" + MarkdownFixture.LANGUAGE + "\n" + "int ".repeat(520_000) + "\n```\n";
        try (var ignored = MarkdownFixture.lexer(probe)) {
            long started = System.nanoTime();
            var worker = new Thread(() -> {
                try { MarkdownRendererTest.render(source); }
                catch (Throwable thrown) { failure.set(thrown); }
                finally {
                    completed.set(System.nanoTime());
                    // Also release a waiting test if rendering failed before reaching the measured lexer.
                    probe.firstToken.countDown();
                }
            }, "markdown-cancel-measurement");
            worker.start();
            long interrupted;
            try {
                probe.firstToken.await();
                assertThat(probe.sawToken.get()).isTrue();
                interrupted = System.nanoTime();
                worker.interrupt();
            } finally {
                probe.release.countDown();
                worker.join();
            }
            assertThat(failure.get()).isInstanceOf(CancellationException.class);
            assertThat(probe.passes.get()).isEqualTo(1);
            assertThat(probe.lexerReturned).isGreaterThanOrEqualTo(interrupted);
            assertThat(completed.get()).isGreaterThanOrEqualTo(probe.lexerReturned);
            System.out.printf(java.util.Locale.ROOT,
                "Markdown in-flight cancellation: total=%.3f ms; lexical work excluding barrier=%.3f ms; "
                    + "interrupt to lexer return=%.3f ms; lexer return to cancellation=%.3f ms; interrupt to cancellation=%.3f ms%n",
                (completed.get() - started) / 1_000_000.0,
                (probe.lexicalNanos - probe.barrierNanos) / 1_000_000.0,
                (probe.lexerReturned - interrupted) / 1_000_000.0,
                (completed.get() - probe.lexerReturned) / 1_000_000.0,
                (completed.get() - interrupted) / 1_000_000.0);
        }
    }
}
