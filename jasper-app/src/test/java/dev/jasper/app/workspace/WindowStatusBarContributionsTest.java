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
    @Test void itemsRenderInPriorityOrderOnTheirSideAndClickTheirAction() {
        new dev.jasper.app.appearance.ThemeController(dev.jasper.app.config.ThemeStyle.MODERN, dev.jasper.app.config.Appearance.LIGHT);
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
    }

    @Test void progressUpdatesInPlaceAndInvokesCurrentActions() {
        var model = new Contributions();
        var entry = model.addStatus("dev.x.progress", false, 0);
        var clicks = new ArrayList<String>();
        Action open = new AbstractAction("Open") {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { clicks.add("open"); }
        };
        Action cancel = new AbstractAction("Cancel") {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { clicks.add("cancel"); }
        };
        var actions = Map.of("open", open, "cancel", cancel);
        var bar = new WindowStatusBar();
        entry.setProgress(new dev.jasper.app.contributions.ProgressState("Copying", "20 MiB left", "Half complete",
            java.util.OptionalDouble.of(.5), "open", "cancel"));
        bar.setContributed(model.status(), actions::get);
        var view = descendants(bar).filter(StatusProgressView.class::isInstance).map(StatusProgressView.class::cast).findFirst().orElseThrow();
        var progress = descendants(view).filter(javax.swing.JProgressBar.class::isInstance).map(javax.swing.JProgressBar.class::cast).findFirst().orElseThrow();
        assertThat(progress.getValue()).isEqualTo(500);
        assertThat(progress.getAccessibleContext().getAccessibleDescription()).isEqualTo("Half complete");
        entry.setProgress(new dev.jasper.app.contributions.ProgressState("Scanning", "", "Scanning files",
            java.util.OptionalDouble.empty(), "open", "cancel"));
        bar.setContributed(model.status(), actions::get);
        assertThat(descendants(bar).filter(StatusProgressView.class::isInstance).findFirst()).containsSame(view);
        assertThat(progress.isIndeterminate()).isTrue();
        descendants(view).filter(JButton.class::isInstance).map(JButton.class::cast).forEach(JButton::doClick);
        assertThat(clicks).containsExactly("open", "cancel");
        cancel.setEnabled(false);
        bar.setContributed(model.status(), actions::get);
        assertThat(descendants(view).filter(JButton.class::isInstance).map(JButton.class::cast).filter(b -> "Cancel".equals(b.getText())).findFirst().orElseThrow().isEnabled()).isFalse();
    }

    @Test void progressFitsNarrowBarsAndLargerUiFontsInBothSkins() {
        try {
            new dev.jasper.app.appearance.ThemeController(dev.jasper.app.config.ThemeStyle.MODERN,
                dev.jasper.app.config.Appearance.LIGHT);
            for (int size : new int[]{12, 18, 32}) {
                javax.swing.UIManager.put("Label.font", new java.awt.Font("Dialog", java.awt.Font.PLAIN, size));
                var view = new StatusProgressView();
                Action cancel = new AbstractAction("Cancel") { public void actionPerformed(java.awt.event.ActionEvent e) {} };
                view.update(new dev.jasper.app.contributions.ProgressState("Copying", "1 GiB left", "Copying one file",
                    java.util.OptionalDouble.of(.25), "open", "cancel"), id -> cancel);
                for (int width : new int[]{0, 20, 100, 320, 500}) {
                    view.setSize(width, view.getPreferredSize().height); view.doLayout();
                    for (var child : view.getComponents()) {
                        assertThat(child.getX()).isGreaterThanOrEqualTo(0);
                        assertThat(child.getX() + child.getWidth()).isLessThanOrEqualTo(width);
                        assertThat(child.getY() + child.getHeight()).isLessThanOrEqualTo(view.getHeight());
                    }
                    var image = new java.awt.image.BufferedImage(Math.max(1, width), view.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    var g = image.createGraphics(); try { view.paint(g); } finally { g.dispose(); }
                }
            }
        } finally { new dev.jasper.app.appearance.ThemeController(); }
    }

    private static java.util.stream.Stream<java.awt.Component> descendants(java.awt.Container parent) {
        return java.util.Arrays.stream(parent.getComponents()).flatMap(c -> c instanceof java.awt.Container nested
            ? java.util.stream.Stream.concat(java.util.stream.Stream.of(c), descendants(nested)) : java.util.stream.Stream.of(c));
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
