package dev.jasper.vault.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SecretClipboardTest {
    String contents = "";
    final List<Runnable> armed = new ArrayList<>();
    final SecretClipboard clipboard = new SecretClipboard(text -> contents = text, () -> Optional.of(contents), armed::add);

    @Test void anOlderTimerCannotClearANewerCopyAndCloseClearsTheCurrentCopy() {
        clipboard.copySecret("first");
        clipboard.copySecret("second");
        armed.getFirst().run();
        assertThat(contents).isEqualTo("second");
        clipboard.close();
        assertThat(contents).isEmpty();
    }

    @Test void aSecretIsClearedLaterUnlessTheClipboardChanged() {
        clipboard.copySecret("s3cret");
        assertThat(contents).isEqualTo("s3cret");
        assertThat(armed).hasSize(1);
        armed.getFirst().run();
        assertThat(contents).as("unchanged: cleared").isEmpty();
        clipboard.copySecret("again");
        contents = "something the user copied";
        armed.get(1).run();
        assertThat(contents).as("changed meanwhile: left alone").isEqualTo("something the user copied");
    }

    @Test void aPlainCopyIsNeverCleared() {
        clipboard.copy("deploy");
        assertThat(armed).isEmpty();
        clipboard.clearIfUnchanged();
        assertThat(contents).isEqualTo("deploy");
    }
}
