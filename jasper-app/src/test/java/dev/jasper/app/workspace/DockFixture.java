package dev.jasper.app.workspace;

import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.contributions.PanelHeaderState;
import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.platform.PresentationIcon;
import dev.jasper.app.testsupport.MockCrops;
import dev.jasper.ui.icons.ChromeArtwork;
import dev.jasper.ui.icons.IconRegistry;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.theme.GeneratedTheme;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.ThemeBinding;
import dev.jasper.ui.theme.ThemePair;
import dev.jasper.ui.theme.ThemeRoute;
import java.awt.BorderLayout;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * The Workstation mocks' dock as a headless fixture (spec 16.1.4): labelled stripes, flush frames, and the four panels in
 * the mocks' places and sizes with the mocks' headers ("SFTP ● Mac", "File Servers (1)", "Sessions (2)", "Tunnels (1)"
 * when connected), with native headers or borrowed kit headers, and the bottom strip's three closed labels. The panels'
 * bodies are blank: their compositions are plan 10's. Titles, numbers and counts are fixtures. EDT only.
 */
final class DockFixture implements AutoCloseable {
    /** The mocks' window content width, the dock area's height, and the left and right frames' widths, in logical pixels. */
    static final int WIDTH = 1440, AREA_HEIGHT = 763, LEFT = 300, RIGHT = 350;
    /** The mocks' splits: the left frames 381 and 381 high, the right 416 and 346, around a 1 px rule. */
    static final double LEFT_RATIO = 0.5, RIGHT_RATIO = 416.0 / 762;

    /**
     * One panel of the mocks.
     *
     * @param id its id
     * @param title its title
     * @param slot where the mock docks it
     * @param number its stripe digit, 0 for none
     * @param count its header count, 0 for none
     * @param detail its header detail
     * @param connected whether its header shows the connected dot
     * @param actions the icon names of its own header actions
     */
    record Panel(String id, String title, Slot slot, int number, int count, String detail, boolean connected, List<String> actions) { }

    final DockState state = new DockState();
    final StripeSet.LabelledStripes stripes;
    final ToolWindowArea area;
    final JPanel body = new JPanel(new BorderLayout());
    private final boolean kitHeaders;
    private final Map<String, KitView> views = new LinkedHashMap<>();
    private final ThemeBinding binding;
    private final IconRegistry icons = new IconRegistry();
    private final List<Panel> panels;

    /** The mocks' panels; the Disconnected mock drops the SFTP detail and the File Servers and Tunnels counts. */
    static List<Panel> panels(boolean connected) {
        return List.of(
            new Panel("sftp", "SFTP", Slot.LEFT_TOP, 1, 0, connected ? "Mac" : "", connected, List.of()),
            new Panel("servers", "File Servers", Slot.LEFT_BOTTOM, 2, connected ? 1 : 0, "", false, List.of("add")),
            new Panel("sessions", "Sessions", Slot.RIGHT_TOP, 0, 2, "", false, List.of("add", "import")),
            new Panel("tunnels", "Tunnels", Slot.RIGHT_BOTTOM, 0, connected ? 1 : 0, "", false, List.of("add")));
    }

    DockFixture(Kit kit, GeneratedTheme theme, boolean connected, boolean kitHeaders) {
        this.kitHeaders = kitHeaders;
        this.panels = panels(connected);
        this.binding = new ThemeBinding(new ThemePair(theme, theme), failure -> { throw new AssertionError(failure); });
        var context = KitContext.of(kit, binding, ThemeRoute.CHROME, icons, () -> false, action -> "");
        stripes = new StripeSet.LabelledStripes(kit, ChromeArtwork.CLASSIC);
        var terminal = new JPanel();
        terminal.setBackground(theme.semantic().color(SemanticColor.SURFACE_TERMINAL));
        area = new ToolWindowArea(terminal, state, kit, true, ChromeArtwork.CLASSIC);
        body.add(stripes.left(), BorderLayout.WEST);
        body.add(area, BorderLayout.CENTER);
        body.add(stripes.right(), BorderLayout.EAST);
        body.add(stripes.bottomStrip(), BorderLayout.SOUTH);
        state.resize(Side.LEFT, LEFT);
        state.resize(Side.RIGHT, RIGHT);
        state.setRatio(Side.LEFT, LEFT_RATIO);
        state.setRatio(Side.RIGHT, RIGHT_RATIO);
        for (Panel panel : panels) {
            JComponent content;
            if (kitHeaders) {
                var view = new KitView(context, spec(panel), event -> { });
                views.put(panel.id(), view);
                content = view;
            } else {
                var plain = new JPanel();
                plain.setBackground(theme.semantic().color(SemanticColor.SURFACE_PANEL));
                content = plain;
            }
            area.setContent(panel.id(), panel.title(), content);
            state.add(panel.id(), panel.slot());
            state.show(panel.id());
            area.setHeader(panel.id(), header(panel));
        }
        for (String id : List.of("log", "transfers", "problems")) state.add(id, Slot.BOTTOM_LEFT);
        area.sync();
        var buttons = new ArrayList<ToolStripe.Button>();
        for (Panel panel : panels)
            buttons.add(new ToolStripe.Button(panel.id(), panel.title(), new PresentationIcon(icon(panel.id()), null), panel.slot(), true,
                Optional.empty(), panel.number() == 0 ? OptionalInt.empty() : OptionalInt.of(panel.number())));
        buttons.add(new ToolStripe.Button("log", "Event Log", AppIcons.semantic("bell"), Slot.BOTTOM_LEFT, false));
        buttons.add(new ToolStripe.Button("transfers", "Transfers", new PresentationIcon("download", null), Slot.BOTTOM_LEFT, false));
        buttons.add(new ToolStripe.Button("problems", "Problems", null, Slot.BOTTOM_LEFT, false,
            Optional.of(new PanelBadge(1, PanelBadge.Tone.ERROR, "1 problem")), OptionalInt.empty()));
        for (DockStripe stripe : stripes.all()) stripe.render(buttons, List.of());
    }

