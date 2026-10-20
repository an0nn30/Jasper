package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.contributions.ActionEntry;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.contributions.PanelEntry;
import dev.jasper.app.contributions.PanelSite;
import dev.jasper.app.dock.DockGeometry;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

class WindowPanelsTest {
    @TempDir Path dir;

    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); edt(() -> ThemeTestSupport.controller(true)); }

    private static WindowContent window() { return DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>())); }

    private static void toggle(Contributions model, WindowContent owner, String id) {
        model.requestPanel(new Contributions.PanelRequest(owner.id(), id, Contributions.PanelRequest.Op.TOGGLE));
    }

    private static List<String> tips(DockStripe stripe) { return stripe.buttons().stream().map(AbstractButton::getToolTipText).toList(); }

    private static Rectangle in(JComponent root, Component component) {
        return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), root);
    }

    private static void mouse(Component target, int id, int x, int y) {
        int modifiers = id == MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON1_DOWN_MASK;
        int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
        target.dispatchEvent(new MouseEvent(target, id, System.currentTimeMillis(), modifiers, x, y, 1, false, button));
    }

    @Test void anUnconnectedOrEmptyWindowHasNoStripesAndTheOldLayout() throws Exception {
        edt(() -> {
            WindowContent owner = window();
            assertThat(owner.leftStripe().isVisible()).isFalse();
            assertThat(owner.rightStripe().isVisible()).isFalse();
            owner.connectContributions(new Contributions());
            assertThat(owner.leftStripe().isVisible()).isFalse();
            assertThat(owner.rightStripe().isVisible()).isFalse();
            assertThat(owner.area().content(Slot.LEFT_TOP)).isNull();
        });
    }

    @Test void panelsAreLazyPerWindowToggleFromTheStripeAndShareASlotOneAtATime() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.inMemory());
            List<PanelSite> sites = new ArrayList<>();
            List<String> events = new ArrayList<>();
            var hostsView = new JLabel("hosts");
            model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), Slot.LEFT_TOP, site -> {
                sites.add(site);
                site.onVisibility(visible -> events.add("hosts:" + visible));
                site.onClosed(() -> events.add("hosts:closed"));
                return hostsView;
            });
            PanelEntry files = model.addPanel("dev.x.files", "Files", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("files"));
            assertThat(owner.leftStripe().isVisible()).isTrue();
            assertThat(owner.rightStripe().isVisible()).as("an empty right stripe hides").isFalse();
            assertThat(tips(owner.leftStripe())).containsExactly("Hosts", "Files");
            assertThat(owner.rightStripe().buttons()).isEmpty();
            assertThat(sites).as("no instance until first shown").isEmpty();

            owner.leftStripe().buttons().get(0).doClick();
            assertThat(sites).singleElement().satisfies(site -> {
                assertThat(site.windowId()).isEqualTo(owner.id());
                assertThat(site.visible()).isTrue();
            });
            assertThat(SwingUtilities.isDescendingFrom(hostsView, owner)).isTrue();
            assertThat(owner.area().content(Slot.LEFT_TOP)).isSameAs(hostsView);
            assertThat(owner.area().headerOf(Slot.LEFT_TOP).titleLabel().getText()).isEqualTo("Hosts");
            assertThat(owner.leftStripe().buttons().get(0).isSelected()).isTrue();

            toggle(model, owner, "dev.x.files");
            assertThat(owner.area().content(Slot.LEFT_TOP)).isNotSameAs(hostsView);
            assertThat(owner.leftStripe().buttons()).extracting(AbstractButton::isSelected).containsExactly(false, true);
            toggle(model, owner, "dev.x.hosts");
            assertThat(sites).as("the instance is reused").hasSize(1);
            sites.get(0).hide();
            assertThat(owner.area().content(Slot.LEFT_TOP)).isNull();
            assertThat(events).containsExactly("hosts:true", "hosts:false", "hosts:true", "hosts:false");

            model.requestPanel(new Contributions.PanelRequest(java.util.UUID.randomUUID(), "dev.x.hosts", Contributions.PanelRequest.Op.SHOW));
            assertThat(owner.area().content(Slot.LEFT_TOP)).as("another window's request").isNull();

            files.close();
            assertThat(owner.leftStripe().buttons()).hasSize(1);
        });
    }

    /** Plan 12 Rulings 18: under flush frames the View toggle action activates the panel it opens; a plugin's toggle does not. */
    @Test void underFlushFramesTheViewToggleActivatesThePanelAndAPluginsToggleDoesNot() throws Exception {
        edt(() -> {
            var themes = ThemeTestSupport.controller(true);
            var owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()), themes, DockChromeTest.classicDock());
            var model = new Contributions();
            owner.connectContributions(model, UiState.inMemory());
            model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("hosts"));
            model.action("dev.x.hosts.toggle").orElseThrow().invoke(new Contributions.Invocation(owner.id(), Optional.empty()));
            assertThat(owner.area().content(Slot.LEFT_TOP)).isNotNull();
            assertThat(owner.area().focusedSlot()).as("the View toggle is a user gesture").hasValue(Slot.LEFT_TOP);
            toggle(model, owner, "dev.x.hosts");
            assertThat(owner.area().content(Slot.LEFT_TOP)).isNull();
            owner.area().focusMoved(null);
            toggle(model, owner, "dev.x.hosts");
            assertThat(owner.area().content(Slot.LEFT_TOP)).isNotNull();
            assertThat(owner.area().focusedSlot()).as("a plugin's programmatic toggle").isEmpty();
        });
    }

    private static void request(Contributions model, WindowContent owner, String id, Contributions.PanelRequest.Op op) {
        model.requestPanel(new Contributions.PanelRequest(owner.id(), id, op));
    }

    /** A connected window with one panel, under flush frames (Enterprise's classic dock) or the default look's standard ones. */
    private static WindowContent framed(Contributions model, boolean flush) {
        var owner = flush
            ? DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()), ThemeTestSupport.controller(true), DockChromeTest.classicDock())
            : window();
        owner.connectContributions(model, UiState.inMemory());
        model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("hosts"));
        return owner;
    }

    /** Plan 15b Rulings 1: a plugin's own toggle action arrives as an activating toggle, which activates what it shows. */
    @Test void aPanelsActivatingToggleUnderFlushFramesActivatesIt() throws Exception {
        edt(() -> {
            var model = new Contributions();
            var owner = framed(model, true);
            request(model, owner, "dev.x.hosts", Contributions.PanelRequest.Op.ACTIVATING_TOGGLE);
            assertThat(owner.area().content(Slot.LEFT_TOP)).isNotNull();
            assertThat(owner.area().focusedSlot()).hasValue(Slot.LEFT_TOP);
        });
    }

    /** An activating toggle of the active tool window hides it and hands focus back, as a plain toggle does. */
    @Test void anActivatingToggleThatHidesThePanelActivatesNothing() throws Exception {
        edt(() -> {
            var model = new Contributions();
            var owner = framed(model, true);
            List<String> events = new ArrayList<>();
            // The selected tab is hosted content, so handing focus back runs this instead of focusing a terminal.
            model.content().open("dev.x", "doc", "Doc", owner.id(), new JPanel()).focusTarget(() -> events.add("focus"));
            request(model, owner, "dev.x.hosts", Contributions.PanelRequest.Op.ACTIVATING_TOGGLE);
            assertThat(owner.area().content(Slot.LEFT_TOP)).isNotNull();
            assertThat(owner.area().focusedSlot()).hasValue(Slot.LEFT_TOP);
            events.clear();
            request(model, owner, "dev.x.hosts", Contributions.PanelRequest.Op.ACTIVATING_TOGGLE);
            assertThat(owner.area().content(Slot.LEFT_TOP)).isNull();
            assertThat(events).as("focus is handed back").contains("focus");
            assertThat(owner.area().focusedSlot()).isEmpty();
        });
    }

    /** As IntelliJ's ActivateToolWindowAction: an activating toggle of a visible tool window that is not active activates it. */
    @Test void anActivatingToggleOfAVisibleInactivePanelActivatesIt() throws Exception {
        edt(() -> {
            var model = new Contributions();
            var owner = framed(model, true);
            toggle(model, owner, "dev.x.hosts");
            assertThat(owner.area().content(Slot.LEFT_TOP)).isNotNull();
            assertThat(owner.area().focusedSlot()).isEmpty();
            request(model, owner, "dev.x.hosts", Contributions.PanelRequest.Op.ACTIVATING_TOGGLE);
            assertThat(owner.area().content(Slot.LEFT_TOP)).as("it stays open").isNotNull();
            assertThat(owner.area().focusedSlot()).hasValue(Slot.LEFT_TOP);
            request(model, owner, "dev.x.hosts", Contributions.PanelRequest.Op.ACTIVATING_TOGGLE);
            assertThat(owner.area().content(Slot.LEFT_TOP)).as("the active one hides").isNull();
        });
    }

    @Test void underStandardFramesAnActivatingToggleIsAPlainToggle() throws Exception {
        edt(() -> {
            var model = new Contributions();
            var owner = framed(model, false);
            request(model, owner, "dev.x.hosts", Contributions.PanelRequest.Op.ACTIVATING_TOGGLE);
            assertThat(owner.area().content(Slot.LEFT_TOP)).isNotNull();
            assertThat(owner.area().focusedSlot()).isEmpty();
            request(model, owner, "dev.x.hosts", Contributions.PanelRequest.Op.ACTIVATING_TOGGLE);
            assertThat(owner.area().content(Slot.LEFT_TOP)).isNull();
        });
    }

    /** Plan 15b Rulings 1: a show by the panel's own toggle action (Transfers' after its first open) activates it, and never hides. */
    @Test void anActivatingShowUnderFlushFramesActivatesThePanel() throws Exception {
        edt(() -> {
            var model = new Contributions();
            var owner = framed(model, true);
            request(model, owner, "dev.x.hosts", Contributions.PanelRequest.Op.ACTIVATING_SHOW);
            assertThat(owner.area().content(Slot.LEFT_TOP)).isNotNull();
            assertThat(owner.area().focusedSlot()).hasValue(Slot.LEFT_TOP);
            owner.area().focusMoved(null);
            request(model, owner, "dev.x.hosts", Contributions.PanelRequest.Op.ACTIVATING_SHOW);
            assertThat(owner.area().content(Slot.LEFT_TOP)).as("a show never hides").isNotNull();
            assertThat(owner.area().focusedSlot()).hasValue(Slot.LEFT_TOP);
        });
    }

    @Test void underStandardFramesAnActivatingShowIsAPlainShow() throws Exception {
        edt(() -> {
            var model = new Contributions();
            var owner = framed(model, false);
            request(model, owner, "dev.x.hosts", Contributions.PanelRequest.Op.ACTIVATING_SHOW);
            assertThat(owner.area().content(Slot.LEFT_TOP)).isNotNull();
            assertThat(owner.area().focusedSlot()).isEmpty();
        });
    }

    /**
     * Final review: a window toggled in by a user gesture in place of the active one keeps the activation. Handing focus
     * back to the terminal first would post focus events that land after the activation and undo it.
     */
    @Test void underFlushFramesAnActivatingToggleThatReplacesAWindowDoesNotHandFocusBack() throws Exception {
        edt(() -> {
            var themes = ThemeTestSupport.controller(true);
            var owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()), themes, DockChromeTest.classicDock());
            var model = new Contributions();
            owner.connectContributions(model, UiState.inMemory());
            List<String> events = new ArrayList<>();
            // The selected tab is hosted content, so handing focus back runs this instead of focusing a terminal.
            model.content().open("dev.x", "doc", "Doc", owner.id(), new JPanel()).focusTarget(() -> events.add("focus"));
            for (String name : List.of("log", "transfers"))
                model.addPanel("dev.x." + name, name, new ImageIcon(), Slot.LEFT_TOP, site -> {
                    site.onVisibility(visible -> events.add(name + ":" + visible));
                    return new JLabel(name);
                });
            model.action("dev.x.log.toggle").orElseThrow().invoke(new Contributions.Invocation(owner.id(), Optional.empty()));
            assertThat(owner.area().focusedSlot()).hasValue(Slot.LEFT_TOP);
            events.clear();
            owner.leftStripe().buttons().get(1).doClick();
            assertThat(owner.area().content(Slot.LEFT_TOP)).isInstanceOfSatisfying(JLabel.class, label -> assertThat(label.getText()).isEqualTo("transfers"));
            assertThat(events).as("no hand-back to the terminal").containsExactly("log:false", "transfers:true");
            assertThat(owner.area().focusedSlot()).hasValue(Slot.LEFT_TOP);
            events.clear();
            toggle(model, owner, "dev.x.log");
            assertThat(events).as("a plugin's toggle still hands focus back").containsExactly("transfers:false", "focus", "log:true");
        });
    }

    @Test void aSplitPanelOpensUnderTheFirstHalfAndBothShowAtOnce() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.inMemory());
            var sessions = new JLabel("sessions"); var sftp = new JLabel("sftp");
            model.addPanel("dev.x.sessions", "Sessions", new ImageIcon(), Slot.LEFT_TOP, site -> sessions);
            model.addPanel("dev.x.sftp", "SFTP", new ImageIcon(), Slot.LEFT_BOTTOM, site -> sftp);
            toggle(model, owner, "dev.x.sessions");
            toggle(model, owner, "dev.x.sftp");
            assertThat(owner.area().content(Slot.LEFT_TOP)).isSameAs(sessions);
            assertThat(owner.area().content(Slot.LEFT_BOTTOM)).isSameAs(sftp);
            owner.setSize(1200, 800);
            LayoutTestSupport.layoutTree(owner);
            Rectangle upper = in(owner, sessions.getParent()), lower = in(owner, sftp.getParent());
            assertThat(lower.x).isEqualTo(upper.x);
            assertThat(lower.width).isEqualTo(upper.width);
            assertThat(lower.y).as("the second half sits under the first, past the divider").isEqualTo(upper.y + upper.height + UIScale.scale(6));
            assertThat(owner.leftStripe().buttons()).extracting(AbstractButton::isSelected).containsExactly(true, true);
            assertThat(Arrays.stream(owner.leftStripe().getComponents()).filter(JSeparator.class::isInstance))
                .as("the second group sits under a separator").hasSize(1);
        });
    }

    @Test void theStripeMenuMovesAWindowToAnyOfTheSixSlotsAndANewWindowRestoresIt() throws Exception {
        edt(() -> {
            var model = new Contributions();
            Path file = dir.resolve("ui-state.toml");
            WindowContent first = window();
            first.connectContributions(model, UiState.load(file));
            List<String> created = new ArrayList<>();
            List<String> visibility = new ArrayList<>();
            model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), Slot.LEFT_TOP, site -> {
                created.add("hosts");
                site.onVisibility(visible -> visibility.add("hosts:" + visible));
                return new JLabel("hosts");
            });
            toggle(model, first, "dev.x.hosts");
            var menu = first.leftStripe().buttons().getFirst().getComponentPopupMenu();
            ToolWindowHeaderTest.item(menu, "Right Bottom").doClick();
            assertThat(first.area().content(Slot.LEFT_TOP)).isNull();
            assertThat(first.area().content(Slot.RIGHT_BOTTOM)).as("a move keeps the window showing").isNotNull();
            assertThat(first.leftStripe().buttons()).isEmpty();
            assertThat(tips(first.rightStripe())).containsExactly("Hosts");
            assertThat(visibility).as("a move is not a change of visibility").containsExactly("hosts:true");
            assertThat(UiState.load(file).panel("dev.x.hosts")).hasValue(new UiState.Panel(Slot.RIGHT_BOTTOM, 0, true));

            WindowContent second = window();
            second.connectContributions(model, UiState.load(file));
            assertThat(second.area().content(Slot.RIGHT_BOTTOM)).as("restored visible in the right side's lower half").isNotNull();
            assertThat(created).hasSize(2);
        });
    }

    @Test void theHeaderMovesAndHidesItsWindow() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.inMemory());
            var view = new JTextField("hosts");
            model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), Slot.LEFT_TOP, site -> view);
            toggle(model, owner, "dev.x.hosts");
            ToolWindowHeaderTest.item(owner.area().headerOf(Slot.LEFT_TOP).menu(), "Bottom Left").doClick();
            assertThat(owner.area().content(Slot.BOTTOM_LEFT)).isSameAs(view);
            assertThat(owner.area().headerOf(Slot.LEFT_TOP)).isNull();
            owner.area().headerOf(Slot.BOTTOM_LEFT).hideButton().doClick();
            assertThat(owner.area().content(Slot.BOTTOM_LEFT)).isNull();
            assertThat(owner.dock().visible("dev.x.hosts")).isFalse();
            assertThat(owner.leftStripe().buttons().getFirst().isSelected()).isFalse();
            assertThat(view.getParent()).as("hidden content leaves the tree").isNull();
        });
    }

    /** PanelHost.setHeader (SDK 0.14.2) on a kit panel: the window's borrowed kit header shows it and releases it once. */
    @Test void aKitPanelsHeaderMetadataReachesItsBorrowedHeaderAndIsReleasedWithTheInstance() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.inMemory());
            var theme = dev.jasper.app.appearance.ThemeTestSupport.generated(true);
            try (var bindings = new dev.jasper.app.appearance.AppearanceBindings(new dev.jasper.ui.theme.ThemePair(theme, theme),
                    failure -> { throw new AssertionError(failure); }, () -> false)) {
                var calls = new ArrayList<String>();
                var sites = new ArrayList<PanelSite>();
                var views = new ArrayList<dev.jasper.ui.swing.KitView>();
                var icon = new ImageIcon();
                PanelEntry entry = model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), Slot.LEFT_TOP, site -> {
                    sites.add(site);
                    site.setHeader(new dev.jasper.app.contributions.PanelHeaderState("3", false, List.of(
                        new dev.jasper.app.contributions.PanelHeaderState.Action("New session", icon, () -> calls.add("new")))));
                    var view = new dev.jasper.ui.swing.KitView(bindings.context(dev.jasper.ui.theme.ThemeRoute.CHROME),
                        new dev.jasper.ui.model.ViewSpec(new dev.jasper.ui.model.Nodes.Panel("hosts", dev.jasper.ui.model.ControlState.READY,
                            new dev.jasper.ui.model.Nodes.Header("hosts-header", dev.jasper.ui.model.ControlState.READY, "Hosts",
                                java.util.OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of()),
                            Optional.empty(), new dev.jasper.ui.model.Nodes.Text("body", dev.jasper.ui.model.ControlState.READY, "Body",
                                dev.jasper.ui.model.Nodes.TextRole.BODY), Optional.empty())), event -> {});
                    site.onClosed(view::close);
                    views.add(view);
                    return view;
                });
                toggle(model, owner, "dev.x.hosts");
                var header = views.getFirst().panelHeader("hosts");
                assertThat(owner.area().headerOf(Slot.LEFT_TOP)).isNull();
                assertThat(names(header)).containsExactly("New session", "Options", "Hide");
                assertThat(labels(header)).contains("Hosts", "3");
                sites.getFirst().setHeader(new dev.jasper.app.contributions.PanelHeaderState("4", true, List.of(
                    new dev.jasper.app.contributions.PanelHeaderState.Action("New session", icon, () -> calls.add("newer")))));
                assertThat(labels(header)).contains("4").doesNotContain("3");
                var created = button(header, "New session");
                created.doClick();
                assertThat(calls).containsExactly("newer");
                toggle(model, owner, "dev.x.hosts");
                assertThat(names(header)).as("hidden: released").isEmpty();
                toggle(model, owner, "dev.x.hosts");
                assertThat(names(header)).as("shown again with the retained metadata").containsExactly("New session", "Options", "Hide");
                assertThat(labels(header)).contains("4");
                var shown = button(header, "New session");
                entry.close();
                assertThat(sites).hasSize(1);
                assertThat(shown.getActionListeners()).as("closing the instance releases the header actions").isEmpty();
                shown.doClick();
                assertThat(calls).containsExactly("newer");
            }
        });
    }

    private static List<AbstractButton> headerButtons(java.awt.Container root) {
        var result = new ArrayList<AbstractButton>();
        for (var child : root.getComponents()) {
            if (child instanceof AbstractButton button) result.add(button);
            else if (child instanceof java.awt.Container nested) result.addAll(headerButtons(nested));
        }
        return result;
    }

    private static List<String> names(java.awt.Container root) {
        return headerButtons(root).stream().map(b -> b.getAccessibleContext().getAccessibleName()).toList();
    }

    private static AbstractButton button(java.awt.Container root, String name) {
        return headerButtons(root).stream().filter(b -> name.equals(b.getAccessibleContext().getAccessibleName())).findFirst().orElseThrow();
    }

    private static List<String> labels(java.awt.Container root) {
        var result = new ArrayList<String>();
        for (var child : root.getComponents()) {
            if (child instanceof JLabel label && label.isVisible() && label.getText() != null && !label.getText().isEmpty()) result.add(label.getText());
            if (child instanceof java.awt.Container nested && child.isVisible()) result.addAll(labels(nested));
        }
        return result;
    }

    @Test void aFailedFactoryShowsAPlaceholderAndRemovalOrCloseNotifiesTheInstance() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.inMemory());
            List<String> events = new ArrayList<>();
            model.addPanel("dev.x.broken", "Broken", new ImageIcon(), Slot.BOTTOM_LEFT, site -> null);
            PanelEntry live = model.addPanel("dev.x.live", "Live", new ImageIcon(), Slot.RIGHT_TOP, site -> {
                site.onClosed(() -> events.add("closed"));
                return new JLabel("live");
            });
            toggle(model, owner, "dev.x.broken");
            JComponent placeholder = owner.area().content(Slot.BOTTOM_LEFT);
            assertThat(placeholder).isInstanceOf(JLabel.class);
            assertThat(((JLabel) placeholder).getText()).contains("could not be loaded");

            toggle(model, owner, "dev.x.live");
            live.close();
            assertThat(owner.area().content(Slot.RIGHT_TOP)).isNull();
            assertThat(owner.rightStripe().buttons()).isEmpty();
            assertThat(events).containsExactly("closed");
        });
    }

    @Test void aThrowingFactoryShowsAPlaceholderAndLogsAWarningNamingThePanel() throws Exception {
        var logger = java.util.logging.Logger.getLogger(WindowContributions.class.getName());
        var previousLevel = logger.getLevel();
        boolean previousUseParentHandlers = logger.getUseParentHandlers();
        List<java.util.logging.LogRecord> records = new java.util.concurrent.CopyOnWriteArrayList<>();
        var handler = new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        logger.setLevel(java.util.logging.Level.ALL);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        try {
            edt(() -> {
                var model = new Contributions();
                WindowContent owner = window();
                owner.connectContributions(model, UiState.inMemory());
                model.addPanel("dev.x.boom", "Boom", new ImageIcon(), Slot.RIGHT_TOP, site -> { throw new IllegalStateException("boom"); });
                toggle(model, owner, "dev.x.boom");
                assertThat(owner.area().content(Slot.RIGHT_TOP)).isInstanceOfSatisfying(JLabel.class,
                    placeholder -> assertThat(placeholder.getText()).contains("could not be loaded"));
                assertThat(owner.dock().visible("dev.x.boom")).as("the placeholder is a window like any other").isTrue();
            });
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
        }
        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.getLevel()).isEqualTo(java.util.logging.Level.WARNING);
            assertThat(record.getMessage()).contains("dev.x.boom");
            assertThat(record.getThrown()).isInstanceOf(IllegalStateException.class).hasMessage("boom");
        });
    }

    @Test void focusReturnsToTheTerminalBeforeAReplacingWindowHearsItShows() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.inMemory());
            List<String> events = new ArrayList<>();
            // The selected tab is hosted content, so focusing the selection runs this instead of focusing a terminal.
            var doc = model.content().open("dev.x", "doc", "Doc", owner.id(), new JPanel());
            doc.focusTarget(() -> events.add("focus"));
            for (String name : List.of("a", "b"))
                model.addPanel("dev.x." + name, name.toUpperCase(java.util.Locale.ROOT), new ImageIcon(), Slot.LEFT_TOP, site -> {
                    site.onVisibility(visible -> events.add(name + ":" + visible));
                    return new JLabel(name);
                });
            toggle(model, owner, "dev.x.a");
            events.clear();
            toggle(model, owner, "dev.x.b");
            assertThat(events).as("a window that takes focus when shown keeps it").containsExactly("a:false", "focus", "b:true");
            events.clear();
            toggle(model, owner, "dev.x.b");
            assertThat(events).containsExactly("b:false", "focus");
        });
    }

    @Test void aFactoryThatHidesItsOwnWindowHearsNothing() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.inMemory());
            List<String> events = new ArrayList<>();
            List<PanelSite> sites = new ArrayList<>();
            model.addPanel("dev.x.shy", "Shy", new ImageIcon(), Slot.LEFT_TOP, site -> {
                sites.add(site);
                site.onVisibility(visible -> events.add("shy:" + visible));
                site.hide();
                return new JLabel("shy");
            });
            toggle(model, owner, "dev.x.shy");
            assertThat(events).as("it never showed").isEmpty();
            assertThat(sites).singleElement().satisfies(site -> assertThat(site.visible()).isFalse());
            assertThat(owner.area().content(Slot.LEFT_TOP)).isNull();
        });
    }

    @Test void aWindowThatShowsItselfAgainWhenReplacedHearsOnlyWhatIsTrue() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.inMemory());
            List<String> events = new ArrayList<>();
            var once = new java.util.concurrent.atomic.AtomicBoolean(true);
            model.addPanel("dev.x.a", "A", new ImageIcon(), Slot.LEFT_TOP, site -> {
                site.onVisibility(visible -> {
                    events.add("a:" + visible);
                    if (!visible && once.getAndSet(false)) site.show();
                });
                return new JLabel("a");
            });
            model.addPanel("dev.x.b", "B", new ImageIcon(), Slot.LEFT_TOP, site -> {
                site.onVisibility(visible -> events.add("b:" + visible));
                return new JLabel("b");
            });
            toggle(model, owner, "dev.x.a");
            events.clear();
            toggle(model, owner, "dev.x.b");
            assertThat(events).as("the replaced window took its slot back before the other heard it shows").containsExactly("a:false", "a:true");
            assertThat(owner.dock().visible("dev.x.a")).isTrue();
            assertThat(owner.dock().visible("dev.x.b")).isFalse();
        });
    }

    @Test void railActionsAndTheRailToggleCommandHideAndShowBothStripes() throws Exception {
        edt(() -> {
            var model = new Contributions();
            var state = UiState.inMemory();
            WindowContent owner = window();
            owner.connectContributions(model, state);
            owner.setActive(true);
            List<Contributions.Invocation> seen = new ArrayList<>();
            model.addAction("dev.x.open", "Open Manager", new ImageIcon(), List.of(), Optional.empty(), seen::add);
            model.addRailAction("dev.x.open");
            model.addRailAction("dev.x.never-registered");
            assertThat(owner.leftStripe().isVisible()).isTrue();
            assertThat(owner.rightStripe().isVisible()).as("rail actions are on the left; the empty right stripe hides").isFalse();
            assertThat(owner.leftStripe().buttons()).singleElement().satisfies(button -> {
                assertThat(button.getToolTipText()).isEqualTo("Open Manager");
                button.doClick();
            });
            assertThat(owner.rightStripe().buttons()).as("rail actions live on the left stripe").isEmpty();
            assertThat(seen).hasSize(1);

            owner.dispatchCommand(owner.commands().find("view.rail").orElseThrow());
            assertThat(owner.leftStripe().isVisible()).isFalse();
            assertThat(owner.rightStripe().isVisible()).isFalse();
            assertThat(state.railVisible()).isFalse();
            owner.setRailVisible(true);
            assertThat(owner.leftStripe().isVisible()).isTrue();
            assertThat(owner.rightStripe().isVisible()).isFalse();
        });
    }

    /** The frame around the terminal column: the area's child that holds the tab strip. */
    private static Component centerFrame(WindowContent owner) {
        Component frame = owner.tabStrip();
        while (frame.getParent() != owner.area()) frame = frame.getParent();
        return frame;
    }

    @Test void eachStripeShowsOnlyWhileItHasAWindowAndTheTerminalIsBorderlessOnAHiddenOnesSide() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.inMemory());
            model.addPanel("dev.x.logs", "Logs", new ImageIcon(), Slot.BOTTOM_LEFT, site -> new JLabel("logs"));
            toggle(model, owner, "dev.x.logs");
            var root = new javax.swing.JRootPane(); root.setContentPane(owner); root.setSize(1200, 800);
            LayoutTestSupport.layoutTree(root);
            assertThat(owner.leftStripe().isVisible()).isTrue();
            assertThat(owner.rightStripe().isVisible()).isFalse();
            var logs = owner.area().content(Slot.BOTTOM_LEFT).getParent();
            assertThat(((JComponent) logs).getInsets().left).as("the left stripe supplies the left edge's spacing").isZero();
            assertThat(((JComponent) logs).getInsets().right).as("no right stripe: a tool window keeps its edge gutter, as in Zen").isPositive();
            var center = (JComponent) centerFrame(owner);
            assertThat(center.getInsets().left).isZero();
            assertThat(center.getInsets().right).as("the terminal is borderless on the hidden stripe's side").isZero();

            owner.dock().move("dev.x.logs", Slot.BOTTOM_RIGHT, 0);
            LayoutTestSupport.layoutTree(root);
            assertThat(owner.leftStripe().isVisible()).as("the left stripe emptied").isFalse();
            assertThat(owner.rightStripe().isVisible()).isTrue();
            logs = owner.area().content(Slot.BOTTOM_RIGHT).getParent();
            assertThat(((JComponent) logs).getInsets().left).isPositive();
            assertThat(((JComponent) logs).getInsets().right).isZero();
            assertThat(center.getInsets().left).isZero();
            assertThat(center.getInsets().right).isZero();
        });
    }

    @Test void aRailActionsTitleAndEnabledChangesUpdateItsButtonInPlace() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.inMemory());
            ActionEntry open = model.addAction("dev.x.open", "Open Manager", new ImageIcon(), List.of(), Optional.empty(), invocation -> { });
            model.addRailAction("dev.x.open");
            AbstractButton button = owner.leftStripe().buttons().getFirst();
            assertThat(button.isEnabled()).isTrue();

            open.setTitle("Open Manager (2)");
            assertThat(owner.leftStripe().buttons()).as("the same button, not a rebuilt one").containsExactly(button);
            assertThat(button.getToolTipText()).isEqualTo("Open Manager (2)");
            assertThat(button.getAccessibleContext().getAccessibleName()).isEqualTo("Open Manager (2)");

            open.setEnabled(false);
            assertThat(owner.leftStripe().buttons()).containsExactly(button);
            assertThat(button.isEnabled()).isFalse();
            open.setEnabled(true);
            assertThat(button.isEnabled()).isTrue();
        });
    }

    @Test void addingOrRemovingARailActionStillRendersTheStripe() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.inMemory());
            model.addRailAction("dev.x.open");
            assertThat(owner.leftStripe().buttons()).as("nothing to show until the action exists").isEmpty();

            ActionEntry open = model.addAction("dev.x.open", "Open Manager", new ImageIcon(), List.of(), Optional.empty(), invocation -> { });
            assertThat(tips(owner.leftStripe())).as("a new action appears").containsExactly("Open Manager");

            open.close();
            assertThat(owner.leftStripe().buttons()).as("a removed action goes").isEmpty();

            model.addAction("dev.x.open", "Open Again", new ImageIcon(), List.of(), Optional.empty(), invocation -> { });
            assertThat(tips(owner.leftStripe())).as("and a registered one appears again").containsExactly("Open Again");
        });
    }

    @Test void hiddenPanelKeepsItsComponentAndHandlersAcrossADelegateThemeUpdate() throws Exception {
        edt(() -> {
            var themes = ThemeTestSupport.controller(true);
            var owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()), themes);
            var model = new Contributions(); owner.connectContributions(model, UiState.inMemory());
            var clicks = new java.util.concurrent.atomic.AtomicInteger();
            var button = new javax.swing.JButton("Plugin action");
            button.addActionListener(event -> clicks.incrementAndGet());
            var instances = new java.util.concurrent.atomic.AtomicInteger();
            model.addPanel("dev.x.hidden", "Hidden panel", new ImageIcon(), Slot.LEFT_TOP, site -> {
                instances.incrementAndGet(); return button;
            });
            toggle(model, owner, "dev.x.hidden");
            toggle(model, owner, "dev.x.hidden");
            owner.applyTheme(owner.theme(), true);
            toggle(model, owner, "dev.x.hidden");
            assertThat(owner.area().content(Slot.LEFT_TOP)).isSameAs(button);
            assertThat(instances.get()).isEqualTo(1);
            button.doClick(); assertThat(clicks.get()).isEqualTo(1);
        });
    }

    @Test void theStripesAndSidePanelsReachTheToolbarAndTheBottomSpansStripeToStripe() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.newTab(DesktopTestSupport.HOME);
            owner.connectContributions(model, UiState.inMemory());
            var left = new JLabel("left"); var right = new JLabel("right"); var bottom = new JLabel("bottom");
            model.addPanel("dev.x.left", "Left", new ImageIcon(), Slot.LEFT_TOP, site -> left);
            model.addPanel("dev.x.right", "Right", new ImageIcon(), Slot.RIGHT_TOP, site -> right);
            model.addPanel("dev.x.bottom", "Bottom", new ImageIcon(), Slot.BOTTOM_LEFT, site -> bottom);
            toggle(model, owner, "dev.x.left"); toggle(model, owner, "dev.x.right"); toggle(model, owner, "dev.x.bottom");
            owner.setSize(1200, 800);
            LayoutTestSupport.layoutTree(owner); LayoutTestSupport.layoutTree(owner);

            Rectangle toolbar = in(owner, owner.toolbar()), leftStripe = in(owner, owner.leftStripe()), rightStripe = in(owner, owner.rightStripe());
            Rectangle strip = in(owner, owner.windowTabs()), deck = in(owner, owner.tabStrip());
            Rectangle leftBounds = in(owner, left.getParent()), rightBounds = in(owner, right.getParent()), bottomBounds = in(owner, bottom.getParent());
            int underToolbar = toolbar.y + toolbar.height;
            assertThat(leftStripe.y).as("the left stripe starts under the toolbar").isEqualTo(underToolbar);
            assertThat(rightStripe.y).as("so does the right stripe").isEqualTo(underToolbar);
            assertThat(leftBounds.y).as("the left panel starts under the toolbar").isEqualTo(underToolbar);
            assertThat(rightBounds.y).as("the right panel starts under the toolbar").isEqualTo(underToolbar);
            assertThat(strip.y).isEqualTo(underToolbar);
            assertThat(strip.x).as("the tabs start right of the left panel").isGreaterThanOrEqualTo(leftBounds.x + leftBounds.width);
            assertThat(strip.x + strip.width).as("the tabs end left of the right panel").isLessThanOrEqualTo(rightBounds.x);
            assertThat(strip.x).isEqualTo(deck.x);
            assertThat(strip.width).isEqualTo(deck.width);
            assertThat(deck.y).as("the terminal sits right under its tabs").isEqualTo(strip.y + strip.height);
            assertThat(bottomBounds.y).as("the bottom area sits under the terminal").isGreaterThan(deck.y + deck.height);
            assertThat(leftBounds.y + leftBounds.height).as("and under the side panels").isLessThan(bottomBounds.y);
            assertThat(bottomBounds.x).as("it spans stripe to stripe").isEqualTo(leftStripe.x + leftStripe.width);
            assertThat(bottomBounds.x + bottomBounds.width).isEqualTo(rightStripe.x);
        });
    }

    @Test void aFirstInstallOpensSymmetricRailsPanelsAndPairedTools() throws Exception {
        Path file = dir.resolve("first-install-ui-state.toml");
        assertThat(file).doesNotExist();
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.applyConfiguration(dev.jasper.app.config.ConfigSnapshot.defaults(), true);
            owner.connectContributions(model, UiState.load(file));
            for (Slot slot : Slot.values()) {
                String id = "dev.x." + slot.name().toLowerCase(java.util.Locale.ROOT);
                model.addPanel(id, slot.name(), new ImageIcon(), slot, site -> {
                    var content = new JPanel();
                    // Tool-specific preferred sizes must not override the shared dock defaults.
                    content.setPreferredSize(new java.awt.Dimension(350 + slot.ordinal() * 50, 400));
                    return content;
                });
                toggle(model, owner, id);
            }
            for (int width : new int[]{1200, 1401}) {
                owner.setSize(width, 800);
                LayoutTestSupport.layoutTree(owner); LayoutTestSupport.layoutTree(owner);
                Rectangle leftRail = in(owner, owner.leftStripe()), rightRail = in(owner, owner.rightStripe());
                assertThat(leftRail.width).isEqualTo(UIScale.scale(28));
                assertThat(rightRail.width).isEqualTo(leftRail.width);
                assertThat(rightRail.y).isEqualTo(leftRail.y);
                assertThat(rightRail.height).isEqualTo(leftRail.height);
                Rectangle leftTop = in(owner, owner.area().content(Slot.LEFT_TOP).getParent());
                Rectangle leftBottom = in(owner, owner.area().content(Slot.LEFT_BOTTOM).getParent());
                Rectangle rightTop = in(owner, owner.area().content(Slot.RIGHT_TOP).getParent());
                Rectangle rightBottom = in(owner, owner.area().content(Slot.RIGHT_BOTTOM).getParent());
                for (Rectangle panel : List.of(leftTop, leftBottom, rightTop, rightBottom))
                    assertThat(panel.width).isEqualTo(UIScale.scale(260));
                assertThat(rightTop.y).isEqualTo(leftTop.y);
                assertThat(rightTop.height).isEqualTo(leftTop.height);
                assertThat(rightBottom.y).isEqualTo(leftBottom.y);
                assertThat(rightBottom.height).isEqualTo(leftBottom.height);
                assertThat(Math.abs(leftTop.height - leftBottom.height)).as("equal heights, allowing pixel rounding").isLessThanOrEqualTo(1);
                assertThat(leftTop.x - leftRail.x - leftRail.width).as("matching spacing against the rails")
                    .isEqualTo(rightRail.x - rightTop.x - rightTop.width);
                Rectangle bottomLeft = in(owner, owner.area().content(Slot.BOTTOM_LEFT).getParent());
                Rectangle bottomRight = in(owner, owner.area().content(Slot.BOTTOM_RIGHT).getParent());
                assertThat(bottomLeft.height).isEqualTo(UIScale.scale(200));
                assertThat(bottomRight.height).isEqualTo(bottomLeft.height);
                assertThat(bottomRight.y).isEqualTo(bottomLeft.y);
                assertThat(Math.abs(bottomLeft.width - bottomRight.width)).as("equal widths, allowing pixel rounding").isLessThanOrEqualTo(1);
                assertThat(bottomLeft.x).isEqualTo(leftTop.x);
                assertThat(bottomRight.x + bottomRight.width).isEqualTo(rightTop.x + rightTop.width);
            }
        });
    }

    @Test void draggingADividerSavesTheNewSideSize() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.load(file));
            model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("hosts"));
            toggle(model, owner, "dev.x.hosts");
            owner.setSize(1200, 800);
            LayoutTestSupport.layoutTree(owner);
            var divider = owner.area().divider(DockGeometry.Handle.LEFT_WIDTH);
            assertThat(divider.getX()).isEqualTo(260);
            mouse(divider, MouseEvent.MOUSE_PRESSED, 2, 50);
            mouse(divider, MouseEvent.MOUSE_DRAGGED, 42, 50);
            mouse(divider, MouseEvent.MOUSE_RELEASED, 42, 50);
            assertThat(owner.dock().size(Side.LEFT)).isEqualTo(300);
        });
        assertThat(UiState.load(file).dock().leftWidth()).isEqualTo(300);
    }

    @Test void aVersionOneLayoutOpensWhereItWas() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        Files.writeString(file, """
            version = 1
            rail_visible = true

            [panels."dev.x.hosts"]
            region = "LEFT"
            visible = true
            size = 300

            [windows."dev.x.manager"]
            x = 10
            y = 20
            width = 640
            height = 480
            """);
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.load(file));
            var hosts = new JLabel("hosts");
            model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), Slot.RIGHT_TOP, site -> hosts);
            assertThat(owner.area().content(Slot.LEFT_TOP)).as("the saved place wins over the default").isSameAs(hosts);
            assertThat(owner.dock().size(Side.LEFT)).isEqualTo(300);
            owner.setSize(1200, 800);
            LayoutTestSupport.layoutTree(owner);
            assertThat(hosts.getParent().getWidth()).isEqualTo(UIScale.scale(300));
        });
        assertThat(Files.readString(file)).startsWith("version = 2\n")
            .contains("left_width = 300", "slot = \"LEFT_TOP\"", "[windows.\"dev.x.manager\"]");
    }

    @Test void theSavedOrderSurvivesAnyRegistrationOrder() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        var saved = UiState.load(file);
        saved.putPanel("dev.x.a", new UiState.Panel(Slot.LEFT_TOP, 0, false));
        saved.putPanel("dev.x.b", new UiState.Panel(Slot.LEFT_TOP, 1, false));
        saved.putPanel("dev.x.c", new UiState.Panel(Slot.LEFT_TOP, 2, false));
        saved.save();
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.load(file));
            for (String id : List.of("dev.x.c", "dev.x.b", "dev.x.a"))
                model.addPanel(id, id.substring(6).toUpperCase(java.util.Locale.ROOT), new ImageIcon(), Slot.RIGHT_TOP, site -> new JLabel(id));
            model.addPanel("dev.x.new", "NEW", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("new"));
            assertThat(owner.dock().ids(Slot.LEFT_TOP)).containsExactly("dev.x.a", "dev.x.b", "dev.x.c", "dev.x.new");
            assertThat(tips(owner.leftStripe())).containsExactly("A", "B", "C", "NEW");
        });
        assertThat(UiState.load(file).panel("dev.x.new")).hasValue(new UiState.Panel(Slot.LEFT_TOP, 3, false));
    }

    @Test void aReloadedPluginGoesBackWhereItWas() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.inMemory());
            List<String> events = new ArrayList<>();
            model.addPanel("dev.x.a", "A", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("a"));
            PanelEntry b = model.addPanel("dev.x.b", "B", new ImageIcon(), Slot.LEFT_TOP, site -> {
                site.onClosed(() -> events.add("b:closed"));
                return new JLabel("b");
            });
            model.addPanel("dev.x.c", "C", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("c"));
            toggle(model, owner, "dev.x.b");
            b.close();
            assertThat(events).containsExactly("b:closed");
            assertThat(owner.area().content(Slot.LEFT_TOP)).as("its frame goes").isNull();
            assertThat(tips(owner.leftStripe())).containsExactly("A", "C");

            var again = new JLabel("b again");
            model.addPanel("dev.x.b", "B", new ImageIcon(), Slot.RIGHT_TOP, site -> again);
            assertThat(owner.dock().ids(Slot.LEFT_TOP)).containsExactly("dev.x.a", "dev.x.b", "dev.x.c");
            assertThat(owner.area().content(Slot.LEFT_TOP)).as("and it shows again, as it did").isSameAs(again);
        });
    }

    @Test void aMigratedLayoutFollowsRegistrationOrder() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        Files.writeString(file, """
            version = 1
            [panels."dev.x.a"]
            region = "LEFT"
            visible = false
            size = 260
            [panels."dev.x.b"]
            region = "LEFT"
            visible = false
            size = 260
            """);
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.load(file));
            model.addPanel("dev.x.b", "B", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("b"));
            model.addPanel("dev.x.c", "C", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("c"));
            model.addPanel("dev.x.a", "A", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("a"));
            assertThat(owner.dock().ids(Slot.LEFT_TOP)).containsExactly("dev.x.b", "dev.x.c", "dev.x.a");
        });
    }

    @Test void eachWindowKeepsItsOwnLayoutAndANewWindowTakesTheLastSaved() throws Exception {
        edt(() -> {
            var model = new Contributions();
            var state = UiState.inMemory();
            WindowContent first = window(), second = window();
            first.connectContributions(model, state);
            second.connectContributions(model, state);
            model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("hosts"));
            toggle(model, first, "dev.x.hosts");
            ToolWindowHeaderTest.item(first.area().headerOf(Slot.LEFT_TOP).menu(), "Right Top").doClick();
            assertThat(first.dock().slot("dev.x.hosts")).hasValue(Slot.RIGHT_TOP);
            assertThat(second.dock().slot("dev.x.hosts")).as("the other window keeps its own layout").hasValue(Slot.LEFT_TOP);
            assertThat(second.dock().visible("dev.x.hosts")).isFalse();

            WindowContent third = window();
            third.connectContributions(model, state);
            assertThat(third.dock().slot("dev.x.hosts")).as("a new window opens with the last saved layout").hasValue(Slot.RIGHT_TOP);
            assertThat(third.dock().visible("dev.x.hosts")).isTrue();
        });
    }

    @Test void theLastWindowClosedWinsEvenWhenAnotherWindowWasEditedLast() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        edt(() -> {
            var model = new Contributions();
            var state = UiState.load(file);
            WindowContent first = window(), second = window();
            first.connectContributions(model, state);
            second.connectContributions(model, state);
            model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("hosts"));
            var menu = first.leftStripe().buttons().getFirst().getComponentPopupMenu();
            ToolWindowHeaderTest.item(menu, "Right Top").doClick();
            assertThat(UiState.load(file).panel("dev.x.hosts")).hasValue(new UiState.Panel(Slot.RIGHT_TOP, 0, false));
            second.close();
            assertThat(UiState.load(file).panel("dev.x.hosts")).as("the closing window saves its own layout")
                .hasValue(new UiState.Panel(Slot.LEFT_TOP, 0, false));
            first.close();
            assertThat(UiState.load(file).panel("dev.x.hosts")).as("the last window closed wins")
                .hasValue(new UiState.Panel(Slot.RIGHT_TOP, 0, false));
        });
    }

    @Test void aDiscardedWindowSavesNothingSoTheLastWindowClosedStillWins() throws Exception {
        // A window that failed to compose, or the warm-up rehearsal's, is discarded rather than closed.
        Path file = dir.resolve("ui-state.toml");
        edt(() -> {
            var model = new Contributions();
            var state = UiState.load(file);
            WindowContent kept = window(), discarded = window();
            kept.connectContributions(model, state);
            discarded.connectContributions(model, state);
            model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("hosts"));
            var menu = kept.leftStripe().buttons().getFirst().getComponentPopupMenu();
            ToolWindowHeaderTest.item(menu, "Right Top").doClick();
            kept.close();
            discarded.forgetLayout();
            discarded.close();
            assertThat(discarded.closed()).isTrue();
            assertThat(UiState.load(file).panel("dev.x.hosts")).as("the discarded window wrote nothing")
                .hasValue(new UiState.Panel(Slot.RIGHT_TOP, 0, false));
            assertThat(state.panel("dev.x.hosts")).as("nor changed the shared state")
                .hasValue(new UiState.Panel(Slot.RIGHT_TOP, 0, false));
        });
    }

    @Test void aWindowOpenedAfterEveryWindowClosedInTheSameProcessGetsTheLastLayout() throws Exception {
        // Quit, and a resident Jasper's Quit to the background, close every window in the order they opened; a resident
        // process then opens its next window from the same state, without reading the file again.
        Path file = dir.resolve("ui-state.toml");
        edt(() -> {
            var model = new Contributions();
            model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("hosts"));
            var state = UiState.load(file);
            WindowContent older = window(), newer = window();
            older.connectContributions(model, state);
            newer.connectContributions(model, state);
            toggle(model, newer, "dev.x.hosts");
            newer.dock().resize(Side.LEFT, 243);
            older.close();
            newer.close();
            WindowContent reopened = window();
            reopened.connectContributions(model, state);
            assertThat(reopened.dock().visible("dev.x.hosts")).isTrue();
            assertThat(reopened.dock().size(Side.LEFT)).isEqualTo(243);
        });
    }

    @Test void closingAndReloadingRestoresTheWholeDockAndTerminalArea() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        edt(() -> {
            var model = new Contributions();
            for (Slot slot : Slot.values())
                model.addPanel("dev.x." + slot.name().toLowerCase(java.util.Locale.ROOT), slot.name(), new ImageIcon(),
                    slot, site -> new JLabel("tool"));
            var state = UiState.load(file);
            WindowContent first = window(), last = window();
            first.connectContributions(model, state);
            last.connectContributions(model, state);
            last.setSize(1403, 907);
            for (var entry : model.panels()) toggle(model, last, entry.id());
            last.dock().resize(Side.LEFT, 243); last.dock().resize(Side.RIGHT, 317); last.dock().resize(Side.BOTTOM, 211);
            last.dock().setRatio(Side.LEFT, .37); last.dock().setRatio(Side.RIGHT, .64); last.dock().setRatio(Side.BOTTOM, .43);
            LayoutTestSupport.layoutTree(last);
            var terminal = new Rectangle(last.tabStrip().getBounds());
            var panels = state.panels(); var dock = state.dock();
            // The other window edits last, but closes first.
            first.dock().resize(Side.LEFT, 499);
            first.setRailVisible(false);
            first.close(); last.close();
            var loaded = UiState.load(file);
            assertThat(loaded.dock()).isEqualTo(dock);
            assertThat(loaded.panels()).isEqualTo(panels);
            assertThat(loaded.railVisible()).isTrue();
            WindowContent restored = window(); restored.setSize(1403, 907);
            restored.connectContributions(model, loaded);
            LayoutTestSupport.layoutTree(restored);
            assertThat(restored.tabStrip().getBounds()).isEqualTo(terminal);
            for (var entry : model.panels()) assertThat(restored.dock().visible(entry.id())).isTrue();
        });
    }

    @Test void closingTheWindowSavesShowingPanelsAsShowing() throws Exception {
        edt(() -> {
            var model = new Contributions();
            var state = UiState.inMemory();
            WindowContent owner = window();
            owner.connectContributions(model, state);
            List<String> events = new ArrayList<>();
            model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), Slot.BOTTOM_RIGHT, site -> {
                site.onVisibility(visible -> events.add("visible:" + visible));
                site.onClosed(() -> events.add("closed"));
                return new JLabel("hosts");
            });
            toggle(model, owner, "dev.x.hosts");
            assertThat(owner.rightStripe().buttons()).as("a Bottom Right window stands on the right stripe").hasSize(1);
            owner.close();
            assertThat(state.panel("dev.x.hosts")).hasValue(new UiState.Panel(Slot.BOTTOM_RIGHT, 0, true));
            assertThat(events).as("closing is not hiding").containsExactly("visible:true", "closed");
            assertThat(owner.rightStripe().buttons()).as("closing clears the stripe").isEmpty();
        });
    }

    @Test void aBadgedPanelShowsADotAndDescribesItsCount() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.inMemory());
            var icon = new ImageIcon(new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB));
            PanelEntry notes = model.addPanel("dev.x.notes", "Notes", icon, Slot.RIGHT_TOP, site -> new JLabel("notes"));
            notes.setBadge(Optional.of(new PanelBadge(4, PanelBadge.Tone.ERROR, "4 unread notifications, 1 error")));
            AbstractButton button = owner.rightStripe().buttons().getFirst();
            assertThat(button.getToolTipText()).isEqualTo("Notes (4 unread notifications, 1 error)");
            assertThat(button.getAccessibleContext().getAccessibleName()).isEqualTo("Notes");
            assertThat(button.getAccessibleContext().getAccessibleDescription()).isEqualTo("4 unread notifications, 1 error");
            assertThat(button.getIcon()).isInstanceOfSatisfying(BadgedIcon.class, badged -> {
                assertThat(badged.tone()).isEqualTo(PanelBadge.Tone.ERROR);
                assertThat(badged.base()).isSameAs(icon);
            });
            notes.setBadge(Optional.empty());
            assertThat(owner.rightStripe().buttons().getFirst().getIcon()).isSameAs(icon);
            assertThat(owner.rightStripe().buttons().getFirst().getToolTipText()).isEqualTo("Notes");
            assertThat(owner.rightStripe().buttons().getFirst().getAccessibleContext().getAccessibleDescription())
                .as("the badge's words go with it").doesNotContain("unread");
        });
    }

    @Test void aBadgeSetAndClearedUpdatesTheSameButtonInPlace() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.inMemory());
            var icon = new ImageIcon(new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB));
            PanelEntry notes = model.addPanel("dev.x.notes", "Notes", icon, Slot.RIGHT_TOP, site -> new JLabel("notes"));
            AbstractButton button = owner.rightStripe().buttons().getFirst();
            owner.setSize(1200, 800);
            LayoutTestSupport.layoutTree(owner);
            var layoutBefore = button.getBounds();
            assertThat(layoutBefore).isNotEqualTo(new Rectangle());
            notes.setBadge(Optional.of(new PanelBadge(4, PanelBadge.Tone.ERROR, "4 unread notifications, 1 error")));
            assertThat(owner.rightStripe().buttons().getFirst()).as("the badge is not a render").isSameAs(button);
            assertThat(button.getIcon()).isInstanceOf(BadgedIcon.class);
            assertThat(button.getToolTipText()).isEqualTo("Notes (4 unread notifications, 1 error)");
            assertThat(button.getAccessibleContext().getAccessibleDescription()).isEqualTo("4 unread notifications, 1 error");
            notes.setBadge(Optional.of(new PanelBadge(1, PanelBadge.Tone.ACCENT, "1 unread notification")));
            assertThat(owner.rightStripe().buttons().getFirst()).isSameAs(button);
            assertThat(button.getIcon()).isInstanceOfSatisfying(BadgedIcon.class, badged -> {
                assertThat(badged.tone()).as("a changed badge replaces the old one").isEqualTo(PanelBadge.Tone.ACCENT);
                assertThat(badged.base()).isSameAs(icon);
            });
            assertThat(button.getToolTipText()).isEqualTo("Notes (1 unread notification)");
            notes.setBadge(Optional.empty());
            assertThat(owner.rightStripe().buttons().getFirst()).as("nor is clearing it").isSameAs(button);
            assertThat(button.getIcon()).isSameAs(icon);
            assertThat(button.getToolTipText()).isEqualTo("Notes");
            assertThat(button.getAccessibleContext().getAccessibleName()).isEqualTo("Notes");
            assertThat(button.getAccessibleContext().getAccessibleDescription())
                .as("no badge, no badge description").doesNotContain("unread");
            assertThat(button.getBounds()).as("the layout did not move").isEqualTo(layoutBefore);
        });
    }

    @Test void aSplitSideRaisesTheWindowsMinimumHeightAndTheWindowHearsOfIt() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.inMemory());
            model.addPanel("dev.x.sessions", "Sessions", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("sessions"));
            model.addPanel("dev.x.sftp", "SFTP", new ImageIcon(), Slot.LEFT_BOTTOM, site -> new JLabel("sftp"));
            var heard = new java.util.concurrent.atomic.AtomicInteger();
            owner.onMinimumSizeChanged = heard::incrementAndGet;
            int split = UIScale.scale(2 * 120 + 6);

            toggle(model, owner, "dev.x.sessions");
            int alone = owner.getMinimumSize().height, heardAlone = heard.get();
            assertThat(heardAlone).as("showing a window tells the window").isPositive();
            toggle(model, owner, "dev.x.sftp");
            assertThat(heard.get()).as("so does the second half of a side").isGreaterThan(heardAlone);
            assertThat(owner.area().getMinimumSize().height).isGreaterThanOrEqualTo(split);
            assertThat(owner.getMinimumSize().height).as("the window's minimum grows with the split")
                .isGreaterThan(alone).isGreaterThanOrEqualTo(split);

            int heardSplit = heard.get();
            toggle(model, owner, "dev.x.sftp");
            assertThat(heard.get()).as("and so does hiding it again").isGreaterThan(heardSplit);
            assertThat(owner.getMinimumSize().height).isEqualTo(alone);
        });
    }

    @Test void theStripesAppearingAndGoingAwayRefreshTheWindowsMinimum() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.inMemory());
            // The stripes show without any dock sync (a panel is only registered, none shows), so only their own
            // appearing can tell the window what its minimum is with them.
            List<Boolean> stripes = new ArrayList<>();
            List<java.awt.Dimension> minimums = new ArrayList<>();
            owner.onMinimumSizeChanged = () -> { stripes.add(owner.leftStripe().isVisible()); minimums.add(owner.getMinimumSize()); };
            var bare = owner.getMinimumSize();
            model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("hosts"));
            assertThat(owner.leftStripe().isVisible()).isTrue();
            assertThat(stripes).as("the stripes appearing tell the window").isNotEmpty();
            assertThat(stripes.getLast()).as("and it last heard with them showing").isTrue();
            var withStripes = minimums.getLast();
            assertThat(owner.rightStripe().isVisible()).as("nothing on the right").isFalse();
            assertThat(withStripes.width).as("the left stripe").isGreaterThanOrEqualTo(bare.width + UIScale.scale(owner.railWidth()));
            assertThat(withStripes.height).as("the lower inset").isGreaterThanOrEqualTo(bare.height + UIScale.scale(8));
            assertThat(withStripes).as("what the window heard is what it has").isEqualTo(owner.getMinimumSize());

            int heard = stripes.size();
            owner.setRailVisible(false);
            assertThat(stripes.size()).as("hiding the stripes tells the window too").isGreaterThan(heard);
            assertThat(stripes.getLast()).isFalse();
            assertThat(minimums.getLast()).isEqualTo(owner.getMinimumSize()).isEqualTo(bare);
        });
    }
}
