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
        bar.finish(); bar.setTitleBar(true); bar.setSize(100, 42); bar.doLayout();
        var menu = (JMenu) bar.overflowMenu().getComponent(0);
        assertThat(menu.getText()).isEqualTo("Sessions");
        menu.getItem(0).doClick(); assertThat(calls).hasValue(1);
        bar.remove(button); bar.doLayout();
        assertThat(bar.overflowMenu().getComponentCount()).isZero();
    }

    @Test void resizingNeverOverlapsOrShrinksVisibleButtonsAndKeepsDraggingSpace() {
        var bar = new WindowToolbar(() -> {});
        var calls = new AtomicInteger();
        for (int i = 0; i < 12; i++) button(bar, action("Plugin " + i, calls), i);
        bar.add(Box.createHorizontalGlue());
        button(bar, action("Settings", calls), 100);
        bar.finish(); bar.setTitleBar(true);
        for (int width : new int[] {1000, 450, 200, 120, 450, 1000}) {
            bar.setSize(width, 42); bar.doLayout();
            int end = 0;
            for (var child : bar.getComponents()) {
                if (!(child instanceof JButton) || !child.isVisible()) continue;
                assertThat(child.getX()).isGreaterThanOrEqualTo(end);
                assertThat(child.getWidth()).isGreaterThanOrEqualTo(24);
                end = child.getX() + child.getWidth();
                assertThat(end).isLessThanOrEqualTo(width);
            }
            var gap = java.util.Arrays.stream(bar.getComponents()).filter(Box.Filler.class::isInstance).findFirst().orElseThrow();
            assertThat(gap.getWidth()).isGreaterThanOrEqualTo(64);
        }
    }
}
