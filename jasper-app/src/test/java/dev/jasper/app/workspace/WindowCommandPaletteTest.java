package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.application.ApplicationTestSupport;
import dev.jasper.app.palette.PaletteTestSupport;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.application.JasperApplication;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.Command;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.palette.PaletteController;
import dev.jasper.app.palette.PaletteRow;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WindowCommandPaletteTest {
    static int m(dev.jasper.ui.metrics.DesignMetrics.Role role) { return dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(role); }

    static JRootPane install(WindowContent owner) {
        var root = new JRootPane(); root.setContentPane(owner); owner.installRootBindings(root);
        root.setSize(900, 600); LayoutTestSupport.layoutTree(root); return root;
    }
    static WindowContent owner(CommandHistory history) {
        return new WindowContent(DesktopTestSupport.launcher(new ArrayDeque<>()), DesktopTestSupport.HOME,
            path -> {}, () -> {}, () -> {}, ThemeTestSupport.controller(true), KeyBindings.defaults(true), history, true);
    }
    static Command command(String id, Runnable run) {
        return new Command(id, new AbstractAction(id) {
            @Override public void actionPerformed(ActionEvent event) { run.run(); }
        }, List.of());
    }
    @Test void normalAndCompactKeepTheSameActivatedWidthAndClampToNarrowWorkspaces() throws Exception {
        DesktopTestSupport.edt(() -> {
            for (var mode : dev.jasper.app.config.ViewMode.values()) {
                try (var owner = owner(new CommandHistory())) {
                    owner.applyConfiguration(dev.jasper.app.config.ConfigSnapshot.defaults().toBuilder().viewMode(mode).build(), true);
                    var root = install(owner);
                    root.setSize(1400, 900); LayoutTestSupport.layoutTree(root);
                    owner.action(ActionId.COMMAND_PALETTE).actionPerformed(new ActionEvent(owner, 0, "fixture"));
                    LayoutTestSupport.layoutTree(root);
                    var card = owner.commandPalette().component();
                    assertThat(card.getWidth()).as("roomy activated card in %s", mode)
                        .isEqualTo(com.formdev.flatlaf.util.UIScale.scale(560));
                    for (int width : new int[] {350, 80}) {
                        root.setSize(com.formdev.flatlaf.util.UIScale.scale(width), 210);
                        LayoutTestSupport.layoutTree(root);
                        root.dispatchEvent(new ComponentEvent(root, ComponentEvent.COMPONENT_RESIZED));
                        LayoutTestSupport.layoutTree(root);
                        assertThat(card.getWidth()).as("clamped card in %s, workspace width %d", mode, width)
                            .isEqualTo(com.formdev.flatlaf.util.UIScale.scale(width - 32));
                        var overlay = (JComponent) card.getParent();
                        var workspace = SwingUtilities.convertRectangle(owner.getParent(), owner.getBounds(), overlay);
                        assertThat(workspace.contains(card.getBounds())).isTrue();
                    }
                }
            }
        });
    }

    @Test void normalWithoutAnAnchorKeepsItsExistingFloatingPreferredWidth() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var owner = owner(new CommandHistory())) {
                owner.chrome().search().setVisible(false);
                var root = install(owner); root.setSize(1400, 900); LayoutTestSupport.layoutTree(root);
                owner.commandPalette().toggle(); LayoutTestSupport.layoutTree(root);
                assertThat(owner.commandPalette().component().getWidth())
                    .isEqualTo(com.formdev.flatlaf.util.UIScale.scale(680));
            }
        });
    }

    @Test void compactActionUsesFloatingWorkspaceBoundsWithDockPanelsAndDismissesNormally() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var owner = owner(new CommandHistory())) {
                owner.applyConfiguration(dev.jasper.app.config.ConfigSnapshot.defaults().toBuilder()
                    .viewMode(dev.jasper.app.config.ViewMode.COMPACT).build(), true);
                DockTestSupport.show(owner, dev.jasper.app.dock.Slot.LEFT_TOP, new JPanel(), 240);
                DockTestSupport.show(owner, dev.jasper.app.dock.Slot.RIGHT_TOP, new JPanel(), 170);
                var root = install(owner);
                root.setSize(1400, 900); LayoutTestSupport.layoutTree(root);
                var card = owner.commandPalette().component();
                owner.action(ActionId.COMMAND_PALETTE).actionPerformed(new ActionEvent(owner, 0, "fixture"));
                assertThat(owner.commandPalette().isOpen()).isTrue();
                assertThat(owner.commandPalette().component()).isSameAs(card);
                LayoutTestSupport.layoutTree(root);
                var overlay = (JComponent) card.getParent();
                var workspace = SwingUtilities.convertRectangle(owner.getParent(), owner.getBounds(), overlay);
                assertThat(card.getBounds()).isEqualTo(WindowCommandPalette.positioned(workspace,
                    new Dimension(com.formdev.flatlaf.util.UIScale.scale(560), card.getPreferredSize().height), com.formdev.flatlaf.util.UIScale.scale(16)));
                assertThat(card.getX() + card.getWidth() / 2).isEqualTo(workspace.x + workspace.width / 2);
                assertThat(card.getY()).isEqualTo(workspace.y + workspace.height / 5);
                int top = card.getY(); int initialHeight = card.getHeight();
                dev.jasper.app.palette.PaletteTestSupport.query(card).setText("no matching commands anywhere"); LayoutTestSupport.layoutTree(root);
                assertThat(card.getY()).isEqualTo(top);
                assertThat(card.getHeight()).isLessThan(initialHeight);
                dev.jasper.app.palette.PaletteTestSupport.query(card).setText(""); LayoutTestSupport.layoutTree(root);
                assertThat(card.getY()).isEqualTo(top);
                for (Dimension size : List.of(new Dimension(350, 210), new Dimension(80, 65))) {
                    root.setSize(size); LayoutTestSupport.layoutTree(root);
                    root.dispatchEvent(new ComponentEvent(root, ComponentEvent.COMPONENT_RESIZED));
                    LayoutTestSupport.layoutTree(root);
                    workspace = SwingUtilities.convertRectangle(owner.getParent(), owner.getBounds(), overlay);
                    assertThat(card.getBounds()).isEqualTo(WindowCommandPalette.positioned(workspace,
                        new Dimension(com.formdev.flatlaf.util.UIScale.scale(560), card.getPreferredSize().height), com.formdev.flatlaf.util.UIScale.scale(16)));
                    assertThat(workspace.contains(card.getBounds())).isTrue();
                }
                owner.commandPalette().escape();
                assertThat(owner.commandPalette().isOpen()).isFalse();
                root.setSize(900, 600); LayoutTestSupport.layoutTree(root);
                owner.action(ActionId.COMMAND_PALETTE).actionPerformed(new ActionEvent(owner, 0, "fixture"));
                overlay.dispatchEvent(new MouseEvent(overlay, MouseEvent.MOUSE_PRESSED, 0, 0, 1, 1, 1, false, MouseEvent.BUTTON1));
                assertThat(owner.commandPalette().isOpen()).isFalse();
            }
        });
    }

    @Test void theCardTopIsFixedSoTheInputRowNeverMovesAsResultsChange() {
        var deck = new Rectangle(0, 100, 900, 600);
        int anchor = deck.y + deck.height / 5;
        for (int height : new int[] {96, 160, 304, 420}) {
            var placed = WindowCommandPalette.positioned(deck, new Dimension(560, height), 16);
            assertThat(placed.y).as("card top for height %d", height).isEqualTo(anchor);
        }
    }
    @Test void aCardTallerThanTheWindowIsPulledUpToStayOnScreen() {
        var deck = new Rectangle(0, 100, 900, 300);
        var placed = WindowCommandPalette.positioned(deck, new Dimension(560, 400), 16);
        assertThat(placed.y).isEqualTo(deck.y + 16);
        assertThat(placed.height).isEqualTo(300 - 32);
    }
    @Test void neitherAScopeSwitchNorANewRowCountMovesTheInputRow() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var owner = owner(new CommandHistory())) {
                var root = install(owner);
                var fake = new PaletteScopesTest.FakeScope();
                owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle(); LayoutTestSupport.layoutTree(root);
                int anchor = card.getBounds().y; int commandsHeight = card.getBounds().height;
                palette.open(fake.id()); LayoutTestSupport.layoutTree(root);
                assertThat(card.getBounds().y).as("after a scope switch").isEqualTo(anchor);
                fake.rows = List.of();
                fake.listeners.forEach(Runnable::run); LayoutTestSupport.layoutTree(root);
                assertThat(card.getBounds().y).as("with no rows").isEqualTo(anchor);
                int emptyHeight = card.getBounds().height;
                fake.rows = List.of(PaletteRow.of("a", "A"), PaletteRow.of("b", "B"), PaletteRow.of("c", "C"),
                    PaletteRow.of("d", "D"), PaletteRow.of("e", "E"));
                fake.listeners.forEach(Runnable::run); LayoutTestSupport.layoutTree(root);
                assertThat(card.getBounds().y).as("with five rows").isEqualTo(anchor);
                // The card must actually have changed height, or the assertions above prove nothing.
                assertThat(card.getBounds().height).isGreaterThan(emptyHeight);
                assertThat(commandsHeight).isNotEqualTo(emptyHeight);
            }
        });
    }
    @Test void anAnchoredCardCoversTheFieldIsAtLeast560WideAndStaysInTheWindow() {
        var window = new Rectangle(0, 0, 1400, 900);
        var field = new Rectangle(420, 4, 560, 22);
        assertThat(WindowCommandPalette.anchored(field, window, window, new Dimension(680, 300), 560, 16))
            .isEqualTo(new Rectangle(420, 4, 560, 300));
        var narrow = new Rectangle(500, 4, 300, 22);
        assertThat(WindowCommandPalette.anchored(narrow, window, window, new Dimension(680, 300), 560, 16))
            .as("centred on the field, widened to the minimum").isEqualTo(new Rectangle(370, 4, 560, 300));
        var edge = new Rectangle(20, 4, 300, 22);
        assertThat(WindowCommandPalette.anchored(edge, window, window, new Dimension(680, 300), 560, 16).x).isEqualTo(16);
        var small = new Rectangle(0, 0, 350, 210);
        assertThat(WindowCommandPalette.anchored(new Rectangle(20, 4, 286, 22), small, small, new Dimension(680, 400), 560, 16))
            .isEqualTo(new Rectangle(16, 4, 318, 210 - 16 - 4));
    }
    @Test void anAnchoredCardStaysClearOfTheWindowControls() {
        var field = new Rectangle(250, 4, 200, 22);
        var window = new Rectangle(0, 0, 700, 600);
        assertThat(WindowCommandPalette.anchored(field, window, new Rectangle(96, 0, 596, 38), new Dimension(680, 300), 560, 16))
            .as("controls at the left: moved right, still the minimum wide").isEqualTo(new Rectangle(96, 4, 560, 300));
        assertThat(WindowCommandPalette.anchored(field, window, new Rectangle(0, 0, 562, 38), new Dimension(680, 300), 560, 16))
            .as("controls at the right: narrowed to the room before them").isEqualTo(new Rectangle(16, 4, 546, 300));
        var narrow = new Rectangle(0, 0, 658, 600);
        assertThat(WindowCommandPalette.anchored(new Rectangle(229, 4, 200, 22), narrow, new Rectangle(96, 0, 554, 38),
            new Dimension(680, 300), 560, 16)).as("no room for the minimum").isEqualTo(new Rectangle(96, 4, 546, 300));
    }
    @Test void overlayDoesNotResizeTerminalAndTheCardDropsFromTheTitleField() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var owner = owner(new CommandHistory())) {
                var root = install(owner); var before = owner.workspace().getBounds();
                owner.commandPalette().toggle(); LayoutTestSupport.layoutTree(root);
                assertThat(owner.workspace().getBounds()).isEqualTo(before);
                var palette = owner.commandPalette().component();
                var search = owner.chrome().search();
                var field = SwingUtilities.convertRectangle(search.getParent(), search.getBounds(), palette.getParent());
                assertThat(palette.getY()).as("the card covers the field").isEqualTo(field.y);
                assertThat(Math.abs(palette.getBounds().getCenterX() - field.getCenterX())).isLessThanOrEqualTo(1);
                assertThat(palette.getWidth()).isEqualTo(Math.max(field.width, 560));
                owner.setActive(false); assertThat(owner.commandPalette().isOpen()).isFalse();
            }
        });
    }
    @Test void aCollapsedFieldFallsBackToTheCentredCard() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var owner = owner(new CommandHistory())) {
                var root = install(owner);
                owner.chrome().search().setCollapsed(true);
                owner.commandPalette().toggle();
                owner.chrome().search().setCollapsed(true);
                ((JComponent) owner.commandPalette().component().getParent()).doLayout();
                var palette = owner.commandPalette().component();
                var deck = SwingUtilities.convertRectangle(owner.getParent(), owner.getBounds(), palette.getParent());
                assertThat(palette.getBounds().y).isEqualTo(deck.y + deck.height / 5);
            }
        });
    }
    @Test void toolPanelsDoNotShiftOrConstrainTheDroppedPalette() throws Exception {
        DesktopTestSupport.edt(() -> {
            for (boolean nativeHeader : new boolean[]{false, true}) {
                try (var owner = owner(new CommandHistory())) {
                    var root = new JRootPane();
                    try (var title = WindowContent.installTitleBar(root, owner, nativeHeader, value -> {})) {
                        owner.installRootBindings(root); root.setSize(1200, 800);
                        LayoutTestSupport.layoutTree(root);
                        owner.commandPalette().toggle(); LayoutTestSupport.layoutTree(root);
                        var palette = owner.commandPalette().component();
                        var placed = palette.getBounds();
                        for (int sideWidth : new int[]{260, 400}) {
                            DockTestSupport.show(owner, dev.jasper.app.dock.Slot.LEFT_TOP, new JPanel(), sideWidth);
                            DockTestSupport.show(owner, dev.jasper.app.dock.Slot.RIGHT_TOP, new JPanel(), 220);
                            DockTestSupport.show(owner, dev.jasper.app.dock.Slot.BOTTOM_LEFT, new JPanel(), 180);
                            LayoutTestSupport.layoutTree(root); LayoutTestSupport.layoutTree(root);
                            assertThat(palette.getBounds()).as("panels leave the card where it is").isEqualTo(placed);
                        }
                        root.setSize(900, 700); LayoutTestSupport.layoutTree(root); LayoutTestSupport.layoutTree(root);
                        var search = owner.chrome().search();
                        var field = SwingUtilities.convertRectangle(search.getParent(), search.getBounds(), palette.getParent());
                        assertThat(Math.abs(palette.getBounds().getCenterX() - field.getCenterX())).isLessThanOrEqualTo(1);
                        assertThat(palette.getY()).isEqualTo(field.y);
                    }
                }
            }
        });
    }
    @Test void aNarrowWindowKeepsTheDroppedCardClearOfTheWindowControls() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var owner = owner(new CommandHistory())) {
                var root = new JRootPane();
                try (var title = WindowContent.installTitleBar(root, owner, true, value -> {})) {
                    owner.installRootBindings(root); root.setSize(com.formdev.flatlaf.util.UIScale.scale(658), 600);
                    LayoutTestSupport.layoutTree(root);
                    owner.commandPalette().toggle(); LayoutTestSupport.layoutTree(root);
                    var palette = owner.commandPalette().component();
                    var header = owner.chrome().header();
                    int clear = SwingUtilities.convertPoint(header, header.getInsets().left, 0, palette.getParent()).x;
                    assertThat(clear).as("the header keeps room for the controls").isPositive();
                    assertThat(palette.getX()).as("the card starts past the window controls").isGreaterThanOrEqualTo(clear);
                    assertThat(palette.getBounds().getMaxX()).as("and stays inside the window")
                        .isLessThanOrEqualTo(root.getWidth() - com.formdev.flatlaf.util.UIScale.scale(16));
                    var search = owner.chrome().search();
                    var field = SwingUtilities.convertRectangle(search.getParent(), search.getBounds(), palette.getParent());
                    assertThat(palette.getY()).as("still dropped from the field").isEqualTo(field.y);
                }
            }
        });
    }
    @Test void theCardDripsOpenFastThenSlowWithoutMovingOrResizing() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var owner = owner(new CommandHistory())) {
                var clock = new java.util.concurrent.atomic.AtomicLong(1_000_000_000L);
                var palette = owner.commandPalette(); palette.useClock(clock::get);
                var root = install(owner);
                palette.toggle(); LayoutTestSupport.layoutTree(root);
                var card = palette.component();
                Rectangle placed = card.getBounds();
                assertThat(palette.revealRunning()).isTrue();
                assertThat(palette.view().revealedHeight()).as("the reveal starts closed").isZero();
                int previous = 0;
                for (int quarter = 1; quarter <= 4; quarter++) {
                    clock.addAndGet(WindowCommandPalette.REVEAL_MILLIS * 1_000_000L / 4);
                    palette.animate(clock.get());
                    LayoutTestSupport.layoutTree(root);
                    assertThat(card.getBounds()).as("only the visible edge moves").isEqualTo(placed);
                    assertThat(palette.view().revealedHeight()).as("quarter %d", quarter).isGreaterThanOrEqualTo(previous);
                    previous = palette.view().revealedHeight();
                    if (quarter == 1) assertThat(previous).as("fast at first").isGreaterThan(placed.height / 2);
                }
                assertThat(palette.view().revealedHeight()).isEqualTo(placed.height);
                assertThat(palette.revealRunning()).isFalse();
            }
        });
    }
    private static void mouse(JComponent card, int type, Point overlayPoint, int clicks) {
        var at = SwingUtilities.convertPoint(card.getParent(), overlayPoint, card);
        card.dispatchEvent(new MouseEvent(card, type, 0, type == MouseEvent.MOUSE_RELEASED || type == MouseEvent.MOUSE_CLICKED
            ? 0 : InputEvent.BUTTON1_DOWN_MASK, at.x, at.y, clicks, false, MouseEvent.BUTTON1));
    }
    private static Point grab(JComponent card) {
        // The field row's top padding: no field, chip or row takes a press there.
        return new Point(card.getX() + card.getWidth() / 2, card.getY() + com.formdev.flatlaf.util.UIScale.scale(3));
    }
    @Test void draggingTheCardByItsEmptyAreasMovesSnapsAndRemembersItForEveryWindow() throws Exception {
        DesktopTestSupport.edt(() -> {
            var state = dev.jasper.app.persistence.UiState.inMemory();
            try (var owner = owner(new CommandHistory()); var other = owner(new CommandHistory())) {
                owner.connectContributions(new dev.jasper.app.contributions.Contributions(), state);
                other.connectContributions(new dev.jasper.app.contributions.Contributions(), state);
                var root = install(owner); root.setSize(1400, 900); LayoutTestSupport.layoutTree(root);
                var palette = owner.commandPalette();
                palette.toggle(); LayoutTestSupport.layoutTree(root);
                var card = palette.component();
                var overlay = (JComponent) card.getParent();
                Rectangle anchoredBounds = card.getBounds();
                Point press = grab(card);
                mouse(card, MouseEvent.MOUSE_PRESSED, press, 1);
                mouse(card, MouseEvent.MOUSE_DRAGGED, new Point(press.x + 2, press.y + 1), 1);
                assertThat(palette.dragging()).as("a press that barely moves is a click").isFalse();
                assertThat(card.getBounds()).isEqualTo(anchoredBounds);

                mouse(card, MouseEvent.MOUSE_DRAGGED, new Point(press.x - 300, press.y + 250), 1);
                assertThat(palette.dragging()).isTrue();
                assertThat(palette.snappedToCenter() || palette.snappedToTop()).isFalse();
                assertThat(card.getLocation()).isEqualTo(new Point(anchoredBounds.x - 300, anchoredBounds.y + 250));
                LayoutTestSupport.layoutTree(root);
                assertThat(card.getLocation()).as("layout keeps the dragged place").isEqualTo(new Point(anchoredBounds.x - 300, anchoredBounds.y + 250));
                mouse(card, MouseEvent.MOUSE_RELEASED, new Point(press.x - 300, press.y + 250), 1);
                assertThat(palette.dragging()).isFalse();
                var saved = state.palettePosition().orElseThrow();
                Rectangle window = SwingUtilities.convertRectangle(owner.getParent(), owner.getBounds(), overlay);
                assertThat(saved.centerX()).isCloseTo((card.getX() + card.getWidth() / 2.0 - window.x) / window.width, within(0.002));
                assertThat(saved.top()).isCloseTo((card.getY() - window.y) / (double) window.height, within(0.002));
                Rectangle dropped = card.getBounds();
                palette.dismiss(); palette.toggle(); LayoutTestSupport.layoutTree(root);
                assertThat(card.getBounds()).as("reopening keeps the saved place").isEqualTo(dropped);
                palette.dismiss();

                var otherRoot = install(other); otherRoot.setSize(1000, 700); LayoutTestSupport.layoutTree(otherRoot);
                other.commandPalette().toggle(); LayoutTestSupport.layoutTree(otherRoot);
                var otherCard = other.commandPalette().component();
                var otherWindow = SwingUtilities.convertRectangle(other.getParent(), other.getBounds(), otherCard.getParent());
                assertThat(otherCard.getBounds()).as("another window opens it in the same relative place")
                    .isEqualTo(WindowCommandPalette.placed(otherWindow, otherCard.getSize(), saved, com.formdev.flatlaf.util.UIScale.scale(16)));
                other.commandPalette().dismiss();

                palette.toggle(); LayoutTestSupport.layoutTree(root);
                press = grab(card);
                int guideX = window.x + (int) Math.round(window.width * 0.5), guideY = window.y + (int) Math.round(window.height * 0.2);
                int dx = guideX - (card.getX() + card.getWidth() / 2) + 5, dy = guideY - card.getY() - 6;
                mouse(card, MouseEvent.MOUSE_PRESSED, press, 1);
                mouse(card, MouseEvent.MOUSE_DRAGGED, new Point(press.x + dx, press.y + dy), 1);
                assertThat(palette.snappedToCenter()).isTrue();
                assertThat(palette.snappedToTop()).isTrue();
                assertThat(card.getX() + card.getWidth() / 2).isEqualTo(guideX);
                assertThat(card.getY()).isEqualTo(guideY);
                var image = new java.awt.image.BufferedImage(overlay.getWidth(), overlay.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var graphics = image.createGraphics();
                try { overlay.paint(graphics); } finally { graphics.dispose(); }
                assertThat(image.getRGB(guideX, window.y + window.height - 5) >>> 24).as("the centre guide is felt, not drawn").isZero();
                assertThat(image.getRGB(window.x + 5, guideY) >>> 24).as("the top guide is felt, not drawn").isZero();
                mouse(card, MouseEvent.MOUSE_RELEASED, new Point(press.x + dx, press.y + dy), 1);
                assertThat(state.palettePosition()).hasValue(new dev.jasper.app.persistence.UiState.PalettePosition(0.5, 0.2));

                press = grab(card);
                mouse(card, MouseEvent.MOUSE_PRESSED, press, 1);
                mouse(card, MouseEvent.MOUSE_RELEASED, press, 1);
                mouse(card, MouseEvent.MOUSE_CLICKED, press, 2);
                assertThat(state.palettePosition()).as("a double click forgets the place").isEmpty();
                assertThat(card.getBounds()).as("back under the title field").isEqualTo(anchoredBounds);
            }
        });
    }
    @Test void draggingTheCardBackToTheTitleFieldReattachesIt() throws Exception {
        DesktopTestSupport.edt(() -> {
            var state = dev.jasper.app.persistence.UiState.inMemory();
            try (var owner = owner(new CommandHistory())) {
                owner.connectContributions(new dev.jasper.app.contributions.Contributions(), state);
                var root = install(owner); root.setSize(1400, 900); LayoutTestSupport.layoutTree(root);
                var palette = owner.commandPalette();
                palette.toggle(); LayoutTestSupport.layoutTree(root);
                var card = palette.component();
                Rectangle home = card.getBounds();
                Point press = grab(card);
                mouse(card, MouseEvent.MOUSE_PRESSED, press, 1);
                mouse(card, MouseEvent.MOUSE_DRAGGED, new Point(press.x - 300, press.y + 250), 1);
                mouse(card, MouseEvent.MOUSE_RELEASED, new Point(press.x - 300, press.y + 250), 1);
                assertThat(state.palettePosition()).isPresent();
                palette.dismiss(); palette.toggle(); LayoutTestSupport.layoutTree(root);
                assertThat(card.getBounds()).isNotEqualTo(home);

                press = grab(card);
                int dx = home.x - card.getX(), dy = home.y - card.getY();
                mouse(card, MouseEvent.MOUSE_PRESSED, press, 1);
                mouse(card, MouseEvent.MOUSE_DRAGGED, new Point(press.x + dx + 30, press.y + dy + 60), 1);
                assertThat(palette.snappedHome()).as("still too far below its home").isFalse();
                mouse(card, MouseEvent.MOUSE_DRAGGED, new Point(press.x + dx + 12, press.y + dy - 80), 1);
                assertThat(palette.snappedHome()).as("near it across and above it").isTrue();
                assertThat(card.getBounds()).isEqualTo(home);
                mouse(card, MouseEvent.MOUSE_RELEASED, new Point(press.x + dx + 12, press.y + dy - 80), 1);
                assertThat(state.palettePosition()).as("re-attached: the dragged place is forgotten").isEmpty();
                assertThat(card.getBounds()).isEqualTo(home);
                palette.dismiss(); palette.toggle(); LayoutTestSupport.layoutTree(root);
                assertThat(card.getBounds()).isEqualTo(home);
            }
        });
    }
    @Test void compactModeHasNoHomeToSnapTo() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var owner = owner(new CommandHistory())) {
                owner.applyConfiguration(dev.jasper.app.config.ConfigSnapshot.defaults().toBuilder()
                    .viewMode(dev.jasper.app.config.ViewMode.COMPACT).build(), true);
                var root = install(owner); root.setSize(1400, 900); LayoutTestSupport.layoutTree(root);
                var palette = owner.commandPalette();
                palette.toggle(); LayoutTestSupport.layoutTree(root);
                var card = palette.component();
                Point press = grab(card);
                mouse(card, MouseEvent.MOUSE_PRESSED, press, 1);
                mouse(card, MouseEvent.MOUSE_DRAGGED, new Point(press.x, press.y - 400), 1);
                assertThat(palette.dragging()).isTrue();
                assertThat(palette.snappedHome()).isFalse();
                mouse(card, MouseEvent.MOUSE_RELEASED, new Point(press.x, press.y - 400), 1);
                assertThat(owner.uiState().palettePosition()).isPresent();
            }
        });
    }
    @Test void aSavedPlaceKeepsTheCardInsideTheWindow() {
        var window = new Rectangle(0, 40, 1000, 600);
        var size = new Dimension(560, 300);
        assertThat(WindowCommandPalette.placed(window, size, new dev.jasper.app.persistence.UiState.PalettePosition(0.5, 0.2), 16))
            .isEqualTo(new Rectangle(220, 160, 560, 300));
        assertThat(WindowCommandPalette.placed(window, size, new dev.jasper.app.persistence.UiState.PalettePosition(1, 1), 16))
            .as("pulled back inside the margin").isEqualTo(new Rectangle(1000 - 16 - 560, 40 + 600 - 16 - 300, 560, 300));
        assertThat(WindowCommandPalette.placed(new Rectangle(0, 0, 400, 200), size, new dev.jasper.app.persistence.UiState.PalettePosition(0, 0), 16))
            .as("narrower than the card").isEqualTo(new Rectangle(16, 16, 400 - 32, 200 - 32));
    }
    @Test void theDripEasesOutMonotonically() {
        assertThat(WindowCommandPalette.drip(0)).isZero();
        assertThat(WindowCommandPalette.drip(1)).isEqualTo(1);
        double previous = 0, previousStep = Double.MAX_VALUE;
        for (int i = 1; i <= 20; i++) {
            double value = WindowCommandPalette.drip(i / 20.0), step = value - previous;
            assertThat(value).isGreaterThan(previous);
            assertThat(step).as("each step is smaller than the last").isLessThan(previousStep);
            previous = value; previousStep = step;
        }
    }
    @Test void disabledAnimationsOpenTheCardFullyAndDisablingMidwayFinishesTheReveal() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var owner = owner(new CommandHistory())) {
                var clock = new java.util.concurrent.atomic.AtomicLong(1_000_000_000L);
                var palette = owner.commandPalette(); palette.useClock(clock::get);
                var root = install(owner);
                owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 475, 425, 0));
                palette.toggle(); LayoutTestSupport.layoutTree(root);
                assertThat(palette.revealRunning()).isFalse();
                assertThat(palette.view().revealedHeight()).isEqualTo(palette.component().getHeight()).isPositive();
                palette.dismiss();

                owner.setAnimations(dev.jasper.app.config.AnimationSettings.defaults());
                palette.toggle(); LayoutTestSupport.layoutTree(root);
                assertThat(palette.revealRunning()).isTrue();
                owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 475, 425, 0));
                assertThat(palette.revealRunning()).isFalse();
                assertThat(palette.view().revealedHeight()).isEqualTo(palette.component().getHeight());
                palette.dismiss();

                owner.setAnimations(dev.jasper.app.config.AnimationSettings.defaults());
                palette.toggle(); LayoutTestSupport.layoutTree(root);
                clock.addAndGet(WindowCommandPalette.REVEAL_MILLIS * 1_000_000L / 3);
                palette.animate(clock.get());
                palette.dismiss();
                assertThat(palette.revealRunning()).as("dismissing stops the reveal").isFalse();
                palette.toggle(); LayoutTestSupport.layoutTree(root);
                assertThat(palette.revealRunning()).as("every fresh open drips again").isTrue();
                assertThat(palette.view().revealedHeight()).isZero();
            }
        });
    }
    /** Spec 10.9: under a still look the card opens fully at once, though the window's animation settings are on. */
    @Test void aStillLookOpensTheCardFullyAtOnce() throws Exception {
        DesktopTestSupport.edt(() -> {
            var themes = ThemeTestSupport.controller(true);
            themes.configureAnimations(false);
            try (var owner = new WindowContent(DesktopTestSupport.launcher(new ArrayDeque<>()), DesktopTestSupport.HOME,
                    path -> {}, () -> {}, () -> {}, themes, KeyBindings.defaults(true), new CommandHistory(), true)) {
                var palette = owner.commandPalette();
                var root = install(owner);
                owner.setAnimations(dev.jasper.app.config.AnimationSettings.defaults());
                palette.toggle(); LayoutTestSupport.layoutTree(root);
                assertThat(palette.revealRunning()).isFalse();
                assertThat(palette.view().revealedHeight()).isEqualTo(palette.component().getHeight()).isPositive();
                palette.dismiss();
            } finally { themes.close(); }
        });
    }
    /** theme.md's shadows are black, never theme colours: a red scrim leaves the palette's shadow neutral. */
    @Test void overlayShadowIsBlackWhateverTheScrim() throws Exception {
        DesktopTestSupport.edt(() -> {
            var themes = ThemeTestSupport.controller(true);
            try (var owner = new WindowContent(DesktopTestSupport.launcher(new ArrayDeque<>()), DesktopTestSupport.HOME,
                    path -> {}, () -> {}, () -> {}, themes, KeyBindings.defaults(true), new CommandHistory(), true)) {
                var root = install(owner);
                owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 475, 425, 0));
                owner.commandPalette().toggle(); LayoutTestSupport.layoutTree(root);
                var overlay = (JComponent) owner.commandPalette().component().getParent();
                var card = owner.commandPalette().component().getBounds();
                try {
                    // A red scrim in the window's binding and the installed look, and a red popup shadow over the installed one.
                    var red = ThemeTestSupport.recolored(ThemeTestSupport.generated(true),
                        java.util.Map.of(dev.jasper.ui.theme.SemanticColor.SURFACE_SCRIM, Color.RED));
                    ThemeTestSupport.install(red);
                    UIManager.put("Popup.dropShadowColor", Color.RED);
                    themes.appearance().binding().publish(new dev.jasper.ui.theme.ThemePair(red, red));
                    owner.commandPalette().refreshTheme();
                    var image = new BufferedImage(overlay.getWidth(), overlay.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    var graphics = image.createGraphics();
                    try {
                        graphics.setColor(Color.GRAY);
                        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                        overlay.paint(graphics);
                    } finally { graphics.dispose(); }
                    var pixel = new Color(image.getRGB(card.x - 2, card.y - 2), true);
                    assertThat(pixel.getRed()).as("the shadow darkens what lies beneath").isLessThan(Color.GRAY.getRed());
                    assertThat(pixel.getRed()).as("and stays neutral, not the scrim's red").isCloseTo(pixel.getGreen(), org.assertj.core.api.Assertions.within(1));
                } finally {
                    // Putting the saved value back would pin it in the developer defaults, ahead of
                    // whatever look and feel installs next; clearing the override is the real restore.
                    UIManager.put("Popup.dropShadowColor", null);
                }
            }
        });
    }

    @SuppressWarnings("try")
    @Test void registeredActionsExecuteAndRecentsAreSharedWithoutCrossWindowDispatch() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var history = new CommandHistory(); var first = owner(history); var second = owner(history)) {
                install(first); install(second);
                var a = new AtomicInteger(); var b = new AtomicInteger();
                first.commands().register(command("custom", a::incrementAndGet));
                second.commands().register(command("custom", b::incrementAndGet));
                first.commandPalette().toggle(); dev.jasper.app.palette.PaletteTestSupport.query(first.commandPalette().component()).setText("custom");
                dev.jasper.app.palette.PaletteTestSupport.executeSelected(first.commandPalette().component());
                assertThat(a.get()).isEqualTo(1); assertThat(b.get()).isZero();
                second.commandPalette().open(dev.jasper.app.palette.PaletteScope.COMMANDS_ID);
                assertThat(dev.jasper.app.palette.PaletteTestSupport.selectedRow(second.commandPalette().component()).id()).isEqualTo("custom");
                first.close(); dev.jasper.app.palette.PaletteTestSupport.executeSelected(second.commandPalette().component());
                assertThat(b.get()).isEqualTo(1); assertThat(history.recent()).containsExactly("custom");
            }
        });
    }
    @Test void staleRowsAndDisabledCommandsCannotExecute() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var owner = owner(new CommandHistory())) {
                install(owner); var count = new AtomicInteger(); var stale = command("custom", count::incrementAndGet);
                var registration = owner.commands().register(stale);
                owner.commandPalette().toggle(); dev.jasper.app.palette.PaletteTestSupport.query(owner.commandPalette().component()).setText("custom");
                registration.close(); owner.commands().register(command("custom", count::incrementAndGet));
                // Simulate a row already selected by a queued mouse/key event.
                PaletteTestSupport.setRows(owner.commandPalette().component(), owner.commandsScope(), PaletteTestSupport.rows(owner.commandsScope(), List.of(stale)), null);
                dev.jasper.app.palette.PaletteTestSupport.executeSelected(owner.commandPalette().component()); assertThat(count.get()).isZero();
                owner.commandPalette().toggle(); stale = command("disabled", count::incrementAndGet);
                owner.commands().register(stale); stale.action().setEnabled(false);
                PaletteTestSupport.setRows(owner.commandPalette().component(), owner.commandsScope(), PaletteTestSupport.rows(owner.commandsScope(), List.of(stale)), null);
                dev.jasper.app.palette.PaletteTestSupport.executeSelected(owner.commandPalette().component()); assertThat(count.get()).isZero();
            }
        });
    }
    @Test void tabChangesDismissAndUnchangedUpdatesDoNotRebuildResults() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var owner = owner(new CommandHistory())) {
                install(owner); owner.commandPalette().toggle();
                long revision = dev.jasper.app.palette.PaletteTestSupport.revision(owner.commandPalette().component());
                var spec = dev.jasper.app.palette.PaletteTestSupport.resultsSpec(owner.commandPalette().component());
                owner.update(); owner.update();
                assertThat(dev.jasper.app.palette.PaletteTestSupport.revision(owner.commandPalette().component())).as("the list was not replaced").isEqualTo(revision);
                assertThat(dev.jasper.app.palette.PaletteTestSupport.resultsSpec(owner.commandPalette().component())).isSameAs(spec);
                owner.newTab(DesktopTestSupport.HOME); assertThat(owner.commandPalette().isOpen()).isFalse();
            }
        });
    }
    @Test void themesAndStatusRefreshWhileOpenAndDisposalDetachesOverlay() throws Exception {
        DesktopTestSupport.edt(() -> {
            var owner = owner(new CommandHistory()); var root = install(owner);
            int listeners = root.getComponentListeners().length;
            owner.commandPalette().toggle(); dev.jasper.app.palette.PaletteTestSupport.query(owner.commandPalette().component()).setText("status bar");
            // "Status Bar Height…" matches too; follow the visibility toggle's own row.
            java.util.function.Supplier<String> toggle = () -> dev.jasper.app.palette.PaletteTestSupport.rows(owner.commandPalette().component())
                .stream().filter(row -> row.id().equals("view.status_bar")).findFirst().orElseThrow().title();
            assertThat(toggle.get()).isEqualTo("Hide Status Bar");
            owner.setStatusVisible(false);
            assertThat(toggle.get()).isEqualTo("Show Status Bar");
            owner.selectVariant(false); assertThat(owner.commandPalette().isOpen()).isTrue();
            var overlay = owner.commandPalette().component().getParent(); owner.close();
            assertThat(overlay.getParent()).isNull(); assertThat(root.getComponentListeners().length).isLessThan(listeners);
            assertThat(owner.commands().entries()).isEmpty();
        });
    }
    @Test void outsideClickIsCapturedThroughReleaseAcrossSurroundingChrome() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var owner = owner(new CommandHistory())) {
                var root = new JRootPane(); var wrapper = new JPanel(new BorderLayout());
                var title = new JButton("title chrome"); wrapper.add(title, BorderLayout.NORTH); wrapper.add(owner);
                root.setContentPane(wrapper); owner.installRootBindings(root); root.setSize(900,600); LayoutTestSupport.layoutTree(root);
                owner.commandPalette().toggle(); LayoutTestSupport.layoutTree(root);
                var overlay = (JComponent) owner.commandPalette().component().getParent();
                var point = SwingUtilities.convertPoint(title, 3, 3, overlay);
                assertThat(overlay.contains(point)).isTrue();
                for (int type : new int[]{MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED}) {
                    var event = new MouseEvent(overlay, type, 0, 0, point.x, point.y, 1, false, MouseEvent.BUTTON1);
                    overlay.dispatchEvent(event); assertThat(event.isConsumed()).isTrue();
                    assertThat(owner.commandPalette().isOpen()).isFalse();
                }
                assertThat(overlay.isVisible()).isFalse();
            }
        });
    }
    @Test void pressesInThePalettesEmptyAreasStopAtThePaletteAndKeepItOpen() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var owner = owner(new CommandHistory())) {
                var root = install(owner);
                var fake = new PaletteScopesTest.FakeScope();
                fake.rows = List.of(new PaletteRow("alpha", "Alpha", "a detail", null, null, true, null));
                owner.scopes().register(fake);
                // A settled card: while it drips open, only its shown part takes presses.
                owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 475, 425, 0));
                owner.commandPalette().open(fake.id());
                LayoutTestSupport.layoutTree(root);
                var palette = owner.commandPalette().component();
                var overlay = (JComponent) palette.getParent();

                var chip = dev.jasper.app.palette.PaletteTestSupport.chip(palette);
                var chipBounds = SwingUtilities.convertRectangle(chip.getParent(), chip.getBounds(), palette);
                var fieldRow = dev.jasper.app.palette.PaletteTestSupport.fieldRow(palette);
                var fieldRowBounds = SwingUtilities.convertRectangle(fieldRow.getParent(), fieldRow.getBounds(), palette);
                var hintBar = dev.jasper.app.palette.PaletteTestSupport.hintBar(palette);
                assertThat(dev.jasper.app.palette.PaletteTestSupport.hint(palette)).as("a detail is showing to click on").isEqualTo("a detail");

                // The scope chip, the hint bar's detail text and the field
                // row's padding: three places AWT could otherwise retarget a press straight to the
                // overlay behind the palette, dismissing it and losing the query.
                var points = List.of(
                    new Point(chipBounds.x + chipBounds.width / 2, chipBounds.y + chipBounds.height / 2),
                    new Point(hintBar.getX() + 10, hintBar.getY() + hintBar.getHeight() / 2),
                    new Point(fieldRowBounds.x + 2, fieldRowBounds.y + 2));

                for (Point point : points) {
                    var overlayPoint = SwingUtilities.convertPoint(palette, point, overlay);
                    Component deepest = SwingUtilities.getDeepestComponentAt(overlay, overlayPoint.x, overlayPoint.y);
                    // AWT retargets a press to the nearest ancestor that "wants" mouse events, i.e. has a listener.
                    Component target = deepest;
                    while (target != null && target.getMouseListeners().length == 0) target = target.getParent();
                    assertThat(target).as("nearest mouse-listening ancestor of %s", point).isNotSameAs(overlay);
                    assertThat(target == palette || SwingUtilities.isDescendingFrom(target, palette))
                        .as("point %s resolves inside the palette, not the overlay", point).isTrue();
                    var local = SwingUtilities.convertPoint(overlay, overlayPoint, target);
                    target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                        0, local.x, local.y, 1, false, MouseEvent.BUTTON1));
                    assertThat(owner.commandPalette().isOpen()).as("point %s keeps the palette open", point).isTrue();
                }

                // A control point clearly outside the card still dismisses (the existing outside-click
                // test covers this path in full; this only proves the fix above did not disable it).
                var outside = new Point(2, 2);
                assertThat(palette.getBounds().contains(SwingUtilities.convertPoint(overlay, outside, palette))).isFalse();
                overlay.dispatchEvent(new MouseEvent(overlay, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                    0, outside.x, outside.y, 1, false, MouseEvent.BUTTON1));
                assertThat(owner.commandPalette().isOpen()).isFalse();
            }
        });
    }

    @Test void lastWindowAndQuitDispatchAreRecordedBeforeDeferredHistoryClose(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        for (boolean quit : new boolean[]{false, true}) {
            CommandHistory[] history = new CommandHistory[1];
            var file = dir.resolve(quit ? "quit.toml" : "close.toml");
            DesktopTestSupport.edt(() -> {
                history[0] = new CommandHistory(file);
                var application = new JasperApplication(null,
                    DesktopTestSupport.launcher(new ArrayDeque<>()), history[0]);
                WindowContent[] content = new WindowContent[1];
                content[0] = new WindowContent(DesktopTestSupport.launcher(new ArrayDeque<>()), DesktopTestSupport.HOME,
                    path -> {}, () -> { content[0].close(); application.quit(); },
                    () -> { content[0].close(); ApplicationTestSupport.windowClosed(application, null); }, ThemeTestSupport.controller(true),
                    KeyBindings.defaults(true), history[0], true);
                install(content[0]); content[0].commandPalette().toggle();
                dev.jasper.app.palette.PaletteTestSupport.query(content[0].commandPalette().component()).setText(quit ? "quit" : "close tab");
                dev.jasper.app.palette.PaletteTestSupport.executeSelected(content[0].commandPalette().component());
                assertThat(history[0].recent()).containsExactly(quit ? "quit" : "close_tab");
                assertThat(history[0].closedFuture()).isNotDone();
                assertThat(application.windows().openTerminalWindow(DesktopTestSupport.HOME)).isEmpty();
                application.quit(); // Repeated shutdown requests retain the accepted dispatch.
            });
            history[0].closedFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(readCommands(file)).containsExactly(quit ? "quit" : "close_tab");
        }
    }

    @Test void failedActionReportsAndDoesNotRecord() throws Exception {
        DesktopTestSupport.edt(() -> {
            var logger = java.util.logging.Logger.getLogger(PaletteController.class.getName());
            boolean parents = logger.getUseParentHandlers(); logger.setUseParentHandlers(false);
            var records = new ArrayList<java.util.logging.LogRecord>();
            var handler = new java.util.logging.Handler() {
                public void publish(java.util.logging.LogRecord record) { records.add(record); }
                public void flush() {} public void close() {}
            };
            logger.addHandler(handler);
            try (var history = new CommandHistory(); var owner = owner(history)) {
                install(owner); var errors = new ArrayList<String>(); owner.onError = (title, message) -> errors.add(message);
                owner.commands().register(command("failing", () -> { throw new IllegalStateException("fixture"); }));
                owner.commandPalette().toggle(); dev.jasper.app.palette.PaletteTestSupport.query(owner.commandPalette().component()).setText("failing");
                dev.jasper.app.palette.PaletteTestSupport.executeSelected(owner.commandPalette().component());
                assertThat(errors).containsExactly("Could not run failing. See the application log for details.");
                assertThat(history.recent()).isEmpty(); assertThat(owner.commandPalette().isOpen()).isFalse();
                assertThat(records).hasSize(1); assertThat(records.getFirst().getThrown()).isInstanceOf(IllegalStateException.class);
            } finally { logger.removeHandler(handler); logger.setUseParentHandlers(parents); }
        });
    }

    @Test void refreshPreservesSelectionButEditingResetsItAndCloseDetachesListeners() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var history = new CommandHistory(); var owner = owner(history)) {
                var root = install(owner); int baseline = root.getLayeredPane().getComponentListeners().length;
                var first = command("custom.one", () -> {}); var second = command("custom.two", () -> {});
                owner.commands().register(first); owner.commands().register(second);
                owner.commandPalette().toggle(); var palette = owner.commandPalette().component();
                dev.jasper.app.palette.PaletteTestSupport.query(palette).setText("custom"); PaletteTestSupport.selectRelative(palette, 1);
                var selected = dev.jasper.app.palette.PaletteTestSupport.selectedRow(palette); first.action().putValue(Command.TITLE, "custom one");
                // Rows are rebuilt as fresh PaletteRow records on every refresh; only their
                // content (matched by row ID) is stable across an edit to an unrelated command.
                assertThat(dev.jasper.app.palette.PaletteTestSupport.selectedRow(palette)).isEqualTo(selected);
                dev.jasper.app.palette.PaletteTestSupport.query(palette).setText("custom "); assertThat(dev.jasper.app.palette.PaletteTestSupport.selectedRowIndex(palette)).isZero();
                long revision = dev.jasper.app.palette.PaletteTestSupport.revision(palette);
                owner.commandPalette().close(); history.record("custom.one"); first.action().setEnabled(false);
                assertThat(dev.jasper.app.palette.PaletteTestSupport.revision(palette)).as("closed: the list is never rebuilt").isEqualTo(revision);
                assertThat(root.getLayeredPane().getComponentListeners().length).isLessThan(baseline);
            }
        });
    }

    @Test void smallCardsKeepInputFixedAndSelectedResultsScrollable() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var owner = owner(new CommandHistory())) {
                var root = install(owner);
                for (int i = 0; i < 5; i++) owner.commands().register(command("custom." + i, () -> {}));
                owner.commandPalette().toggle(); var palette = owner.commandPalette().component();
                dev.jasper.app.palette.PaletteTestSupport.query(palette).setText("custom"); root.setSize(350, 210); LayoutTestSupport.layoutTree(root);
                root.dispatchEvent(new ComponentEvent(root, ComponentEvent.COMPONENT_RESIZED)); LayoutTestSupport.layoutTree(root);
                assertThat(palette.getWidth()).isLessThanOrEqualTo(318);
                assertThat(dev.jasper.app.palette.PaletteTestSupport.fieldRow(palette).getHeight()).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(m(dev.jasper.ui.metrics.DesignMetrics.Role.PALETTE_FIELD_HEIGHT)));
                PaletteTestSupport.selectRelative(palette, 4); LayoutTestSupport.layoutTree(root);
                assertThat(dev.jasper.app.palette.PaletteTestSupport.resultsCanvas(palette).getVisibleRect().intersects(dev.jasper.app.palette.PaletteTestSupport.rowBounds(palette, 4))).isTrue();
                assertThat(palette.getHeight()).isLessThan(palette.getPreferredSize().height);
            }
        });
    }

    @Test void queryChangeRevealsResetSelectionInScrolledShortCard() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var owner = owner(new CommandHistory())) {
                var root = install(owner);
                for (int i = 0; i < 5; i++) owner.commands().register(command("custom." + i, () -> {}));
                owner.commandPalette().toggle(); var palette = owner.commandPalette().component();
                dev.jasper.app.palette.PaletteTestSupport.query(palette).setText("custom"); root.setSize(350, 210); LayoutTestSupport.layoutTree(root);
                root.dispatchEvent(new ComponentEvent(root, ComponentEvent.COMPONENT_RESIZED)); LayoutTestSupport.layoutTree(root);
                PaletteTestSupport.selectRelative(palette, 4); LayoutTestSupport.layoutTree(root);

                dev.jasper.app.palette.PaletteTestSupport.query(palette).setText("custom "); LayoutTestSupport.layoutTree(root);

                assertThat(dev.jasper.app.palette.PaletteTestSupport.selectedRowIndex(palette)).isZero();
                assertThat(dev.jasper.app.palette.PaletteTestSupport.resultsCanvas(palette).getVisibleRect().contains(
                    dev.jasper.app.palette.PaletteTestSupport.rowBounds(palette, 0))).isTrue();
            }
        });
    }

    @Test @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void delayedReadinessPreservesOriginThenPaneChangeDismissesAndZoomTitleUpdates() throws Exception {
        var pending = new ArrayDeque<Runnable>(); WindowContent[] owner = new WindowContent[1]; JRootPane[] root = new JRootPane[1];
        try {
            DesktopTestSupport.edt(() -> {
                owner[0] = DesktopTestSupport.content(DesktopTestSupport.launcher(pending)); root[0] = install(owner[0]);
                owner[0].commandPalette().toggle(); dev.jasper.app.palette.PaletteTestSupport.query(owner[0].commandPalette().component()).setText("split");
            });
            pending.remove().run(); DesktopTestSupport.until(() -> owner[0].currentPane().view() != null);
            DesktopTestSupport.edt(() -> {
                var content = owner[0]; var origin = content.currentPane();
                assertThat(content.commandPalette().isOpen()).isTrue(); assertThat(origin.allowLaunchFocus.getAsBoolean()).isFalse();
                assertThat(dev.jasper.app.palette.PaletteTestSupport.rowAt(content.commandPalette().component(), 0).id()).startsWith("split_");
                LayoutTestSupport.layoutTree(root[0]); var before = origin.view().getBounds();
                content.commandPalette().dismiss(); assertThat(origin.allowLaunchFocus.getAsBoolean()).isTrue();
                content.commandPalette().toggle(); LayoutTestSupport.layoutTree(root[0]); assertThat(origin.view().getBounds()).isEqualTo(before);
                content.split(dev.jasper.app.layout.Side.RIGHT); assertThat(content.commandPalette().isOpen()).isFalse();
                content.commandPalette().toggle(); content.selectTab(content.terminalOf(origin)); assertThat(content.commandPalette().isOpen()).isFalse();
                content.commandPalette().toggle(); dev.jasper.app.palette.PaletteTestSupport.query(content.commandPalette().component()).setText("zoom");
                content.toggleMaximize();
                assertThat(content.commandPalette().isOpen()).isTrue();
                assertThat(content.action(ActionId.MAXIMIZE_GROUP).getValue(Command.TITLE)).isEqualTo("Restore Group");
                content.closeTab(content.terminalOf(origin)); assertThat(content.commandPalette().isOpen()).isFalse();
            });
        } finally { DesktopTestSupport.edt(() -> { if (owner[0] != null) owner[0].close(); }); }
    }

    @Test @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void priorFocusRestoresSynchronouslyBeforeFindAndNeverAgain() throws Exception {
        var pending = new ArrayDeque<Runnable>(); WindowContent[] owner = new WindowContent[1];
        var requests = new AtomicInteger(); var ordered = new ArrayList<String>();
        var previousManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        JTextField previous = new JTextField() {
            @Override public boolean requestFocusInWindow() { requests.incrementAndGet(); ordered.add("restore"); return true; }
        };
        previous.removeCaretListener((javax.swing.event.CaretListener) previous.getAccessibleContext());
        try {
            DesktopTestSupport.edt(() -> {
                owner[0] = DesktopTestSupport.content(DesktopTestSupport.launcher(pending)); install(owner[0]);
                owner[0].toolbar().add(previous);
                var query = dev.jasper.app.palette.PaletteTestSupport.query(owner[0].commandPalette().component());
                query.removeCaretListener((javax.swing.event.CaretListener) query.getAccessibleContext());
                var host = new JPanel(); host.add(SwingUtilities.getRootPane(owner[0])); host.addNotify();
                KeyboardFocusManager.setCurrentKeyboardFocusManager(new DefaultKeyboardFocusManager() {
                    @Override public Component getFocusOwner() { return previous; }
                });
            });
            pending.remove().run(); DesktopTestSupport.until(() -> owner[0].currentPane().view() != null);
            DesktopTestSupport.edt(() -> {
                assertThat(SwingUtilities.isDescendingFrom(previous, owner[0])).isTrue();
                // A lightweight component can have a peer in headless tests without creating a native window.
                assertThat(previous.isShowing()).isTrue();
                owner[0].commandPalette().toggle();
                owner[0].commands().register(command("find.fixture", () -> {
                    ordered.add("find"); owner[0].action(ActionId.FIND).actionPerformed(new ActionEvent(owner[0], 0, "find"));
                }));
                dev.jasper.app.palette.PaletteTestSupport.query(owner[0].commandPalette().component()).setText("find.fixture");
                dev.jasper.app.palette.PaletteTestSupport.executeSelected(owner[0].commandPalette().component());
                assertThat(ordered).containsExactly("restore", "find");
                assertThat(owner[0].currentPane().findBar().isVisible()).isTrue();
                assertThat(requests.get()).isEqualTo(1);
            });
            DesktopTestSupport.edt(() -> assertThat(requests.get()).isEqualTo(1));
        } finally {
            DesktopTestSupport.edt(() -> {
                KeyboardFocusManager.setCurrentKeyboardFocusManager(previousManager);
                if (owner[0] != null) { SwingUtilities.getRootPane(owner[0]).getParent().removeNotify(); owner[0].close(); }
            });
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void paneTransitionNeverRestoresTheOldPanesFocus(boolean navigate) throws Exception {
        var pending = new ArrayDeque<Runnable>(); WindowContent[] owner = new WindowContent[1];
        var previousManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        var oldRequests = new AtomicInteger(); JPanel[] host = new JPanel[1];
        try {
            DesktopTestSupport.edt(() -> {
                owner[0] = DesktopTestSupport.content(DesktopTestSupport.launcher(pending)); install(owner[0]);
            });
            pending.remove().run(); DesktopTestSupport.until(() -> owner[0].currentPane().view() != null);
            DesktopTestSupport.edt(() -> owner[0].split(dev.jasper.app.layout.Side.RIGHT));
            pending.remove().run(); DesktopTestSupport.until(() -> owner[0].currentPane().view() != null);
            DesktopTestSupport.edt(() -> {
                var window = owner[0];
                var original = window.terminalTabs().getFirst().pane(); var destination = window.terminalTabs().getLast().pane();
                // Record requests on a real focusable child of the old pane. Like TerminalPane's
                // focus listener, accepting a stale request would select that pane again.
                var prior = new JTextField() {
                    @Override public boolean requestFocusInWindow() {
                        oldRequests.incrementAndGet(); window.selectTab(window.terminalOf(original)); return true;
                    }
                };
                prior.removeCaretListener((javax.swing.event.CaretListener) prior.getAccessibleContext());
                // Accessible text location callbacks require a native screen peer, which this
                // lightweight fixture deliberately does not create.
                for (var listener : prior.getComponentListeners()) prior.removeComponentListener(listener);
                original.add(prior, BorderLayout.NORTH);
                var query = dev.jasper.app.palette.PaletteTestSupport.query(owner[0].commandPalette().component());
                query.removeCaretListener((javax.swing.event.CaretListener) query.getAccessibleContext());
                for (var listener : query.getComponentListeners()) query.removeComponentListener(listener);
                var root = SwingUtilities.getRootPane(owner[0]);
                host[0] = new JPanel(); host[0].add(root); host[0].addNotify(); LayoutTestSupport.layoutTree(root);
                assertThat(original.isShowing()).isTrue(); assertThat(destination.isShowing()).isTrue();
                assertThat(prior.isShowing()).isTrue(); assertThat(original.view().isShowing()).isTrue();
                KeyboardFocusManager.setCurrentKeyboardFocusManager(new DefaultKeyboardFocusManager() {
                    @Override public Component getFocusOwner() { return prior; }
                });
                window.selectTab(window.terminalOf(original)); owner[0].commandPalette().toggle();
                if (navigate) window.focusGroup(dev.jasper.app.layout.Side.RIGHT); else window.selectTab(window.terminalOf(destination));
                assertThat(owner[0].commandPalette().isOpen()).isFalse();
                assertThat(oldRequests.get()).isZero();
                assertThat(window.currentPane()).isSameAs(destination);
                // Cancellation with a still-valid origin must retain normal synchronous restoration.
                window.selectTab(window.terminalOf(original)); owner[0].commandPalette().toggle(); owner[0].commandPalette().dismiss();
                assertThat(oldRequests.get()).isEqualTo(1);
            });
            DesktopTestSupport.edt(() -> assertThat(oldRequests.get()).isEqualTo(1));
        } finally {
            DesktopTestSupport.edt(() -> {
                KeyboardFocusManager.setCurrentKeyboardFocusManager(previousManager);
                if (host[0] != null) host[0].removeNotify();
                if (owner[0] != null) owner[0].close();
            });
        }
    }


    /** The saved recents, decoded the way the app writes them (a small TOML array of ids). */
    static java.util.List<String> readCommands(java.nio.file.Path file) throws java.io.IOException {
        return dev.jasper.app.commands.CommandHistoryFile.read(file);
    }
}
