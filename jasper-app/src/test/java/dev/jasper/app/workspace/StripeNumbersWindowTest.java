package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.LayoutTestSupport;
import com.formdev.flatlaf.util.UIScale;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
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
            owner.setBindings(KeyBindings.withOverrides(false, Map.of("test.sftp.toggle", "alt+s")));
            assertThat(tips(owner.leftStripe())).containsExactly("SFTP", "File Servers");
            assertThat(owner.dockDrag().labels.apply("test.sftp")).isEqualTo("SFTP");
        } finally { owner.close(); }
    }

    /**
     * Review Focus 1: a rebind while a label is being dragged re-renders the stripes, so it ends the drag first and leaves
     * nothing half drawn: no lifted label or gap, no overlay on the layered pane and no Esc dispatcher.
     */
    @Test void aRebindDuringADragEndsItCleanly() {
        var previous = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        var keys = new CommandPaletteShortcutsTest.RecordingFocusManager();
        KeyboardFocusManager.setCurrentKeyboardFocusManager(keys);
        var owner = window(new Contributions());
        try {
            var root = new JRootPane();
            root.setContentPane(owner);
            root.setSize(1200, 800);
            LayoutTestSupport.layoutTree(root);
            LayoutTestSupport.layoutTree(root);
            var label = owner.leftStripe().buttons().getFirst();
            assertThat(label.getToolTipText()).isEqualTo("2: SFTP");
            Rectangle bounds = SwingUtilities.convertRectangle(label.getParent(), label.getBounds(), owner);
            Point from = new Point((int) bounds.getCenterX(), (int) bounds.getCenterY());
            mouse(owner, label, MouseEvent.MOUSE_PRESSED, from);
            mouse(owner, label, MouseEvent.MOUSE_DRAGGED, new Point(from.x, from.y + UIScale.scale(owner.area().dragThreshold()) + 2));
            assertThat(owner.dockDrag().active()).as("past the threshold: dragging").isTrue();
            assertThat(owner.leftStripe().dragging()).isTrue();
            assertThat(owner.leftStripe().gapBounds()).as("a gap where the label would drop").isPresent();
            assertThat(owner.dockDrag().overlay().getParent()).isNotNull();
            assertThat(keys.dispatchers).as("Esc cancels the drag").isNotEmpty();
            owner.setBindings(KeyBindings.withOverrides(false, Map.of("test.sftp.toggle", "alt+7")));
            assertThat(owner.dockDrag().active()).isFalse();
            for (DockStripe stripe : owner.stripes().all()) {
                assertThat(stripe.dragging()).isFalse();
                assertThat(stripe.gapBounds()).isEmpty();
            }
            assertThat(owner.dockDrag().overlay().getParent()).as("the overlay is off the layered pane").isNull();
            assertThat(keys.dispatchers).as("no Esc dispatcher is left").isEmpty();
            assertThat(tips(owner.leftStripe())).containsExactly("7: SFTP", "File Servers");
        } finally {
            owner.close();
            KeyboardFocusManager.setCurrentKeyboardFocusManager(previous);
        }
    }

    private static void mouse(JComponent owner, JComponent source, int id, Point inOwner) {
        Point at = SwingUtilities.convertPoint(owner, inOwner, source);
        int modifiers = id == MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON1_DOWN_MASK;
        int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
        source.dispatchEvent(new MouseEvent(source, id, System.currentTimeMillis(), modifiers, at.x, at.y, 1, false, button));
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
