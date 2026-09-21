package dev.jasper.terminal.internal.text;

import dev.jasper.terminal.internal.emulation.EmulationFixture;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.TerminalLine;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class LogicalLineTest {
    @Test
    void exactRowLimitIsCompleteWhenBothEndsAreKnown() {
        LogicalLine line = LogicalLine.around(2048, 5,
            row -> row < 0 || row >= 4096 ? null : line(row < 4095));
        assertThat(line).isEqualTo(new LogicalLine(0, 4095, 5, false));
    }

    @Test
    void exactTextBudgetIsCompleteAndUsesTwoBytesPerCell() {
        LogicalLine line = LogicalLine.around(200, 1024,
            row -> row < 0 || row >= 512 ? null : line(row < 511));
        assertThat(line).isEqualTo(new LogicalLine(0, 511, 1024, false));
        assertThat((long) line.rowCount() * line.columns() * Character.BYTES).isEqualTo(1 << 20);
    }

    @Test
    void anUnboundedWrapSourceRequiresOnlyBoundedMetadataReads() {
        AtomicInteger reads = new AtomicInteger();
        TerminalRow wrapped = line(true);
        LogicalLine line = LogicalLine.around(1_000_000, 5, row -> {
            reads.incrementAndGet();
            return wrapped;
        });
        assertThat(line.rowCount()).isEqualTo(4096);
        assertThat(line.lastRow()).isEqualTo(1_000_000);
        assertThat(line.truncated()).isTrue();
        assertThat(reads.get()).isLessThanOrEqualTo(4096 + 2);
    }

    @Test
    void aWidthTooLargeForOneRowFallsBackWithoutReadingOrMultiplyingIt() {
        for (int width : new int[]{524_289, Integer.MAX_VALUE}) {
            LogicalLine line = LogicalLine.around(42, width, row -> {
                throw new AssertionError("oversized rows must not be read");
            });
            assertThat(line).isEqualTo(new LogicalLine(42, 42, 524_288, true));
        }
    }

    @Test
    void absoluteRowArithmeticDoesNotWrapAtLongEndpoints() {
        LogicalLine first = LogicalLine.around(Long.MIN_VALUE, 5, row -> {
            assertThat(row).isEqualTo(Long.MIN_VALUE);
            return line(false);
        });
        assertThat(first.rowCount()).isOne();
        LogicalLine last = LogicalLine.around(Long.MAX_VALUE, 5, row -> {
            assertThat(row).isGreaterThanOrEqualTo(Long.MAX_VALUE - 1);
            return row == Long.MAX_VALUE ? line(true) : null;
        });
        assertThat(last.rowCount()).isOne();
        assertThat(last.truncated()).as("the known continuation cannot be traversed").isTrue();
    }

    @Test
    void aMissingWrappedContinuationMakesTheAvailableContextIncomplete() {
        LogicalLine line = LogicalLine.around(0, 20, row -> row == 0 ? line(true) : null);
        assertThat(line).isEqualTo(new LogicalLine(0, 0, 20, true));
    }

    @Test
    void missingRowsAndInvalidWidthsHaveExplicitFallbacks() {
        assertThat(LogicalLine.around(42, 5, row -> null)).isEqualTo(new LogicalLine(42, 42, 5, true));
        for (int width : new int[]{0, -1, Integer.MIN_VALUE}) {
            assertThatIllegalArgumentException().isThrownBy(() -> LogicalLine.around(42, width, row -> null));
        }
    }

    private static TerminalRow line(boolean wrapped) {
        TerminalLine line = new TerminalLine(new TerminalLine.TextEntry(TextStyle.EMPTY, new CharBuffer("")));
        line.setWrapped(wrapped);
        return EmulationFixture.capture(line, line.length());
    }
}