    /** The icon name the shipped plugin declares for the panel {@code id} (the stripes lead each label with it). */
    private static String icon(String id) {
        return switch (id) {
            case "sftp" -> "folder";
            case "servers" -> "server";
            case "sessions" -> "network";
            case "tunnels" -> "tunnel";
            default -> throw new IllegalArgumentException(id);
        };
    }

    /**
     * A panel's borrowed kit header: the title and the count, as Remote's and File Server's panels build theirs (their own
     * actions reach the header through the host header, {@link #header(Panel)}).
     */
    private static ViewSpec spec(Panel panel) {
        var header = new Nodes.Header(panel.id() + ".header", ControlState.READY, panel.title(),
            panel.count() == 0 ? OptionalInt.empty() : OptionalInt.of(panel.count()), Optional.empty(), Optional.empty(), List.of());
        return new ViewSpec(new Nodes.Panel(panel.id(), ControlState.READY, header, Optional.empty(),
            new Nodes.Text(panel.id() + ".body", ControlState.READY, panel.title(), Nodes.TextRole.BODY), Optional.empty()));
    }

    /**
     * A panel's host header metadata: the detail, the dot and its own actions (as Remote's panels publish theirs through
     * {@code PanelHost.setHeader}), and, for a native header, the count as a digits-only detail.
     */
    private PanelHeaderState header(Panel panel) {
        String detail = !kitHeaders && panel.count() > 0 ? Integer.toString(panel.count()) : panel.detail();
        return new PanelHeaderState(detail, panel.connected(), panel.actions().stream()
            .map(icon -> new PanelHeaderState.Action(icon, AppIcons.semantic(icon), () -> { })).toList());
    }

    /** The body at the mocks' size: the dock area 763 high above the bottom strip. */
    BufferedImage render(int scale) {
        return MockCrops.render(body, WIDTH, AREA_HEIGHT + stripes.bottomStrip().getPreferredSize().height, scale);
    }

    /** Puts the focus owner in a panel, as clicking in it would. */
    void focus(String id) {
        Panel panel = panels.stream().filter(each -> each.id().equals(id)).findFirst().orElseThrow();
        area.focusMoved(area.content(panel.slot()));
    }

    /** A panel's header strip in the body's coordinates: its borrowed kit header or its native header. */
    Rectangle header(String id) {
        Panel panel = panels.stream().filter(each -> each.id().equals(id)).findFirst().orElseThrow();
        JComponent header = kitHeaders ? views.get(id).panelHeader(id) : area.headerOf(panel.slot());
        return SwingUtilities.convertRectangle(header.getParent(), header.getBounds(), body);
    }

    /** The independently specified native count; also hold the label's literal content and role. */
    String nativeCount(String id) {
        if (kitHeaders) return null;
        Panel panel = panels.stream().filter(each -> each.id().equals(id)).findFirst().orElseThrow();
        if (panel.count() == 0) return null;
        String expected = "(" + panel.count() + ")";
        var title = area.headerOf(panel.slot()).titleLabel();
        var labels = java.util.Arrays.stream(title.getParent().getComponents())
            .filter(component -> component instanceof javax.swing.JLabel && component != title)
            .map(component -> (javax.swing.JLabel) component).toList();
        org.assertj.core.api.Assertions.assertThat(labels).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(labels.getFirst().getText()).isEqualTo(expected);
        org.assertj.core.api.Assertions.assertThat(labels.getFirst().getFont())
            .isEqualTo(dev.jasper.app.platform.ThemeZone.font(dev.jasper.ui.metrics.Typography.Role.COUNT));
        return expected;
    }

    @Override public void close() {
        views.values().forEach(KitView::close);
        area.close();
        icons.close();
        binding.close();
    }
}
