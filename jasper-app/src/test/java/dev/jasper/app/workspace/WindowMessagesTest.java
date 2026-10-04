package dev.jasper.app.workspace;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.util.ArrayDeque;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static dev.jasper.app.workspace.DesktopTestSupport.content;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(EdtTestExtension.class)
class WindowMessagesTest {
    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    /** Headless, a message dialog would throw; a window without a receiver only logs. */
    @Test void aWindowWithNoReceiverNeverOpensADialog() {
        var owner = content(launcher(new ArrayDeque<>()));
        assertThatCode(() -> {
            owner.onError.accept("Could not start the terminal", "details");
            owner.onInfo.accept("Migrate Config", "done");
        }).doesNotThrowAnyException();
    }

    @Test void configurationDetailsOpenFromOutsideTheStatusBar() {
        var owner = content(launcher(new ArrayDeque<>()));
        boolean[] shown = {false};
        owner.status().onConfigurationDetails = () -> shown[0] = true;
        owner.showConfigurationDetails();
        assertThat(shown[0]).isTrue();
    }
}
