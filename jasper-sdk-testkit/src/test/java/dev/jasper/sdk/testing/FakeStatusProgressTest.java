package dev.jasper.sdk.testing;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.ui.*;
import java.util.OptionalDouble;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FakeStatusProgressTest {
    @Test void recordsAtomicProgressWithOwnedActionsAndSharedNamespace() {
        try (var host = new FakePluginHost()) {
            var context = host.start(new PluginInfo("dev.x.progress", "Progress", "1", Set.of()), Set.of(), Set.of(), c -> {});
            context.actions().register(ActionSpec.of("dev.x.progress.open", "Open"), c -> {});
            var spec = new StatusItemSpec("dev.x.progress.status", Side.RIGHT, 0);
            var handle = context.statusBar().addProgress(spec);
            var state = new StatusProgressState("Copying", "20 MiB left", "Half complete", OptionalDouble.of(.5), "dev.x.progress.open", null);
            handle.update(state);
            assertThat(host.progress()).containsEntry(spec.id(), state);
            assertThatIllegalArgumentException().isThrownBy(() -> context.statusBar().add(spec));
            assertThatIllegalArgumentException().isThrownBy(() -> handle.update(new StatusProgressState("", "", "", OptionalDouble.empty(), null, "foreign.action")));
            handle.setVisible(false);
            assertThat(host.progress()).isEmpty();
            handle.setVisible(true);
            handle.close();
            handle.update(state);
            assertThat(host.progress()).isEmpty();
        }
    }
}
