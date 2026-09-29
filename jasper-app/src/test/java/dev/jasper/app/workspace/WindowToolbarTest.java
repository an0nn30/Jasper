package dev.jasper.app.workspace;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class WindowToolbarTest {
    private JButton button(WindowToolbar bar, Action action, int priority) {
        var button = new JButton(action);
        button.setPreferredSize(new Dimension(60, 24));
        WindowToolbar.entry(button, priority, () -> WindowToolbar.menuItem(action));
        bar.add(button);
        return button;
    }
    private Action action(String name, AtomicInteger calls) {
        return new AbstractAction(name) {
            @Override public void actionPerformed(ActionEvent event) { calls.incrementAndGet(); }
        };
    }

    @Test void overflowRetainsActionsSelectionShortcutsAndLiveEnabledState() {
        var calls = new AtomicInteger();
        var bar = new WindowToolbar(() -> {});
        var toggle = action("Toggle", calls);
        toggle.putValue(Action.SELECTED_KEY, true);
        toggle.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("ctrl T"));
        var secondary = button(bar, toggle, 10);
        var primary = button(bar, action("Primary", calls), 100);
        bar.finish(); bar.setSize(130, 30); bar.doLayout();
        assertThat(secondary.isVisible()).isFalse();
        assertThat(primary.isVisible()).isTrue();
        var item = (JCheckBoxMenuItem) bar.overflowMenu().getComponent(0);
        assertThat(item.isSelected()).isTrue();
        assertThat(item.getAccelerator()).isEqualTo(toggle.getValue(Action.ACCELERATOR_KEY));
        toggle.setEnabled(false);
        assertThat(item.isEnabled()).isFalse();
        toggle.setEnabled(true); item.doClick();
        assertThat(calls).hasValue(1);
        bar.setSize(500, 30); bar.doLayout();
        assertThat(secondary.isVisible()).isTrue();
        assertThat(bar.overflowMenu().getComponentCount()).isZero();
    }

    @Test void dropdownsStayGroupedAndRemovedContributionsLeaveNoOverflowEntries() {
        var bar = new WindowToolbar(() -> {});
        var calls = new AtomicInteger();
        var button = button(bar, action("Sessions", calls), 80);
        WindowToolbar.entry(button, 80, () -> {
            var menu = new JMenu("Sessions");
            menu.add(WindowToolbar.menuItem(action("SSH", calls)));
            return menu;
        });
        bar.finish(); bar.setSize(70, 42); bar.doLayout();
        var menu = (JMenu) bar.overflowMenu().getComponent(0);
        assertThat(menu.getText()).isEqualTo("Sessions");
        menu.getItem(0).doClick(); assertThat(calls).hasValue(1);
        bar.remove(button); bar.doLayout();
        assertThat(bar.overflowMenu().getComponentCount()).isZero();
    }

    @Test void controlsPackAgainstTheRightEdgeAndTheOverflowIsTheRightmost() {
        var bar = new WindowToolbar(() -> {});
        var calls = new AtomicInteger();
        for (int i = 0; i < 12; i++) button(bar, action("Plugin " + i, calls), i);
        button(bar, action("Settings", calls), 100);
        bar.finish();
        assertThat(bar.fullWidth()).isEqualTo(16 + 13 * 64 - 4);
        assertThat(bar.minimumWidth()).as("padding and the overflow button").isEqualTo(16 + 24);
        for (int width : new int[] {1000, 450, 200, 120, 450, 1000}) {
            bar.setSize(width, 30); bar.doLayout();
            int end = 0, right = 0;
            for (var child : bar.getComponents()) {
                if (!(child instanceof JButton) || !child.isVisible()) continue;
                assertThat(child.getX()).isGreaterThanOrEqualTo(end);
                assertThat(child.getWidth()).isGreaterThanOrEqualTo(24);
                end = child.getX() + child.getWidth();
                right = Math.max(right, end);
            }
            assertThat(right).as("packed right at %d", width).isEqualTo(width - 8);
            if (bar.overflowButton().isVisible())
                assertThat(bar.overflowButton().getX() + bar.overflowButton().getWidth()).isEqualTo(width - 8);
        }
        bar.setSize(bar.fullWidth(), 30); bar.doLayout();
        assertThat(bar.overflowButton().isVisible()).as("the full width shows everything").isFalse();
        assertThat(bar.overflowButton().getIcon()).isSameAs(dev.jasper.app.platform.AppIcons.bundled("general/moreVertical"));
    }

    @Test void labelsGiveWayBeforeAnyControlOverflowsAndComeBackWithRoom() {
        var bar = new WindowToolbar(() -> {});
        var calls = new AtomicInteger();
        var labelled = new JButton(action("Sessions", calls)) {
            @Override public Dimension getPreferredSize() { return new Dimension(getText() == null ? 24 : 90, 24); }
        };
        labelled.putClientProperty("label", "Sessions");
        WindowToolbar.entry(labelled, 80, () -> WindowToolbar.menuItem(labelled.getAction()));
        bar.add(labelled);
        button(bar, action("Find", calls), 100);
        bar.finish();
        bar.setSize(16 + 90 + 4 + 60, 30); bar.doLayout();
        assertThat(labelled.getText()).isEqualTo("Sessions");
        bar.setSize(16 + 24 + 4 + 60, 30); bar.doLayout();
        assertThat(labelled.getText()).as("icons before overflow").isNull();
        assertThat(bar.overflowButton().isVisible()).isFalse();
        bar.setSize(60, 30); bar.doLayout();
        assertThat(bar.overflowButton().isVisible()).isTrue();
        bar.setSize(1000, 30); bar.doLayout();
        assertThat(labelled.getText()).as("labels return with room").isEqualTo("Sessions");
        bar.setLabelsWanted(false); bar.doLayout();
        assertThat(labelled.getText()).isNull();
    }
}
