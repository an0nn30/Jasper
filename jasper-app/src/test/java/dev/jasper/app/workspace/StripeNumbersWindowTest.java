package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Spec 10.7: a window's labelled stripes number their labels from the window's live keymap. */
@ExtendWith(EdtTestExtension.class)
class StripeNumbersWindowTest {
    private static List<String> tips(DockStripe stripe) { return stripe.buttons().stream().map(AbstractButton::getToolTipText).toList(); }

    private static WindowContent window(Contributions model) {
        var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), ThemeTestSupport.controller(false),
            DockChromeTest.classicDock());
        owner.connectContributions(model, UiState.inMemory());
        model.addAction("test.sftp.toggle", "Toggle SFTP", null, List.of("sftp"), Optional.of("alt+2"), invocation -> { });
        model.addPanel("test.sftp", "SFTP", new ImageIcon(), Slot.LEFT_TOP, Optional.of("test.sftp.toggle"), site -> new JLabel("sftp"));
        model.addPanel("test.servers", "File Servers", new ImageIcon(), Slot.LEFT_BOTTOM, site -> new JLabel("servers"));
        return owner;
    }

    @Test void aPanelsLabelStartsWithTheDigitOfItsToggle() {
        var owner = window(new Contributions());
        try {
            assertThat(tips(owner.leftStripe())).containsExactly("2: SFTP", "File Servers");
            assertThat(owner.dockDrag().labels.apply("test.sftp")).isEqualTo("2: SFTP");
        } finally { owner.close(); }
    }

    /** Review Focus 1. */
    @Test void aRebindRenumbersTheStripeAtOnce() {
        var owner = window(new Contributions());
        try {
            owner.setBindings(KeyBindings.withOverrides(false, Map.of("test.sftp.toggle", "alt+7")));
            assertThat(tips(owner.leftStripe())).containsExactly("7: SFTP", "File Servers");
            assertThat(owner.dockDrag().active()).isFalse();
            owner.setBindings(KeyBindings.withOverrides(false, Map.of("test.sftp.toggle", "alt+s")));
            assertThat(tips(owner.leftStripe())).containsExactly("SFTP", "File Servers");
            assertThat(owner.dockDrag().labels.apply("test.sftp")).isEqualTo("SFTP");
        } finally { owner.close(); }
    }

    /** The default look: a rebind leaves the icon rails alone (no re-render, so a running drag is not cancelled). */
    @Test void aRebindUnderTheIconRailsLeavesTheRailsAlone() {
        var model = new Contributions();
        var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), ThemeTestSupport.controller(false),
            WorkspaceChrome.STANDARD);
        try {
            owner.connectContributions(model, UiState.inMemory());
            model.addAction("test.sftp.toggle", "Toggle SFTP", null, List.of("sftp"), Optional.of("alt+2"), invocation -> { });
            model.addPanel("test.sftp", "SFTP", new ImageIcon(), Slot.LEFT_TOP, Optional.of("test.sftp.toggle"), site -> new JLabel("sftp"));
            var button = owner.leftStripe().buttons().getFirst();
            owner.setBindings(KeyBindings.withOverrides(false, Map.of("test.sftp.toggle", "alt+7")));
            assertThat(owner.leftStripe().buttons().getFirst()).as("the rail was not rebuilt").isSameAs(button);
            assertThat(owner.dockDrag().labels.apply("test.sftp")).isEqualTo("SFTP");
        } finally { owner.close(); }
    }
}
