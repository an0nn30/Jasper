package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static dev.jasper.ui.swing.PanelCompositionTest.*;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;

import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import javax.swing.*;

/**
 * Host header metadata (a native host's detail text, connected dot and contributed icon actions) shown in a borrowed kit
 * panel header: one title row, contributed actions before the host's own actions, released exactly once.
 */
class PanelHostHeaderTest {
    private static final javax.swing.Icon ARTWORK = new javax.swing.Icon() {
        @Override public int getIconWidth() { return 16; }
        @Override public int getIconHeight() { return 16; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) { g.fillRect(x, y, 16, 16); }
    };

    private static Nodes.Button ghost(String id, String tooltip) {
        return new Nodes.Button(id, READY, "", "collapse", ButtonKind.GHOST, id, tooltip);
    }

    private static ViewSpec spec(String panel, int count) {
        return new ViewSpec(new Nodes.Panel(panel, READY,
            new Header(panel + "-header", READY, "Sessions", OptionalInt.of(count), Optional.empty(),
                Optional.of(new Nodes.Button("add", READY, "", "add", ButtonKind.GHOST, "add", "Add")), List.of()),
            Optional.empty(), new Text("body", READY, "Contents", TextRole.BODY), Optional.empty()));
    }

    private static JLabel detail(KitView view, String panel) { return find(view, panel + "-header.host-detail", JLabel.class); }

    private static List<AbstractButton> buttons(Container root) {
        var result = new ArrayList<AbstractButton>();
        for (var child : root.getComponents()) {
            if (child instanceof AbstractButton button) result.add(button);
            else if (child instanceof Container nested) result.addAll(buttons(nested));
        }
        return result;
    }

    private static AbstractButton named(Container root, String accessibleName) {
        return buttons(root).stream().filter(b -> accessibleName.equals(b.getAccessibleContext().getAccessibleName()))
            .findFirst().orElse(null);
    }

