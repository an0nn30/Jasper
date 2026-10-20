package dev.jasper.app.workspace;

import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.plugins.WorkspacePanelsFixture;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JRootPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan 3 Task 10: the integrated reference arrangement (Sessions over SFTP left, File Servers over Tunnels right) with
 * the staged plugins in one actual window. Each docked header shows its plugin's title once beside the host's Options
 * and Hide actions; Hide collapses only that panel and its rail button follows; the chrome keeps its receivers.
 */
class IntegratedWorkspaceTest {
    @TempDir Path home;

    @Test @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void dockedPluginHeadersAreSingleWithHostActionsAndHideCollapsesOnlyThatPanel() throws Exception {
        WindowContent[] owner = new WindowContent[1];
        JRootPane root = new JRootPane();
        edt(() -> owner[0] = content(launcher(new ArrayDeque<>()), true));
        var theme = new dev.jasper.app.appearance.ResolvedTheme[1];
        edt(() -> theme[0] = owner[0].theme());
        try (var panels = new WorkspacePanelsFixture(Files.createDirectories(home.resolve("plugins")), theme[0])) {
            edt(() -> {
                WindowContent.installTitleBar(root, owner[0], true, title -> { });
                owner[0].connectTerminals(panels.registry(), () -> { });
                owner[0].connectContributions(panels.contributions());
            });
            WorkspacePanelsFixture.settle();
            String[][] slots = {{WorkspacePanelsFixture.SESSIONS, "LEFT_TOP"}, {WorkspacePanelsFixture.SFTP, "LEFT_BOTTOM"},
                {WorkspacePanelsFixture.FILE_SERVERS, "RIGHT_TOP"}, {WorkspacePanelsFixture.TUNNELS, "RIGHT_BOTTOM"}};
            edt(() -> {
                var dock = owner[0].dock();
                for (String[] entry : slots) { dock.move(entry[0], Slot.valueOf(entry[1]), Integer.MAX_VALUE); dock.show(entry[0]); }
                dock.resize(Side.LEFT, 260); dock.resize(Side.RIGHT, 260);
                owner[0].area().sync();
            });
            WorkspacePanelsFixture.settle();
            edt(() -> {
                root.setSize(1342, 858);
                for (int i = 0; i < 4; i++) dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(root);
                var labels = labels(owner[0].area());
                for (String title : List.of("Sessions", "SFTP", "File Servers", "Tunnels"))
                    assertThat(labels.stream().filter(title::equals).count()).as("one %s title row", title).isEqualTo(1);
                var buttons = buttons(owner[0].area());
                assertThat(buttons.stream().filter("Options"::equals).count()).as("host Options per docked header").isEqualTo(4);
                assertThat(buttons.stream().filter("Hide"::equals).count()).as("host Hide per docked header").isEqualTo(4);
                assertThat(owner[0].leftStripe().buttons()).extracting(AbstractButton::isSelected).containsExactly(true, true);
                // The rail and status bar are visible chrome receivers around the docked panels.
                assertThat(owner[0].leftStripe().isVisible()).isTrue();
                assertThat(owner[0].status().getClientProperty("Jasper.chromeArtwork")).isEqualTo(dev.jasper.ui.icons.ChromeArtwork.NEW_UI);
            });
            edt(() -> {
                var hide = hostButton(owner[0].area(), "Hide", "SFTP");
                hide.doClick(0);
            });
            WorkspacePanelsFixture.settle();
            edt(() -> {
                assertThat(owner[0].dock().visible(WorkspacePanelsFixture.SFTP)).as("Hide collapses SFTP").isFalse();
                assertThat(owner[0].dock().visible(WorkspacePanelsFixture.SESSIONS)).as("its neighbour stays").isTrue();
                assertThat(owner[0].dock().visible(WorkspacePanelsFixture.TUNNELS)).isTrue();
                assertThat(owner[0].leftStripe().buttons()).extracting(AbstractButton::isSelected).containsExactly(true, false);
            });
        } finally {
            edt(() -> owner[0].close());
            closeOwners();
        }
    }

    /** The host action named {@code name} inside the docked frame whose header shows {@code title}. */
    private static AbstractButton hostButton(Container area, String name, String title) {
        for (Component child : area.getComponents()) {
            if (!(child instanceof Container frame)) continue;
            if (labels(frame).contains(title) && labels(frame).stream().noneMatch(l -> !l.equals(title) && List.of("Sessions", "SFTP", "File Servers", "Tunnels").contains(l)))
                for (var button : buttonComponents(frame)) if (name.equals(button.getAccessibleContext().getAccessibleName())) return button;
        }
        throw new AssertionError("No " + name + " for " + title);
    }

    private static List<String> labels(Container root) {
        var found = new ArrayList<String>();
        for (Component child : root.getComponents()) {
            if (!child.isVisible()) continue;
            if (child instanceof JLabel label && label.getText() != null) found.add(label.getText());
            if (child instanceof Container nested) found.addAll(labels(nested));
        }
        return found;
    }

    private static List<String> buttons(Container root) {
        return buttonComponents(root).stream().map(button -> button.getAccessibleContext().getAccessibleName()).toList();
    }

    private static List<AbstractButton> buttonComponents(Container root) {
        var found = new ArrayList<AbstractButton>();
        for (Component child : root.getComponents()) {
            if (!child.isVisible()) continue;
            if (child instanceof AbstractButton button) found.add(button);
            if (child instanceof Container nested) found.addAll(buttonComponents(nested));
        }
        return found;
    }
}
