package dev.jasper.fileserver.engine;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SizesTest {
    @Test void formatsBytesAndSeconds() {
        assertThat(Sizes.human(0)).isEqualTo("0 B");
        assertThat(Sizes.human(1023)).isEqualTo("1023 B");
        assertThat(Sizes.human(1536)).isEqualTo("1.5 KB");
        assertThat(Sizes.human(10 * 1024)).isEqualTo("10 KB");
        assertThat(Sizes.human((long) (3.1 * 1024 * 1024))).isEqualTo("3.1 MB");
        assertThat(Sizes.human(5L * 1024 * 1024 * 1024)).isEqualTo("5.0 GB");
        assertThat(Sizes.seconds(Duration.ofMillis(400))).isEqualTo("0.4 s");
        assertThat(Sizes.seconds(Duration.ofSeconds(12))).isEqualTo("12.0 s");
    }
}