    private static Color dotColour(JLabel detail) {
        var icon = detail.getIcon();
        var image = new BufferedImage(icon.getIconWidth() + 2, icon.getIconHeight() + 2, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { icon.paintIcon(detail, g, 0, 0); } finally { g.dispose(); }
        return new Color(image.getRGB(icon.getIconWidth() / 2, icon.getIconHeight() / 2), true);
    }

    @Test void rejectedArtworkKeepsEveryPriorActionUsableAndAcceptsLaterReplacement() {
        Edt.run(() -> {
            var ctx = KitFixtures.context(); var calls = new ArrayList<String>();
            try (var view = new KitView(ctx, spec("panel", 6), e -> {})) {
                var top = new PanelHostHeader.Artwork("owner:glyph", "M2 2 L14 2");
                var bottom = new PanelHostHeader.Artwork("owner:glyph", "M2 14 L14 14");
                var invalid = new PanelHostHeader.Artwork("owner:glyph", "invalid path");
                var original = new PanelHostHeader("Original", false, List.of(
                    new PanelHostHeader.Action("First", ARTWORK, () -> calls.add("first"), top),
                    new PanelHostHeader.Action("Second", ARTWORK, () -> calls.add("second"), bottom)));
                view.setPanelHostHeader("panel", original);
                var control = (PanelHeaders.HeaderControl)view.control("panel-header");
                var firstRegistry = control.contributed.get(0).hostArtwork.icons;
                var secondRegistry = control.contributed.get(1).hostArtwork.icons;
                var first = named(view, "First"); var second = named(view, "Second");
                var firstIcon = first.getIcon(); var secondIcon = second.getIcon();
                for (int rejectedIndex : new int[] {1, 0}) {
                    var rejected = new PanelHostHeader("Rejected", true, List.of(
                        new PanelHostHeader.Action("Changed first", ARTWORK, () -> calls.add("bad-first"), rejectedIndex == 0 ? invalid : bottom),
                        new PanelHostHeader.Action("Changed second", ARTWORK, () -> calls.add("bad-second"), rejectedIndex == 1 ? invalid : top)));
                    assertThatIllegalArgumentException().isThrownBy(() -> view.setPanelHostHeader("panel", rejected));
                    assertThat(named(view, "First")).as("a rejected later action cannot update a prefix").isSameAs(first);
                    assertThat(named(view, "Second")).isSameAs(second);
                    assertThat(first.getIcon()).isSameAs(firstIcon); assertThat(second.getIcon()).isSameAs(secondIcon);
                    assertThat(detail(view, "panel").getText()).isEqualTo("Original");
                    assertThat(firstRegistry.names()).contains("owner:glyph");
                    assertThat(secondRegistry.names()).contains("owner:glyph");
                    first.doClick(0); second.doClick(0);
                }
                assertThat(calls).containsExactly("first", "second", "first", "second");
                view.setPanelHostHeader("panel", new PanelHostHeader("Recovered", false, List.of(
                    new PanelHostHeader.Action("First", ARTWORK, () -> calls.add("replacement"), bottom),
                    new PanelHostHeader.Action("Raw", ARTWORK, () -> calls.add("raw")))));
                assertThat(named(view, "First")).isSameAs(first); assertThat(named(view, "Raw")).isSameAs(second);
                assertThatIllegalStateException().isThrownBy(firstRegistry::names);
                assertThatIllegalStateException().isThrownBy(secondRegistry::names);
                var recoveredRegistry = control.contributed.get(0).hostArtwork.icons;
                ctx.binding().publish(new ThemePair(KitFixtures.light(), KitFixtures.light()));
                assertThat(iconImage(first.getIcon()).getRGB(s(8), s(2)) >>> 24).isZero();
                assertThat(iconImage(first.getIcon()).getRGB(s(8), s(16)) >>> 24).isPositive();
                assertThat(second.getIcon()).isSameAs(ARTWORK);
                first.doClick(0); second.doClick(0);
                assertThat(calls).containsExactly("first", "second", "first", "second", "replacement", "raw");
                view.setPanelHostHeader("panel", PanelHostHeader.EMPTY);
                assertThatIllegalStateException().isThrownBy(recoveredRegistry::names);
                assertThat(first.getActionListeners()).isEmpty(); assertThat(second.getActionListeners()).isEmpty();
                first.doClick(0); assertThat(calls).hasSize(6);
            } finally { release(ctx); }
        });
    }

    @Test void rejectedInitialArtworkDoesNotRetainUnmountedControlsAndCanRecover() {
        Edt.run(() -> {
            var ctx = KitFixtures.context(); var calls = new ArrayList<String>();
            var view = new KitView(ctx, spec("panel", 6), e -> {});
            try {
                var header = (PanelHeaders.HeaderControl)view.control("panel-header");
                for (var invalid : List.of(new PanelHostHeader.Artwork("owner:glyph", "invalid path"),
                        new PanelHostHeader.Artwork("missing-canonical", null))) {
                    assertThatIllegalArgumentException().isThrownBy(() -> view.setPanelHostHeader("panel", new PanelHostHeader("Bad", false,
                        List.of(new PanelHostHeader.Action("Bad", ARTWORK, () -> calls.add("bad"), invalid)))));
                    assertThat(header.contributed).as("rejection cannot publish or retain an unmounted control").isEmpty();
                    assertThat(header.hostHeader).isEqualTo(PanelHostHeader.EMPTY);
                }
                view.setPanelHostHeader("panel", new PanelHostHeader("Valid", false, List.of(
                    new PanelHostHeader.Action("Valid", ARTWORK, () -> calls.add("valid"), new PanelHostHeader.Artwork("owner:glyph", "M2 2 L14 2")))));
                var button = named(view, "Valid"); button.doClick(0); assertThat(calls).containsExactly("valid");
                var registry = header.contributed.getFirst().hostArtwork.icons;
                ctx.binding().publish(new ThemePair(KitFixtures.light(), KitFixtures.light()));
                assertThat(named(view, "Valid")).isSameAs(button);
                view.close(); assertThat(button.getActionListeners()).isEmpty();
                assertThatIllegalStateException().isThrownBy(registry::names);
            } finally { view.close(); release(ctx); }
        });
    }

    @Test void semanticArtworkRefreshesTintAndPathWhileRawMulticolorArtworkStaysUntouched() {
        Edt.run(() -> {
            var ctx=KitFixtures.context();var calls=new ArrayList<String>();
            javax.swing.Icon multicolor=new javax.swing.Icon(){
                public int getIconWidth(){return 16;} public int getIconHeight(){return 16;}
                public void paintIcon(Component c,Graphics g,int x,int y){g.setColor(Color.RED);g.fillRect(x,y,8,16);g.setColor(new Color(0,0,255,128));g.fillRect(x+8,y,8,16);}
            };
            try(var view=new KitView(ctx,spec("panel",6),e->{})) {
                var first=new PanelHostHeader.Artwork("owner:glyph","M2 2 L14 2");
                view.setPanelHostHeader("panel",new PanelHostHeader("",false,List.of(
                    new PanelHostHeader.Action("Semantic",ARTWORK,()->calls.add("first"),first),
                    new PanelHostHeader.Action("Raw",multicolor,()->calls.add("raw")))));
                var semantic=named(view,"Semantic");var raw=named(view,"Raw");
                assertThat(raw.getIcon()).isSameAs(multicolor);
                assertThat(raw.getIcon().getIconWidth()).isEqualTo(16);
                assertThat(iconImage(raw.getIcon()).getRGB(2,2)).isEqualTo(Color.RED.getRGB());
                assertThat(iconImage(raw.getIcon()).getRGB(10,2)).isEqualTo(new Color(0,0,255,128).getRGB());
                assertThat(semantic.getIcon().getIconWidth()).isEqualTo(s(18));
                var before=iconImage(semantic.getIcon());
                ctx.binding().publish(new ThemePair(KitFixtures.light(),KitFixtures.light()));
                assertThat(named(view,"Semantic")).isSameAs(semantic);
                assertThat(iconImage(semantic.getIcon()).getRGB(0,0,before.getWidth(),before.getHeight(),null,0,before.getWidth())).isNotEqualTo(before.getRGB(0,0,before.getWidth(),before.getHeight(),null,0,before.getWidth()));
                assertThat(raw.getIcon()).isSameAs(multicolor);
                var second=new PanelHostHeader.Artwork("owner:glyph","M2 14 L14 14");
                view.setPanelHostHeader("panel",new PanelHostHeader("",false,List.of(new PanelHostHeader.Action("Semantic",ARTWORK,()->calls.add("second"),second))));
                assertThat(named(view,"Semantic")).isSameAs(semantic);
                var replaced=iconImage(semantic.getIcon());
                assertThat(replaced.getRGB(s(8),s(2))>>>24).isZero();
                assertThat(replaced.getRGB(s(8),s(16))>>>24).isPositive();
                semantic.doClick(0);assertThat(calls).containsExactly("second");
            } finally {release(ctx);}
        });
    }
    @Test void builtinHostArtworkUsesTheReceivingAppearanceEvenWithAnIdenticalPalette() {
        Edt.run(() -> {
            var ctx=KitFixtures.context();var light=KitFixtures.light();
            var dark=new dev.jasper.ui.theme.GeneratedTheme(light.name(),true,light.author(),light.tokens(),light.syntax(),light.terminal(),light.uiKeys(),light.icons(),light.glass(),light.semantic());
            try(var view=new KitView(ctx,spec("panel",6),e->{})) {
                view.setPanelHostHeader("panel",new PanelHostHeader("",false,List.of(new PanelHostHeader.Action("Network",ARTWORK,()->{},new PanelHostHeader.Artwork("network",null)))));
                ctx.binding().publish(new ThemePair(light,light));
                var button=named(view,"Network");var before=iconImage(button.getIcon());
                ctx.binding().publish(new ThemePair(dark,dark));
                var after=iconImage(button.getIcon());
                assertThat(after.getRGB(0,0,after.getWidth(),after.getHeight(),null,0,after.getWidth()))
                    .isNotEqualTo(before.getRGB(0,0,before.getWidth(),before.getHeight(),null,0,before.getWidth()));
            } finally {release(ctx);}
        });
    }
    private static BufferedImage iconImage(javax.swing.Icon icon) {
        var image=new BufferedImage(icon.getIconWidth(),icon.getIconHeight(),BufferedImage.TYPE_INT_ARGB);
        var graphics=image.createGraphics();try{icon.paintIcon(null,graphics,0,0);}finally{graphics.dispose();}return image;
    }

    @Test
    void detailDotAndContributedActionsShareTheOneHeaderRowBeforeTheHostActions() {
        Edt.run(() -> {
            var ctx = KitFixtures.context();
            var plugin = new ArrayList<UiEvent>();
            var calls = new ArrayList<String>();
            try (var view = new KitView(ctx, spec("panel", 6), plugin::add)) {
                view.setPanelHostActions("panel", List.of(ghost("host.hide", "Hide")), e -> calls.add("host:" + e));
                view.setPanelHostHeader("panel", new PanelHostHeader("prod", true, List.of(
                    new PanelHostHeader.Action("New session", ARTWORK, () -> calls.add("new")),
                    new PanelHostHeader.Action("Import sessions", ARTWORK, () -> calls.add("import")))));
                view.setSize(s(420), s(200));
                layout(view);
                var header = view.panelHeader("panel");
                var palette = ctx.theme().semantic();

                var detail = detail(view, "panel");
                assertThat(detail).isNotNull();
                assertThat(SwingUtilities.isDescendingFrom(detail, header)).as("the detail lives in the borrowed header").isTrue();
                assertThat(detail.isVisible()).isTrue();
                assertThat(detail.getText()).isEqualTo("prod");
                assertThat(detail.getToolTipText()).isEqualTo("prod");
                assertThat(detail.getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
                assertThat(detail.getForeground()).as("the count role's colour").isEqualTo(palette.color(SemanticColor.TEXT_SECONDARY));
                assertThat(detail.getFont()).isEqualTo(ThemePainting.font(ctx, Typography.Role.PANEL_HEADER_COUNT));
                assertThat(detail.getIcon().getIconWidth()).isEqualTo(ThemePainting.metric(ctx, DesignMetrics.Role.STATUS_DOT));
                var dot = dotColour(detail);
                assertThat(dot).isEqualTo(SurfaceContrast.on(palette, SemanticColor.STATUS_SUCCESS, SemanticColor.SURFACE_PANEL,
                    SurfaceContrast.INDICATOR));
                assertThat(ColorMath.contrast(dot, palette.color(SemanticColor.SURFACE_PANEL))).isGreaterThanOrEqualTo(3);

                var add = named(header, "Add");
                var created = named(header, "New session");
                var imported = named(header, "Import sessions");
                var hide = named(header, "Hide");
                assertThat(List.of(add, created, imported, hide)).doesNotContainNull();
                for (var contributed : List.of(created, imported)) {
                    assertThat(contributed.getText()).isNullOrEmpty();
                    assertThat(contributed.getIcon()).isSameAs(ARTWORK);
                    assertThat(contributed.getToolTipText()).isEqualTo(contributed.getAccessibleContext().getAccessibleName());
                    assertThat(contributed.isFocusable()).isTrue();
                }
                int[] x = List.of(add, created, imported, hide).stream()
                    .mapToInt(b -> SwingUtilities.convertPoint(b.getParent(), b.getLocation(), header).x).toArray();
                assertThat(x).as("plugin action, contributed actions, then host actions").isSorted();
                assertThat(x[0]).isLessThan(x[1]);
                assertThat(x[2]).isLessThan(x[3]);
                var title = find(view, "panel-header.title", JLabel.class);
                var count = find(view, "panel-header.count", JLabel.class);
                int detailX = SwingUtilities.convertPoint(detail.getParent(), detail.getLocation(), header).x;
                assertThat(SwingUtilities.convertPoint(title.getParent(), title.getLocation(), header).x)
                    .isLessThan(SwingUtilities.convertPoint(count.getParent(), count.getLocation(), header).x);
                assertThat(SwingUtilities.convertPoint(count.getParent(), count.getLocation(), header).x).isLessThan(detailX);
                assertThat(detailX + detail.getWidth()).as("the detail stops before the actions").isLessThanOrEqualTo(x[0]);
                assertThat(detail.getWidth()).isPositive();

                created.doClick(0);
                imported.doClick(0);
                assertThat(calls).containsExactly("new", "import");
                assertThat(plugin).as("host metadata never reaches the plugin's event callback").isEmpty();
            } finally {
                release(ctx);
            }
        });
    }

    @Test
    void actionsKeepTheirControlsAcrossUpdatesAndAreReleasedOnceWhenCleared() {
        Edt.run(() -> {
            var ctx = KitFixtures.context();
            var calls = new ArrayList<String>();
            try (var view = new KitView(ctx, spec("panel", 3), e -> {})) {
                view.setPanelHostHeader("panel", new PanelHostHeader("5", false, List.of(
                    new PanelHostHeader.Action("New session", ARTWORK, () -> calls.add("first")),
                    new PanelHostHeader.Action("Import sessions", ARTWORK, () -> calls.add("import")))));
                var header = view.panelHeader("panel");
                var created = named(header, "New session");
                var imported = named(header, "Import sessions");
                assertThat(detail(view, "panel").getIcon()).as("inactive: no dot").isNull();
                view.setPanelHostHeader("panel", new PanelHostHeader("6", true, List.of(
                    new PanelHostHeader.Action("New session", ARTWORK, () -> calls.add("second")),
                    new PanelHostHeader.Action("Import sessions", ARTWORK, () -> calls.add("import 2")))));
                assertThat(named(header, "New session")).as("a metadata refresh keeps the control").isSameAs(created);
                assertThat(detail(view, "panel").getText()).isEqualTo("6");
                assertThat(detail(view, "panel").getIcon()).isNotNull();
                created.doClick(0);
                assertThat(calls).containsExactly("second");

                view.update(spec("panel", 9));
                assertThat(view.panelHeader("panel")).isSameAs(header);
                assertThat(named(header, "New session")).as("a descriptor update keeps host metadata").isSameAs(created);
                assertThat(detail(view, "panel").getText()).isEqualTo("6");
                assertThat(detail(view, "panel").isVisible()).isTrue();

                view.setPanelHostHeader("panel", new PanelHostHeader("6", true, List.of(
                    new PanelHostHeader.Action("New session", ARTWORK, () -> calls.add("third")))));
                assertThat(named(header, "Import sessions")).isNull();
                assertThat(imported.getActionListeners()).as("a removed action is released").isEmpty();
                imported.doClick(0);

                view.setPanelHostHeader("panel", PanelHostHeader.EMPTY);
                assertThat(named(header, "New session")).isNull();
                assertThat(created.getActionListeners()).isEmpty();
                created.doClick(0);
                assertThat(detail(view, "panel").isVisible()).as("an empty header shows no detail").isFalse();
                assertThat(calls).containsExactly("second");
            } finally {
                release(ctx);
            }
        });
    }

    @Test
    void themeChangesRestyleTheDetailAndDot() {
        Edt.run(() -> {
            var ctx = KitFixtures.context();
            try (var view = new KitView(ctx, spec("panel", 1), e -> {})) {
                view.setPanelHostHeader("panel", new PanelHostHeader("prod", true, List.of()));
                var light = KitFixtures.light();
                ctx.binding().publish(new ThemePair(light, light));
                var palette = light.semantic();
                var detail = detail(view, "panel");
                assertThat(detail.getText()).isEqualTo("prod");
                assertThat(detail.getForeground()).isEqualTo(palette.color(SemanticColor.TEXT_SECONDARY));
                assertThat(dotColour(detail)).isEqualTo(SurfaceContrast.on(palette, SemanticColor.STATUS_SUCCESS,
                    SemanticColor.SURFACE_PANEL, SurfaceContrast.INDICATOR));
            } finally {
                release(ctx);
            }
        });
    }

    @Test
    void aReplacementHeaderStartsEmptyAndClosingReleasesContributedActions() {
        Edt.run(() -> {
            var ctx = KitFixtures.context();
            var calls = new ArrayList<String>();
            var view = new KitView(ctx, spec("panel", 1), e -> {});
            try {
                view.setPanelHostHeader("panel", new PanelHostHeader("prod", false, List.of(
                    new PanelHostHeader.Action("New session", ARTWORK, () -> calls.add("old")))));
                var old = named(view.panelHeader("panel"), "New session");
                view.update(spec("replacement", 1));
                assertThat(old.getActionListeners()).as("the replaced header released its actions").isEmpty();
                assertThat(named(view.panelHeader("replacement"), "New session")).isNull();
                assertThat(detail(view, "replacement").isVisible()).isFalse();
                view.setPanelHostHeader("replacement", new PanelHostHeader("", false, List.of(
                    new PanelHostHeader.Action("New session", ARTWORK, () -> calls.add("new")))));
                var current = named(view.panelHeader("replacement"), "New session");
                view.close();
                assertThat(current.getActionListeners()).isEmpty();
                current.doClick(0);
                old.doClick(0);
                assertThat(calls).isEmpty();
                assertThatIllegalStateException().isThrownBy(() -> view.setPanelHostHeader("replacement", PanelHostHeader.EMPTY));
            } finally {
                view.close();
                release(ctx);
            }
        });
    }

    @Test
    void theDescriptionIsImmutableAndRequiresLabelledActions() {
        var actions = new ArrayList<PanelHostHeader.Action>();
        actions.add(new PanelHostHeader.Action("New", ARTWORK, () -> {}));
        var header = new PanelHostHeader("1", false, actions);
        actions.clear();
        assertThat(header.actions()).hasSize(1);
        assertThat(PanelHostHeader.EMPTY).isEqualTo(new PanelHostHeader("", false, List.of()));
        assertThatNullPointerException().isThrownBy(() -> new PanelHostHeader(null, false, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new PanelHostHeader.Action(" ", ARTWORK, () -> {}));
        assertThatNullPointerException().isThrownBy(() -> new PanelHostHeader.Action("New", null, () -> {}));
        assertThatNullPointerException().isThrownBy(() -> new PanelHostHeader.Action("New", ARTWORK, null));
    }
}
