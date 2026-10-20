package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.dock.DockGeometry;
import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class ToolWindowAreaTest {
    @org.junit.jupiter.api.BeforeEach void installModuleTheme() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> dev.jasper.app.appearance.ThemeTestSupport.install(true));
    }

    private static Rectangle in(ToolWindowArea area, Component component) {
        return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), area);
    }

    private static void layout(ToolWindowArea area) {
        area.setSize(1000, 600);
        LayoutTestSupport.layoutTree(area);
    }

    private static void mouse(Component target, int id, int x, int y) {
        int modifiers = id == MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON1_DOWN_MASK;
        int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
        target.dispatchEvent(new MouseEvent(target, id, System.currentTimeMillis(), modifiers, x, y, 1, false, button));
    }

    @Test void theCenterKeepsItsRoundedFrameWhenPanelsOpenAndClose() {
        var deck = new JPanel();
        var area = new ToolWindowArea(deck, new DockState());
        area.setStripesVisible(true, true);
        deck.setMinimumSize(new Dimension(420, 320));
        assertThat(area.getMinimumSize()).as("terminal minimum plus the top and bottom gutters and the lower inset")
            .isEqualTo(new Dimension(420, 340));
        deck.setMinimumSize(new Dimension(500, 360));
        assertThat(area.getMinimumSize()).as("terminal font and split minimum changes propagate").isEqualTo(new Dimension(500, 380));
        layout(area);
        assertThat(in(area, deck)).isEqualTo(new Rectangle(0, 6, 1000, 580));
        assertThat(area.content(Slot.LEFT_TOP)).isNull();

        var left = new JLabel("left");
        DockTestSupport.show(area, Slot.LEFT_TOP, left, 250);
        layout(area);
        assertThat(area.content(Slot.LEFT_TOP)).isSameAs(left);
        assertThat(in(area, left.getParent())).as("the frame").isEqualTo(new Rectangle(0, 0, 250, 600));
        int header = UIScale.scale(dev.jasper.ui.swing.FlatLafInstaller.installedKit().metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.NATIVE_TOOL_HEADER_HEIGHT));
        assertThat(in(area, area.headerOf(Slot.LEFT_TOP))).isEqualTo(new Rectangle(0, 6, 250, header));
        assertThat(in(area, left)).as("content under the header, inside the gutters").isEqualTo(new Rectangle(0, 6 + header, 250, 588 - header));
        assertThat(in(area, deck)).isEqualTo(new Rectangle(256, 6, 744, 580));
        assertThat(area.getMinimumSize()).as("a showing frame keeps its minimum").isEqualTo(new Dimension(500 + 6 + 120, 380));

        DockTestSupport.hide(area, Slot.LEFT_TOP);
        layout(area);
        assertThat(left.getParent()).as("hidden content leaves the tree").isNull();
        assertThat(in(area, deck)).isEqualTo(new Rectangle(0, 6, 1000, 580));
        assertThat(area.state().size(Side.LEFT)).as("the size is remembered").isEqualTo(250);
    }

    @Test void theBottomAreaSpansTheFullWidthUnderTheSides() {
        var deck = new JPanel();
        var area = new ToolWindowArea(deck, new DockState());
        area.setStripesVisible(true, true);
        var left = new JLabel("left"); var right = new JLabel("right"); var bottom = new JLabel("bottom");
        DockTestSupport.show(area, Slot.LEFT_TOP, left, 200);
        DockTestSupport.show(area, Slot.RIGHT_TOP, right, 300);
        DockTestSupport.show(area, Slot.BOTTOM_LEFT, bottom, 150);
        layout(area);
        assertThat(in(area, left.getParent())).isEqualTo(new Rectangle(0, 0, 200, 444));
        assertThat(in(area, right.getParent())).isEqualTo(new Rectangle(700, 0, 300, 444));
        assertThat(in(area, bottom.getParent())).as("stripe to stripe").isEqualTo(new Rectangle(0, 450, 1000, 150));
        assertThat(in(area, deck.getParent())).isEqualTo(new Rectangle(206, 0, 488, 444));
        assertThat(in(area, deck).y).isEqualTo(6);

        var replacement = new JLabel("other");
        DockTestSupport.show(area, Slot.RIGHT_TOP, replacement, 320);
        layout(area);
        assertThat(area.content(Slot.RIGHT_TOP)).isSameAs(replacement);
        assertThat(right.getParent()).isNull();
        assertThat(in(area, replacement.getParent()).width).isEqualTo(320);
        assertThat(area.state().size(Side.BOTTOM)).isEqualTo(150);
        area.removeContent(DockTestSupport.id(Slot.BOTTOM_LEFT));
        area.sync();
        assertThat(area.content(Slot.BOTTOM_LEFT)).as("an active window without content shows no frame").isNull();
    }

    @Test void bothSlotsOfASideStackWithTheirOwnHeadersAndGutters() {
        var area = new ToolWindowArea(new JPanel(), new DockState());
        area.setStripesVisible(true, true);
        var upper = new JLabel("sessions"); var lower = new JLabel("sftp");
        DockTestSupport.show(area, Slot.LEFT_TOP, upper, 260);
        DockTestSupport.show(area, Slot.LEFT_BOTTOM, lower, 260);
        layout(area);
        assertThat(in(area, upper.getParent())).isEqualTo(new Rectangle(0, 0, 260, 297));
        assertThat(in(area, lower.getParent())).isEqualTo(new Rectangle(0, 303, 260, 297));
        assertThat(area.headerOf(Slot.LEFT_TOP).titleLabel().getText()).isEqualTo("Left Top");
        assertThat(area.headerOf(Slot.LEFT_BOTTOM).titleLabel().getText()).isEqualTo("Left Bottom");
        assertThat(upper.getParent().getInsets()).as("the upper frame keeps the top gutter, the lower one the bottom gutter")
            .isEqualTo(new Insets(6, 0, 0, 0));
        assertThat(lower.getParent().getInsets()).isEqualTo(new Insets(0, 0, 6, 0));
        assertThat(area.divider(DockGeometry.Handle.LEFT_RATIO).getBounds()).isEqualTo(new Rectangle(0, 297, 260, 6));
        assertThat(area.divider(DockGeometry.Handle.LEFT_RATIO).getCursor().getType()).isEqualTo(Cursor.N_RESIZE_CURSOR);
    }

    @Test void aSplitSidesMinimumHeightCountsBothHalves() {
        var deck = new JPanel();
        var area = new ToolWindowArea(deck, new DockState());
        deck.setMinimumSize(new Dimension(300, 150));
        DockTestSupport.show(area, Slot.LEFT_TOP, new JLabel("upper"), 260);
        assertThat(area.getMinimumSize()).as("one window on a side leaves the minimum as it was")
            .isEqualTo(new Dimension(300 + 6 + 120, 150));
        DockTestSupport.show(area, Slot.RIGHT_TOP, new JLabel("right"), 260);
        assertThat(area.getMinimumSize().height).as("one window per side is not a split").isEqualTo(150);

        DockTestSupport.show(area, Slot.LEFT_BOTTOM, new JLabel("lower"), 260);
        assertThat(area.getMinimumSize().height).as("two minimum frames and the divider between them").isEqualTo(2 * 120 + 6);
        assertThat(area.getPreferredSize().height).isEqualTo(2 * 120 + 6);
        assertThat(area.getMinimumSize().width).as("the width is unchanged").isEqualTo(300 + 2 * (6 + 120));

        DockTestSupport.show(area, Slot.BOTTOM_LEFT, new JLabel("bottom"), 200);
        assertThat(area.getMinimumSize().height).as("the bottom row adds its divider and minimum").isEqualTo(2 * 120 + 6 + 6 + 120);
        assertThat(area.getPreferredSize().height).as("or its saved size, for the preferred size")
            .isEqualTo(2 * 120 + 6 + 6 + 200);

        deck.setMinimumSize(new Dimension(300, 400));
        assertThat(area.getMinimumSize().height).as("a taller terminal column still wins").isEqualTo(400 + 6 + 120);
        deck.setMinimumSize(new Dimension(300, 150));
        DockTestSupport.hide(area, Slot.LEFT_BOTTOM);
        assertThat(area.getMinimumSize().height).as("the minimum follows the split away").isEqualTo(150 + 6 + 120);
        DockTestSupport.show(area, Slot.RIGHT_BOTTOM, new JLabel("right lower"), 260);
        assertThat(area.getMinimumSize().height).as("either side splits it").isEqualTo(2 * 120 + 6 + 6 + 120);
    }

    @Test void onlyASplitSideRaisesTheMinimumHeightWhateverTheUiScale() {
        var metrics = DockGeometry.Metrics.at(1.5);
        // The terminal column's own minimum is under the scaled 120px terminal floor, as with a minimal tab strip.
        var terminal = new Dimension(300, 100);
        var state = new DockState();
        var area = new ToolWindowArea(new JPanel(), state);
        DockTestSupport.show(area, Slot.LEFT_TOP, new JLabel("upper"), 260);
        DockTestSupport.show(area, Slot.BOTTOM_LEFT, new JLabel("bottom"), 200);
        assertThat(ToolWindowArea.size(terminal, state, metrics, false)).as("the terminal's minimum plus the bottom row, as before")
            .isEqualTo(new Dimension(300 + 9 + 180, 100 + 9 + 180));
        assertThat(ToolWindowArea.size(new Dimension(300, 400), state, metrics, false).height).isEqualTo(400 + 9 + 180);

        DockTestSupport.show(area, Slot.LEFT_BOTTOM, new JLabel("lower"), 260);
        assertThat(ToolWindowArea.size(terminal, state, metrics, false).height).as("a split side's halves, then the bottom row")
            .isEqualTo(2 * 180 + 9 + 9 + 180);
        assertThat(ToolWindowArea.size(terminal, state, metrics, true).height).isEqualTo(2 * 180 + 9 + 9 + Math.round(1.5f * 200));
    }

    @Test void theAreaTellsItsWindowWhenWhatShowsChanges() {
        var area = new ToolWindowArea(new JPanel(), new DockState());
        int[] told = {0};
        area.onMinimumSizeChanged = () -> told[0]++;
        DockTestSupport.show(area, Slot.LEFT_TOP, new JLabel("upper"), 260);
        assertThat(told[0]).isEqualTo(1);
        DockTestSupport.show(area, Slot.LEFT_BOTTOM, new JLabel("lower"), 260);
        assertThat(told[0]).isEqualTo(2);
        DockTestSupport.hide(area, Slot.LEFT_BOTTOM);
        assertThat(told[0]).isEqualTo(3);
    }

    @Test void hiddenStripesGiveFramesTheirOuterGutterAndTheTerminalNone() {
        var deck = new JPanel();
        var area = new ToolWindowArea(deck, new DockState());
        var left = new JLabel("left"); var bottom = new JLabel("bottom");
        DockTestSupport.show(area, Slot.LEFT_TOP, left, 250);
        DockTestSupport.show(area, Slot.BOTTOM_RIGHT, bottom, 150);
        layout(area);
        assertThat(left.getParent().getInsets()).isEqualTo(new Insets(6, 6, 0, 0));
        assertThat(bottom.getParent().getInsets()).isEqualTo(new Insets(0, 6, 6, 6));
        assertThat(deck.getParent().getInsets()).isEqualTo(new Insets(0, 0, 0, 0));
        assertThat(in(area, deck)).as("no lower inset either").isEqualTo(in(area, deck.getParent()));
        area.setStripesVisible(true, true); area.setHeaderVisible(true); area.setStatusVisible(true);
        assertThat(left.getParent().getInsets()).isEqualTo(new Insets(0, 0, 0, 0));
        assertThat(bottom.getParent().getInsets()).isEqualTo(new Insets(0, 0, 0, 0));
        assertThat(((javax.swing.JComponent) left.getParent()).getClientProperty("Jasper.zone")).isEqualTo("chrome");
        assertThat(((javax.swing.JComponent) deck.getParent()).getClientProperty("Jasper.zone")).isNull();
    }

    @Test void aDividerIsAQuietGapThatPreviewsADragAndChangesTheStateOnceOnRelease() throws Exception {
        var original = UIManager.getLookAndFeel();
        try {
            var state = new DockState();
            var area = new ToolWindowArea(new JPanel(), state);
            var panel = new JPanel();
            DockTestSupport.show(area, Slot.LEFT_TOP, panel, 250);
            List<DockState.Change> changes = new ArrayList<>();
            state.addListener(changes::add);
            for (boolean themeDark : new boolean[]{true, false}) {
                ThemeTestSupport.install(themeDark);
                SwingUtilities.updateComponentTreeUI(area);
                area.refreshTheme();
                layout(area);
                var divider = area.divider(DockGeometry.Handle.LEFT_WIDTH);
                var image = new BufferedImage(area.getWidth(), area.getHeight(), BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics();
                try { area.paint(g); } finally { g.dispose(); }
                var gap = divider.getBounds();
                assertThat(gap).isEqualTo(new Rectangle(250, 0, 6, 600));
                int chrome = dev.jasper.app.platform.ThemeZone.color(null, dev.jasper.ui.theme.SemanticColor.SURFACE_APP).getRGB();
                for (int x = gap.x; x < gap.x + gap.width; x++) for (int y = gap.y; y < gap.y + gap.height; y += 7)
                    assertThat(image.getRGB(x, y)).as("the gap has no border or grip, %s", (themeDark ? "dark" : "light")).isEqualTo(chrome);
                assertThat(divider.getCursor().getType()).isEqualTo(Cursor.E_RESIZE_CURSOR);
            }

            var divider = area.divider(DockGeometry.Handle.LEFT_WIDTH);
            mouse(divider, MouseEvent.MOUSE_PRESSED, 2, 100);
            mouse(divider, MouseEvent.MOUSE_DRAGGED, 72, 100);
            layout(area);
            assertThat(panel.getParent().getWidth()).as("the drag previews").isEqualTo(320);
            assertThat(state.size(Side.LEFT)).as("but changes nothing yet").isEqualTo(250);
            // The divider moved with the preview, so the same mouse position is 2px into it.
            mouse(divider, MouseEvent.MOUSE_RELEASED, 2, 100);
            assertThat(state.size(Side.LEFT)).isEqualTo(320);
            assertThat(changes).as("one change for the whole drag").hasSize(1);
            layout(area);
            assertThat(panel.getParent().getWidth()).isEqualTo(320);
            assertThat(area.divider(DockGeometry.Handle.BOTTOM_HEIGHT).getBounds()).as("no bottom area, no divider").isEqualTo(new Rectangle());
        } finally { UIManager.setLookAndFeel(original); }
    }

    @Test void aClickOnASqueezedDividerChangesNothing() {
        var state = new DockState();
        var area = new ToolWindowArea(new JPanel(), state);
        var left = new JLabel("left");
        DockTestSupport.show(area, Slot.LEFT_TOP, left, 400);
        List<DockState.Change> changes = new ArrayList<>();
        state.addListener(changes::add);
        // 450px leaves 200 for the terminal and 6 for the divider, so the left side shows at 244, not 400.
        area.setSize(450, 600);
        LayoutTestSupport.layoutTree(area);
        assertThat(left.getParent().getWidth()).as("squeezed").isEqualTo(244);
        var divider = area.divider(DockGeometry.Handle.LEFT_WIDTH);
        mouse(divider, MouseEvent.MOUSE_PRESSED, 2, 100);
        mouse(divider, MouseEvent.MOUSE_RELEASED, 2, 100);
        assertThat(state.size(Side.LEFT)).as("a click keeps the saved size").isEqualTo(400);
        assertThat(changes).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"LEFT_TOP, LEFT_WIDTH, 260, 260", "RIGHT_TOP, RIGHT_WIDTH, 734, 260", "BOTTOM_LEFT, BOTTOM_HEIGHT, 394, 200"})
    void dividerDragPreviewsTheSnapBreaksFreeAndCommitsTheReleasedSize(
            Slot slot, DockGeometry.Handle handle, int target, int defaultSize) {
        var state = new DockState();
        var area = new ToolWindowArea(new JPanel(), state);
        DockTestSupport.show(area, slot, new JPanel(), 300);
        layout(area);
        var divider = area.divider(handle);
        List<DockState.Change> changes = new ArrayList<>();
        state.addListener(changes::add);
        mouse(divider, MouseEvent.MOUSE_PRESSED, 2, 2);
        for (int offset : new int[]{-5, -9, 5, 9, -5}) {
            mouseAtDividerPosition(area, handle, MouseEvent.MOUSE_DRAGGED, target + offset);
            layout(area);
            Rectangle bounds = divider.getBounds();
            assertThat(handle.vertical() ? bounds.x : bounds.y)
                .as("preview at pointer offset %s", offset).isEqualTo(target + (Math.abs(offset) <= 8 ? 0 : offset));
            assertThat(state.size(slot.side())).as("preview has not committed").isEqualTo(300);
            assertThat(changes).isEmpty();
        }
        // Release at a different point inside the zone, relative to the divider's snapped bounds.
        mouseAtDividerPosition(area, handle, MouseEvent.MOUSE_RELEASED, target + 7);
        layout(area);
        assertThat(state.size(slot.side())).isEqualTo(defaultSize);
        assertThat(changes).hasSize(1);
        Rectangle bounds = divider.getBounds();
        assertThat(handle.vertical() ? bounds.x : bounds.y).isEqualTo(target);
    }

    @ParameterizedTest
    @CsvSource({"LEFT_TOP, LEFT_RATIO, 297", "RIGHT_TOP, RIGHT_RATIO, 297", "BOTTOM_LEFT, BOTTOM_RATIO, 497"})
    void pairedToolsPreviewEqualSharesBreakFreeAndCommitTheSnappedRatio(Slot slot, DockGeometry.Handle handle, int target) {
        var state = new DockState();
        var area = new ToolWindowArea(new JPanel(), state);
        DockTestSupport.show(area, slot, new JPanel(), 300);
        DockTestSupport.show(area, slot.partner(), new JPanel(), 300);
        state.setRatio(slot.side(), 0.3);
        layout(area);
        var divider = area.divider(handle);
        List<DockState.Change> changes = new ArrayList<>();
        state.addListener(changes::add);
        mouse(divider, MouseEvent.MOUSE_PRESSED, 2, 2);
        for (int offset : new int[]{-5, -9, 5, 9, -5}) {
            mouseAtDividerPosition(area, handle, MouseEvent.MOUSE_DRAGGED, target + offset);
            layout(area);
            Rectangle bounds = divider.getBounds();
            assertThat(handle.vertical() ? bounds.x : bounds.y)
                .as("preview at pointer offset %s", offset).isEqualTo(target + (Math.abs(offset) <= 8 ? 0 : offset));
            assertThat(state.ratio(slot.side())).as("preview has not committed").isEqualTo(0.3);
            assertThat(changes).isEmpty();
        }
        mouseAtDividerPosition(area, handle, MouseEvent.MOUSE_RELEASED, target + 7);
        layout(area);
        assertThat(state.ratio(slot.side())).isEqualTo(0.5);
        assertThat(changes).hasSize(1);
        Rectangle bounds = divider.getBounds();
        assertThat(handle.vertical() ? bounds.x : bounds.y).isEqualTo(target);
    }

    private static void mouseAtDividerPosition(ToolWindowArea area, DockGeometry.Handle handle, int event, int position) {
        var divider = area.divider(handle);
        var bounds = divider.getBounds();
        // Preserve the two-pixel grab offset while the preview moves the event source.
        mouse(divider, event, handle.vertical() ? position + 2 - bounds.x : 2,
            handle.vertical() ? 2 : position + 2 - bounds.y);
    }

    @Test void sizesAreClampedToAUsableMinimum() {
        var area = new ToolWindowArea(new JPanel(), new DockState());
        var left = new JLabel("left");
        DockTestSupport.show(area, Slot.LEFT_TOP, left, 5);
        layout(area);
        assertThat(area.state().size(Side.LEFT)).isEqualTo(DockState.MIN_SIZE);
        assertThat(left.getParent().getWidth()).isEqualTo(UIScale.scale(DockState.MIN_SIZE));
    }

    @Test void headerTitleDetailAndUnusedSpaceAreDragSourcesButActionButtonsAreNot() {
        var area = new ToolWindowArea(new JPanel(), new DockState());
        var registered = new ArrayList<javax.swing.JComponent>();
        area.dragSource = (component, id) -> registered.add(component);
        String id = DockTestSupport.id(Slot.LEFT_TOP);
        var icon = dev.jasper.app.platform.AppIcons.named("ADD");
        area.setHeader(id, new dev.jasper.app.contributions.PanelHeaderState("prod-web-1", true, List.of(
            new dev.jasper.app.contributions.PanelHeaderState.Action("Add", icon, () -> {}))));
        DockTestSupport.show(area, Slot.LEFT_TOP, new JPanel(), 260);
        var header = area.headerOf(Slot.LEFT_TOP);
        var detail = headerDescendants(header).stream().filter(c -> c instanceof JLabel label && "prod-web-1".equals(label.getText()))
            .map(c -> (JLabel) c).findFirst().orElseThrow();
        assertThat(registered).contains(header, header.titleLabel(), detail, (javax.swing.JComponent) detail.getParent());
        assertThat(registered).doesNotContainAnyElementsOf(headerButtons(header));
    }

    @Test void headerMetadataSurvivesHiddenUpdatesAndMovingWithoutLeakingToAReplacement() {
        var area = new ToolWindowArea(new JPanel(), new DockState());
        String id = "panel.sessions";
        var content = new JPanel();
        var calls = new ArrayList<String>();
        var icon = dev.jasper.app.platform.AppIcons.named("ADD");
        area.setHeader(id, new dev.jasper.app.contributions.PanelHeaderState("5", false, List.of(
            new dev.jasper.app.contributions.PanelHeaderState.Action("Add session", icon, () -> calls.add("first")))));
        area.setContent(id, "Sessions", content);
        area.state().add(id, Slot.LEFT_TOP);
        area.state().show(id);
        area.sync();
        layout(area);
        assertThat(headerTexts(area.headerOf(Slot.LEFT_TOP))).contains("Sessions", "5");
        area.state().hide(id);area.sync();
        area.setHeader(id, new dev.jasper.app.contributions.PanelHeaderState("6", false, List.of(
            new dev.jasper.app.contributions.PanelHeaderState.Action("Add session", icon, () -> calls.add("updated")))));
        assertThat(area.headerOf(Slot.LEFT_TOP)).isNull();
        area.state().move(id, Slot.RIGHT_BOTTOM, 0);
        area.state().show(id);area.sync();layout(area);
        var moved = area.headerOf(Slot.RIGHT_BOTTOM);
        assertThat(area.content(Slot.RIGHT_BOTTOM)).isSameAs(content);
        assertThat(headerTexts(moved)).contains("Sessions", "6").doesNotContain("5");
        headerButtons(moved).stream().filter(button -> "Add session".equals(button.getAccessibleContext().getAccessibleName()))
            .findFirst().orElseThrow().doClick();
        assertThat(calls).containsExactly("updated");
        area.state().remove(id);area.removeContent(id);area.sync();
        area.setContent(id, "Replacement", new JPanel());
        area.state().add(id, Slot.RIGHT_BOTTOM);area.state().show(id);area.sync();layout(area);
        var replacement = area.headerOf(Slot.RIGHT_BOTTOM);
        assertThat(headerTexts(replacement)).contains("Replacement").doesNotContain("6");
        assertThat(headerButtons(replacement)).noneMatch(button -> "Add session".equals(button.getAccessibleContext().getAccessibleName()));
    }

    private static List<Component> headerDescendants(java.awt.Container parent) {
        var result = new ArrayList<Component>();
        for (var child : parent.getComponents()) {
            result.add(child);
            if (child instanceof java.awt.Container nested) result.addAll(headerDescendants(nested));
        }
        return result;
    }

    private static List<String> headerTexts(ToolWindowHeader header) {
        return headerDescendants(header).stream().filter(c -> c instanceof JLabel).map(c -> ((JLabel) c).getText()).toList();
    }

    private static List<javax.swing.JButton> headerButtons(ToolWindowHeader header) {
        return headerDescendants(header).stream().filter(c -> c instanceof javax.swing.JButton).map(c -> (javax.swing.JButton) c).toList();
    }

    @Test void theHeaderHidesAndMovesThroughTheArea() {
        var area = new ToolWindowArea(new JPanel(), new DockState());
        List<String> events = new ArrayList<>();
        area.onHide = id -> events.add("hide:" + id);
        area.onMove = (id, slot) -> events.add("move:" + id + ":" + slot);
        DockTestSupport.show(area, Slot.RIGHT_BOTTOM, new JLabel("files"), 260);
        var header = area.headerOf(Slot.RIGHT_BOTTOM);
        header.hideButton().doClick();
        ToolWindowHeaderTest.item(header.menu(), "Left Top").doClick();
        assertThat(((JCheckBoxMenuItem) ToolWindowHeaderTest.item(header.menu(), "Right Bottom")).isSelected()).isTrue();
        assertThat(events).containsExactly("hide:test.right-bottom", "move:test.right-bottom:LEFT_TOP");
        assertThat(area.headerOf(Slot.LEFT_TOP)).isNull();
    }

    @Test void aTinyAreaLaysOutWithoutNegativeSizes() {
        var area = new ToolWindowArea(new JPanel(), new DockState());
        for (Slot slot : Slot.values()) DockTestSupport.show(area, slot, new JLabel(slot.name()), 300);
        for (int[] size : new int[][]{{0, 0}, {40, 30}, {300, 200}}) {
            area.setSize(size[0], size[1]);
            LayoutTestSupport.layoutTree(area);
            for (Component child : area.getComponents()) {
                assertThat(child.getWidth()).as("%s at %sx%s", child, size[0], size[1]).isNotNegative();
                assertThat(child.getHeight()).isNotNegative();
            }
        }
        area.setSize(2000, 1000);
        LayoutTestSupport.layoutTree(area);
        assertThat(area.content(Slot.LEFT_TOP).getParent().getWidth()).as("a larger area restores the sizes").isEqualTo(300);
    }
}
