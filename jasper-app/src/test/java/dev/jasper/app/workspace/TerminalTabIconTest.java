package dev.jasper.app.workspace;

import dev.jasper.app.terminals.SessionRequest;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class TerminalTabIconTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void localTabsShowTheTerminalIconAndProvidedSessionsShowTheirOwn() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            Icon local = owner.currentTab().icon();
            assertThat(local).isNotNull();
            assertThat(local.getIconWidth()).isEqualTo(16);
            assertThat(owner.currentTab().icon()).as("cached per tab").isSameAs(local);
            var provided = new ImageIcon(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB));
            var remote = owner.openTab(HOME, new SessionRequest("dev.x", "remote", false, attempt -> {}, Runnable::run, provided));
            assertThat(remote.icon()).isSameAs(provided);
            var plain = owner.openTab(HOME, new SessionRequest("dev.x", "plain", false, attempt -> {}, Runnable::run));
            assertThat(plain.icon()).isNotSameAs(provided);
            assertThat(plain.icon().getIconWidth()).isEqualTo(16);
        });
    }
}
