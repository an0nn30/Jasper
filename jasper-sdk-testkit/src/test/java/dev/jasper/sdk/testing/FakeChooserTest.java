package dev.jasper.sdk.testing;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.ui.WindowSpec;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FakeChooserTest {
    @Test void scriptsMultipleFilesAndDirectoryAndDiscardsClosedOwnerResults() {
        try (var host = new FakePluginHost()) {
            var context = host.start(new PluginInfo("dev.x.chooser", "Chooser", "1", Set.of()), Set.of(), Set.of(), c -> {});
            var owner = context.windows().create(new WindowSpec("dev.x.chooser.window", "Upload", new Dimension(500,400), true));
            owner.show();
            host.queuePathSelection(List.of(Path.of("/a"), Path.of("/b")));
            assertThat(context.windows().chooseFiles(owner, "Files", Optional.empty())).containsExactly(Path.of("/a"),Path.of("/b"));
            host.queuePathSelection(List.of(Path.of("/dest")));
            assertThat(context.windows().chooseDirectory(owner, "Directory", Optional.empty())).contains(Path.of("/dest"));
            assertThat(context.windows().chooseFiles(owner, "Cancel", Optional.empty())).isEmpty();
            host.queuePathSelection(List.of(Path.of("/a")));
            host.duringPathSelection(owner::close);
            assertThat(context.windows().chooseFiles(owner, "Closed", Optional.empty())).isEmpty();
            assertThatIllegalArgumentException().isThrownBy(() -> context.windows().chooseFiles(owner, "Stale", Optional.empty()));
        }
    }
}
