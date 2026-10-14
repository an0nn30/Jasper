package dev.jasper.terminal.view;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class FileLinkTest {
    @Test
    void aFileLinkNeedsAPathAndNoNegativeNumbers() {
        assertThat(new FileLink("~/a.txt", 0, 0)).isEqualTo(new FileLink("~/a.txt", 0, 0));
        assertThatNullPointerException().isThrownBy(() -> new FileLink(null, 1, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new FileLink(" ", 1, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new FileLink("a.txt", -1, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new FileLink("a.txt", 1, -1));
    }
}
