package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.StatusEntry;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class WindowStatusBarContributionsTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void itemsRenderInPriorityOrderOnTheirSideAndClickTheirAction(boolean retro) throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
        new dev.jasper.app.appearance.ThemeController(retro ? dev.jasper.app.config.ThemeStyle.RETRO : dev.jasper.app.config.ThemeStyle.MODERN, dev.jasper.app.config.Appearance.LIGHT);
        var model = new Contributions();
        List<String> clicks = new ArrayList<>();
        Action run = new AbstractAction("Run") {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { clicks.add("run"); }
        };
        StatusEntry lock = model.addStatus("dev.x.lock", false, 20);
        StatusEntry sync = model.addStatus("dev.x.sync", false, 10);
        StatusEntry host = model.addStatus("dev.x.host", true, 0);
        StatusEntry hidden = model.addStatus("dev.x.hidden", true, 5);
        lock.setText("Locked"); lock.setTooltip("Vault is locked"); lock.setActionId("dev.x.run");
        sync.setText("Synced");
        host.setText("example.org");
        hidden.setVisible(false);

        var bar = new WindowStatusBar();
        bar.setMetadata("zsh", "/Users/someone/a/very/long/path/that/keeps/going/and/going", "120x40", true, true);
        bar.setContributed(model.status(), Map.of("dev.x.run", run)::get);
        bar.setSize(900, 30);
        bar.doLayout();

        assertThat(bar.contributedItems(false)).extracting(JButton::getText).containsExactly("Synced", "Locked");
        assertThat(bar.contributedItems(true)).extracting(JButton::getText).containsExactly("example.org");
        JButton locked = bar.contributedItems(false).get(1);
        assertThat(locked.getToolTipText()).isEqualTo("Vault is locked");
        locked.doClick();
        bar.contributedItems(false).get(0).doClick();
        assertThat(clicks).containsExactly("run");

        run.setEnabled(false);
        bar.setContributed(model.status(), Map.of("dev.x.run", run)::get);
        assertThat(bar.contributedItems(false).get(1).isEnabled()).isFalse();
        bar.setContributed(List.of(), id -> null);
        assertThat(bar.contributedItems(false)).isEmpty();
        assertThat(bar.contributedItems(true)).isEmpty();
        new dev.jasper.app.appearance.ThemeController();
        });
    }

    @Test void theConfigurationSegmentKeepsItsFullWidthWhenSpaceIsTight() {
        var model = new Contributions();
        for (int i = 0; i < 6; i++) model.addStatus("dev.x.item" + i, i % 2 == 0, i).setText("A fairly long status item " + i);
        var bar = new WindowStatusBar();
        bar.setMetadata("zsh", "/a/long/directory/name/that/would/like/more/room", "120x40", false, false);
        bar.setContributed(model.status(), id -> null);
        bar.setSize(420, 30);
        bar.doLayout();
        for (int width : new int[]{958, 420, 320, 100, 20, 0}) {
            bar.setSize(width, 30);
            bar.doLayout();
            for (java.awt.Component child : bar.getComponents())
                assertThat(child.getX() + child.getWidth()).as("width %d: %s", width, child.getClass().getSimpleName())
                    .isBetween(0, Math.max(0, width));
        }
        bar.setSize(420, 30);
        bar.doLayout();
        JButton config = bar.configButton();
        assertThat(config.getParent().getWidth()).isEqualTo(config.getParent().getPreferredSize().width);
        assertThat(config.getParent().getX() + config.getParent().getWidth()).isLessThanOrEqualTo(bar.getWidth());
    }
}
