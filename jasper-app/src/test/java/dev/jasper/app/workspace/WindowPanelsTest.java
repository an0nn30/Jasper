package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
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

    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); edt(() -> new dev.jasper.app.appearance.ThemeController()); }

    private static WindowContent window() { return DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>())); }

    private static void toggle(Contributions model, WindowContent owner, String id) {
        model.requestPanel(new Contributions.PanelRequest(owner.id(), id, Contributions.PanelRequest.Op.TOGGLE));
    }

    private static List<String> tips(ToolStripe stripe) { return stripe.buttons().stream().map(AbstractButton::getToolTipText).toList(); }

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
            assertThat(owner.rightStripe().isVisible()).as("an empty right stripe still shows").isTrue();
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
            assertThat(owner.rightStripe().isVisible()).isTrue();
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
            assertThat(owner.rightStripe().isVisible()).isTrue();
        });
    }

    @Test void hiddenPanelKeepsItsComponentAndHandlersAcrossADelegateThemeUpdate() throws Exception {
        edt(() -> {
            var themes = new dev.jasper.app.appearance.ThemeController(dev.jasper.app.appearance.Theme.DARK);
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

    @Test void closingAWindowKeepsTheChangesAnotherWindowSaved() throws Exception {
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
            assertThat(UiState.load(file).panel("dev.x.hosts")).as("the untouched window saves nothing as it closes")
                .hasValue(new UiState.Panel(Slot.RIGHT_TOP, 0, false));
            first.close();
            assertThat(UiState.load(file).panel("dev.x.hosts")).as("the last change saved wins")
                .hasValue(new UiState.Panel(Slot.RIGHT_TOP, 0, false));
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
}
