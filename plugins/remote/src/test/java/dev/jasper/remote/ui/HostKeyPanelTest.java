package dev.jasper.remote.ui;

import dev.jasper.remote.client.HostKeyVerifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HostKeyPanelTest {
    @Test void showsTheKeyAndReportsTheDecision() {
        List<HostKeyVerifier.Decision> decisions = new ArrayList<>();
        var panel = new HostKeyPanel(new HostKeyVerifier.Question("api.example", 2222, "ssh-ed25519", "SHA256:abc"), decisions::add);
        assertThat(panel.text.getText()).contains("api.example", "2222", "ssh-ed25519", "SHA256:abc");
        panel.once.doClick(); panel.trust.doClick(); panel.cancel.doClick();
        assertThat(decisions).containsExactly(HostKeyVerifier.Decision.ONCE, HostKeyVerifier.Decision.TRUST, HostKeyVerifier.Decision.CANCEL);
    }
}
