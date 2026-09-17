package dev.jasper.app;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NativeNotifierTest {
    /**
     * The whole point of the argv form: a command containing quotes and a newline must reach osascript
     * as data. If this ever became an interpolated script, these characters would end it early.
     */
    @Test void theCommandTextTravelsAsArgumentsNotAsScript() {
        String nasty = "echo \"hi\"; say 'boo'\nrm -rf /";

        List<String> arguments = NativeNotifier.command(nasty, "Finished in 1m");

        assertThat(arguments).endsWith("Finished in 1m", nasty);
        assertThat(arguments).containsSequence("-e", "on run argv");
        assertThat(arguments.subList(0, arguments.size() - 2))
            .as("no element interpolates the text").noneMatch(part -> part.contains("echo"));
    }

    @Test void anUnsupportedPlatformSendsNothing() {
        try (var notifier = new NativeNotifier(false)) {
            notifier.send("build", "Finished in 1m");
        }
    }
}
