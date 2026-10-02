package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class ToolWindowHeaderTest {
    private final List<String> events = new ArrayList<>();

    private JPopupMenu menu(Slot current, boolean visible) {
        return ToolWindowMenu.create(current, visible, slot -> events.add("move:" + slot), () -> events.add("hide"));
    }

    static JMenuItem item(JPopupMenu menu, String text) {
        for (var child : menu.getComponents()) {
            if (child instanceof JMenu submenu) for (var nested : submenu.getMenuComponents())
                if (nested instanceof JMenuItem entry && entry.getText().equals(text)) return entry;
            if (child instanceof JMenuItem entry && entry.getText().equals(text)) return entry;
        }
        throw new AssertionError("No menu item " + text);
    }

    @Test void theMenuMovesToAnyOfTheSixSlotsAndHides() {
        JPopupMenu menu = menu(Slot.LEFT_BOTTOM, true);
        var moveTo = (JMenu) menu.getComponent(0);
        assertThat(moveTo.getText()).isEqualTo("Move to");
        assertThat(Arrays.stream(moveTo.getMenuComponents()).map(item -> ((JMenuItem) item).getText()))
            .containsExactly("Left Top", "Left Bottom", "Bottom Left", "Right Top", "Right Bottom", "Bottom Right");
        assertThat(Arrays.stream(moveTo.getMenuComponents()).filter(item -> ((JCheckBoxMenuItem) item).isSelected())
            .map(item -> ((JMenuItem) item).getText())).as("the current slot is checked").containsExactly("Left Bottom");
        item(menu, "Bottom Right").doClick();
        item(menu, "Left Bottom").doClick();
        item(menu, "Hide").doClick();
        assertThat(events).as("choosing the current slot does nothing").containsExactly("move:BOTTOM_RIGHT", "hide");
        assertThat(item(menu(Slot.RIGHT_TOP, false), "Hide").isEnabled()).as("nothing to hide").isFalse();
    }

    @Test void theHeaderShowsThePlainTitleAndHidesOrOpensItsMenu() {
        var header = new ToolWindowHeader("<html><b>Sessions</b>", () -> menu(Slot.LEFT_TOP, true), () -> events.add("hidden"));
        assertThat(header.titleLabel().getText()).isEqualTo("<html><b>Sessions</b>");
        assertThat(header.titleLabel().getClientProperty("html.disable")).as("a plugin title is never HTML").isEqualTo(Boolean.TRUE);
        assertThat(header.titleLabel().getFont().getStyle() & Font.BOLD).isEqualTo(Font.BOLD);
        header.updateUI();
        SwingUtilities.updateComponentTreeUI(header);
        assertThat(header.titleLabel().getFont().isBold()).as("bold after a look-and-feel change").isTrue();
        assertThat(header.optionsButton().getAccessibleContext().getAccessibleName()).isEqualTo("Options");
        assertThat(header.optionsButton().getToolTipText()).isEqualTo("Options");
        assertThat(header.hideButton().getAccessibleContext().getAccessibleName()).isEqualTo("Hide");
        assertThat(header.optionsButton().isFocusable()).isFalse();
        assertThat(header.getPreferredSize().height).isEqualTo(UIScale.scale(ToolWindowHeader.HEIGHT));

        header.hideButton().doClick();
        header.optionsButton().doClick();
        assertThat(events).as("a header that is not showing opens no popup").containsExactly("hidden");
        assertThat(header.menu().getComponentCount()).isEqualTo(2);
    }

    @Test void theButtonsSitAtTheRightAndAreCenteredVertically() {
        var header = new ToolWindowHeader("Sessions", () -> menu(Slot.LEFT_TOP, true), () -> { });
        header.setSize(260, header.getPreferredSize().height);
        LayoutTestSupport.layoutTree(header);
        var hide = SwingUtilities.convertRectangle(header.hideButton().getParent(), header.hideButton().getBounds(), header);
        var options = SwingUtilities.convertRectangle(header.optionsButton().getParent(), header.optionsButton().getBounds(), header);
        assertThat(hide.x + hide.width).isEqualTo(260 - UIScale.scale(6));
        assertThat(options.x + options.width).isLessThan(hide.x);
        assertThat(Math.abs(hide.y - (header.getHeight() - hide.y - hide.height))).isLessThanOrEqualTo(1);
        var title = SwingUtilities.convertRectangle(header.titleLabel().getParent(), header.titleLabel().getBounds(), header);
        assertThat(title.x).isEqualTo(UIScale.scale(10));
    }
}
