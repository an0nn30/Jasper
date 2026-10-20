package dev.jasper.ui.swing;

import static dev.jasper.ui.model.ControlState.READY;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.Nodes.ButtonKind;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.Icon;
import org.junit.jupiter.api.Test;

/**
 * Plan 15b Task 2: under a window's classic chrome icons, the six classic names in a toolbar view and in a panel's header
 * (its own controls and the host's actions) draw IntelliJ's classic art; every other name, and every panel body, keeps
 * its art.
 */
class KitClassicChromeIconsTest {
    private static final Icon RAW = new Icon() {
        @Override public int getIconWidth() { return 16; }
        @Override public int getIconHeight() { return 16; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) { g.fillRect(x, y, 16, 16); }
    };

    private static Nodes.Button ghost(String id, String icon) {
        return new Nodes.Button(id, READY, "", icon, ButtonKind.GHOST, id, icon);
    }

    private static ViewSpec toolbar() {
        return new ViewSpec(new Nodes.Stack("bar", READY, Nodes.LayoutRole.INLINE,
            List.of(ghost("bar.search", "search"), ghost("bar.add", "add"), ghost("bar.folder", "folder"), ghost("bar.more", "more"),
                new Nodes.MenuButton("bar.new", READY, "", "add", ButtonKind.GHOST, "",
                    List.of(new dev.jasper.ui.model.MenuItem("bar.new.session", "Session", "", "session", true, false)), "New"))));
    }

    private static ViewSpec others() {
        return new ViewSpec(new Nodes.Stack("bar", READY, Nodes.LayoutRole.INLINE,
            List.of(ghost("bar.folder", "folder"), ghost("bar.more", "more"))));
    }

    private static ViewSpec body() {
        return new ViewSpec(new Nodes.Stack("body", READY, Nodes.LayoutRole.VERTICAL, List.of(ghost("body.add", "add"))));
    }

    private static ViewSpec panel() {
        return new ViewSpec(new Nodes.Panel("panel", READY,
            new Nodes.Header("panel-header", READY, "Sessions", OptionalInt.empty(), Optional.empty(), Optional.of(ghost("panel.add", "add")), List.of()),
            Optional.empty(), new Nodes.Text("panel.body", READY, "Contents", Nodes.TextRole.BODY), Optional.empty()));
    }

    /** A panel whose body is a document split with a bar holding an {@code add} button, as the Vault manager's + New. */
    private static ViewSpec documentBar() {
        return new ViewSpec(new Nodes.Panel("panel", READY,
            new Nodes.Header("panel-header", READY, "Vault", OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of()),
            Optional.empty(),
            new Nodes.Split("doc", READY, Nodes.SplitKind.DOCUMENT, List.of(
                new Nodes.Stack("doc.bar", READY, Nodes.LayoutRole.INLINE, List.of(ghost("doc.add", "add"))),
                new Nodes.Text("doc.text", READY, "Contents", Nodes.TextRole.BODY))),
            Optional.empty()));
    }

    private static PanelHostHeader hostHeader(PanelHostHeader.Frame frame) {
        return new PanelHostHeader("", false,
            List.of(new PanelHostHeader.Action("New", RAW, () -> { }, new PanelHostHeader.Artwork("add", null))), frame);
    }

    private static int[] pixels(ViewSpec spec, Consumer<KitView> presentation) {
        var context = KitFixtures.context();
        try (var view = new KitView(context, spec, event -> { })) {
            presentation.accept(view);
            return KitFixtures.render(view, 320, 120, 1).getRGB(0, 0, 320, 120, null, 0, 320);
        } finally { context.binding().close(); context.icons().close(); }
    }

