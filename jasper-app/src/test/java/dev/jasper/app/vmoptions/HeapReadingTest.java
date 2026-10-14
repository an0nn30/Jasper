package dev.jasper.app.vmoptions;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HeapReadingTest {
    @Test void theLabelAndTooltipAreInMiB() {
        var reading = new HeapReading(312L << 20, 512L << 20, 2048L << 20, 98L << 20);
        assertThat(reading.label()).isEqualTo("312 of 2048M");
        assertThat(reading.tooltip()).isEqualTo(
            "Heap: 312 MiB used, 512 MiB committed, 2048 MiB maximum. Non-heap: 98 MiB used. Click to run garbage collection.");
    }

    @Test void partialMebibytesRoundDown() {
        assertThat(new HeapReading((312L << 20) + (700L << 10), 512L << 20, 2048L << 20, 0).label()).isEqualTo("312 of 2048M");
    }

    @Test void withoutAMaximumTheCommittedHeapIsTheLimit() {
        var reading = new HeapReading(10L << 20, 64L << 20, -1, 0);
        assertThat(reading.limit()).isEqualTo(64L << 20);
        assertThat(reading.label()).isEqualTo("10 of 64M");
        assertThat(reading.tooltip()).contains("64 MiB maximum");
    }

    @Test void thisJvmCanBeRead() {
        var reading = HeapReading.current();
        assertThat(reading.heapUsed()).isPositive();
        assertThat(reading.heapCommitted()).isGreaterThanOrEqualTo(reading.heapUsed());
        assertThat(reading.label()).matches("\\d+ of \\d+M");
    }
}
