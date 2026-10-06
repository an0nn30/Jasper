package dev.jasper.remote.ui;

import dev.jasper.remote.client.HostKeyVerifier;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.*;

class HostKeyPanelTest {
    @Test void showsTheKeyAndReportsTheDecision() throws Exception {
        edt(() -> {
            List<HostKeyVerifier.Decision> decisions = new ArrayList<>();
            var panel = new HostKeyPanel(new FakeKit("dev.jasper.remote"), new HostKeyVerifier.Question("api.example", 2222, "ssh-ed25519", "SHA256:abc"), decisions::add);
            assertThat(String.join("\n", texts(panel.view()))).contains("api.example", "2222", "ssh-ed25519", "SHA256:abc");
            assertThat(node(panel.view(), HostKeyPanel.FINGERPRINT, Nodes.Text.class).role()).as("the fingerprint reads as code").isEqualTo(Nodes.TextRole.MONO);
            click(panel.view(), HostKeyPanel.ONCE); click(panel.view(), HostKeyPanel.TRUST); click(panel.view(), HostKeyPanel.CANCEL);
            assertThat(decisions).containsExactly(HostKeyVerifier.Decision.ONCE, HostKeyVerifier.Decision.TRUST, HostKeyVerifier.Decision.CANCEL);
        });
    }

    /** Baseline f16ae32f: no default button; Trust must never be what Enter presses. Escape is Cancel, as closing is. */
    @Test void trustIsNeverTheDefaultAndEscapeCancels() throws Exception {
        edt(() -> {
            List<HostKeyVerifier.Decision> decisions = new ArrayList<>();
            var panel = new HostKeyPanel(new FakeKit("dev.jasper.remote"), new HostKeyVerifier.Question("<b>evil</b>", 22, "ssh-rsa", "SHA256:x"), decisions::add);
            assertThat(primaries(panel.view())).as("Enter presses nothing").isEmpty();
            assertThat(form(panel.view()).actions()).extracting(Nodes.Button::label).containsExactly("Cancel", "Connect once", "Trust and connect");
            assertThat(texts(panel.view())).as("host names are literal text, never markup").anyMatch(text -> text.contains("<b>evil</b>"))
                .noneMatch(text -> text.contains("&lt;") || text.startsWith("<html>"));
            press(panel.view(), form(panel.view()).cancelAction());
            assertThat(decisions).containsExactly(HostKeyVerifier.Decision.CANCEL);
            panel.close();
            assertThat(closed(panel.view())).isTrue();
        });
    }
}