    private static int[] painted(Icon icon) {
        var image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { icon.paintIcon(null, g, 0, 0); } finally { g.dispose(); }
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    /** {@code button} shows, and paints at its centre, exactly IntelliJ's classic {@code name} art. */
    private static void assertClassic(KitContext context, AbstractButton button, String name) {
        assertThat(button).as(name).isNotNull();
        var icon = button.getIcon();
        assertThat(icon).as(name).isNotNull();
        int size = icon.getIconWidth();
        var expected = context.icons().classicChromeIcon(name, size).orElseThrow().icon(context.theme(), SemanticColor.TEXT_PRIMARY);
        int[] want = painted(expected);
        assertThat(java.util.Arrays.stream(want).filter(argb -> argb >>> 24 != 0).count()).as("the classic %s art has ink", name).isPositive();
        assertThat(painted(icon)).as("%s draws the classic art", name).isEqualTo(want);
        if (button.getParent() instanceof Menus.MenuSurface) return;
        var image = new BufferedImage(button.getWidth(), button.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { button.paint(g); } finally { g.dispose(); }
        int x = (button.getWidth() - size) / 2, y = (button.getHeight() - size) / 2;
        assertThat(image.getSubimage(x, y, size, size).getRGB(0, 0, size, size, null, 0, size))
            .as("%s paints the classic art at its centre", name).isEqualTo(want);
    }

    private static AbstractButton labelled(Component root, String accessibleName) {
        if (root instanceof AbstractButton button && accessibleName.equals(button.getAccessibleContext().getAccessibleName())) return button;
        if (root instanceof java.awt.Container container)
            for (var child : container.getComponents()) { var found = labelled(child, accessibleName); if (found != null) return found; }
        return null;
    }

    @Test void theSixNamesInAToolbarStripDrawIntelliJsClassicArt() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, toolbar(), event -> { })) {
                view.toolbarStrip(true);
                view.classicChromeIcons(true);
                assertThat(view.classicChromeIcons()).isTrue();
                KitFixtures.render(view, 320, 40, 1);
                assertClassic(context, KitViewTest.find(view, "bar.search", AbstractButton.class), "search");
                assertClassic(context, KitViewTest.find(view, "bar.add", AbstractButton.class), "add");
                assertClassic(context, ((Menus.MenuControl) view.control("bar.new")).main, "add");
            } finally { context.binding().close(); context.icons().close(); }
        });
    }

    @Test void onlyTheSixNamesInHeadersAndToolbarStripsChange() {
        Edt.run(() -> {
            assertThat(pixels(others(), view -> { view.toolbarStrip(true); view.classicChromeIcons(true); }))
                .as("folder and more keep their art in a toolbar strip").isEqualTo(pixels(others(), view -> view.toolbarStrip(true)));
            assertThat(pixels(body(), view -> view.classicChromeIcons(true)))
                .as("a panel body's add keeps its art").isEqualTo(pixels(body(), view -> { }));
            assertThat(pixels(documentBar(), view -> view.classicChromeIcons(true)))
                .as("a document bar's add in a panel body keeps its art").isEqualTo(pixels(documentBar(), view -> { }));
            for (var frame : PanelHostHeader.Frame.values()) {
                var context = KitFixtures.context();
                try (var view = new KitView(context, panel(), event -> { })) {
                    view.setPanelHostHeader("panel", hostHeader(frame));
                    view.classicChromeIcons(true);
                    KitFixtures.render(view, 320, 120, 1);
                    assertClassic(context, KitViewTest.find(view, "panel.add", AbstractButton.class), "add");
                    assertClassic(context, labelled(view, "New"), "add");
                } finally { context.binding().close(); context.icons().close(); }
            }
        });
    }

    @Test void withoutTheFlagNothingChanges() {
        Edt.run(() -> {
            assertThat(pixels(toolbar(), view -> { view.toolbarStrip(true); view.classicChromeIcons(true); view.classicChromeIcons(false); }))
                .isEqualTo(pixels(toolbar(), view -> view.toolbarStrip(true)));
            for (var frame : PanelHostHeader.Frame.values())
                assertThat(pixels(panel(), view -> { view.setPanelHostHeader("panel", hostHeader(frame)); view.classicChromeIcons(true); view.classicChromeIcons(false); }))
                    .as(frame.name()).isEqualTo(pixels(panel(), view -> view.setPanelHostHeader("panel", hostHeader(frame))));
            assertThat(pixels(toolbar(), view -> { view.toolbarStrip(true); view.classicChromeIcons(true); }))
                .as("the flag does change a toolbar strip").isNotEqualTo(pixels(toolbar(), view -> view.toolbarStrip(true)));
        });
    }
}
