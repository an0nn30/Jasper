package dev.jasper.buddy.documentation;

import dev.jasper.buddy.config.BuddyOptions;
import dev.jasper.buddy.notice.*;
import dev.jasper.buddy.view.BuddyCompanion;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class BuddyExamplesTest {
    @Test void embeddedLifecycle() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            // example:buddy:start
            var options = BuddyOptions.builder(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 13))
                .dark(true).activateHost(() -> {}).toggleRequested(() -> {}).build();
            try (var buddy = new BuddyCompanion(options)) {
                var id = new BuddyNoticeId("example", new Object());
                buddy.post(new BuddyNotice(id, BuddyNotice.Kind.TASK, "Build", BuddyNotice.State.DONE,
                    () -> "Finished", () -> {}));
                buddy.acknowledge(id);
                buddy.hide();
                buddy.applyOptions(options.toBuilder().dark(false).build());
            }
            // example:buddy:end
        });
    }
}
