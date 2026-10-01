package dev.jasper.fileserver.engine.http;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RangeTest {
    @Test void parsesOneRangeAndIgnoresTheRest() throws Exception {
        assertThat(Range.parse(null, 10)).isEmpty();
        assertThat(Range.parse("bytes=2-4", 10)).contains(new Range(2, 3));
        assertThat(Range.parse("bytes=7-", 10)).contains(new Range(7, 3));
        assertThat(Range.parse("bytes=-3", 10)).contains(new Range(7, 3));
        assertThat(Range.parse("bytes=-30", 10)).contains(new Range(0, 10));
        assertThat(Range.parse("bytes=5-99", 10)).contains(new Range(5, 5));
        assertThat(Range.parse("bytes=0-1,4-5", 10)).as("several ranges: the whole file").isEqualTo(Optional.empty());
        assertThat(Range.parse("bytes=4-2", 10)).isEmpty();
        assertThat(Range.parse("items=0-1", 10)).isEmpty();
        assertThat(Range.parse("bytes=x-1", 10)).isEmpty();
        assertThatThrownBy(() -> Range.parse("bytes=10-", 10)).isInstanceOf(Range.Unsatisfiable.class);
        assertThatThrownBy(() -> Range.parse("bytes=-1", 0)).isInstanceOf(Range.Unsatisfiable.class);
    }
}
