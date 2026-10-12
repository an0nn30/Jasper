package dev.jasper.app.workspace;

import dev.jasper.app.platform.ThemeZone;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.ui.metrics.Typography;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JRootPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Plan 3 Task 10: the workspace chrome binds its text and artwork to named kit roles (Desktop workspace references). */
@ExtendWith(EdtTestExtension.class)
class WorkspaceChromeRolesTest {
    @AfterEach void close() throws Exception { closeOwners(); }

    @Test void titleBarToolbarAndStatusUseTheirChromeTypographyRoles() {
        var owner = content(launcher(new ArrayDeque<>()), true);
        var root = new JRootPane();
        try (var bar = WindowContent.installTitleBar(root, owner, true, title -> { })) {
            owner.setConfigurationState(new dev.jasper.app.config.ConfigService.State(dev.jasper.app.config.ConfigSnapshot.defaults(),
                List.of(), java.nio.file.Path.of("config.toml"), true));
            setStatusMetadata(owner.status(), "zsh", HOME.resolve("Desktop").toString(), "93 × 50", true, true);
            var split = buttons(owner.toolbar()).stream().filter(button -> "Split".equals(button.getText())).findFirst().orElseThrow();
            assertThat(split.getFont()).isEqualTo(ThemeZone.font(Typography.Role.TOOLBAR_LABEL));
            assertThat(owner.status().configButton().getFont()).isEqualTo(ThemeZone.font(Typography.Role.STATUS_TEXT));
            var labels = labels(owner.status());
            assertThat(labels).filteredOn(label -> "93 × 50".equals(label.getText()))
                .singleElement().satisfies(label -> assertThat(label.getFont()).isEqualTo(ThemeZone.font(Typography.Role.STATUS_TEXT)));
            assertThat(labels).filteredOn(label -> "Desktop".equals(label.getText()))
                .singleElement().satisfies(label -> assertThat(label.getFont()).isEqualTo(ThemeZone.font(Typography.Role.STATUS_PATH)));
            // The title bar, status bar and stripes are the native chrome's receiving contexts for original artwork.
            for (JComponent receiver : List.<JComponent>of(owner.chrome().header(), owner.status(), owner.leftStripe(), owner.rightStripe()))
                assertThat(receiver.getClientProperty("Jasper.chromeArtwork")).as(receiver.getAccessibleContext().getAccessibleName()).isEqualTo(Boolean.TRUE);
            assertThat(owner.windowTabs().getClientProperty("Jasper.chromeArtwork")).isNull();
        }
    }

    private static List<AbstractButton> buttons(Container root) {
        var found = new ArrayList<AbstractButton>();
        for (Component child : root.getComponents()) {
            if (child instanceof AbstractButton button) found.add(button);
            if (child instanceof Container nested) found.addAll(buttons(nested));
        }
        return found;
    }

    private static List<JLabel> labels(Container root) {
        var found = new ArrayList<JLabel>();
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label) found.add(label);
            if (child instanceof Container nested) found.addAll(labels(nested));
        }
        return found;
    }
}
