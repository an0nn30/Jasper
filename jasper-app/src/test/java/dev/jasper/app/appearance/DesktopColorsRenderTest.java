package dev.jasper.app.appearance;

import dev.jasper.app.platform.DesktopEnvironment;
import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.platform.DesktopObservation;
import dev.jasper.app.platform.DesktopRenderFixtures;
import dev.jasper.app.settingsui.*;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.gallery.KitGallery;
import dev.jasper.ui.model.*;
import dev.jasper.ui.swing.*;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.*;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class DesktopColorsRenderTest {
    @Test void desktopFallbackPreservesContentPalettes() {
        var portal = new DesktopObservation.Portal(1, DesktopObservation.Polarity.DARK,
            Optional.empty(), Optional.of(false), "");
        var result = DesktopPalette.resolve(true, DesktopPalette.SourceMode.AUTO, DesktopEnvironment.from(Map.of("XDG_CURRENT_DESKTOP", "GNOME")),
            new DesktopObservation(portal, Map.of()));
        var bundled = JasperTheme.bundled().generated(true);
        assertThat(result.theme().terminal()).isEqualTo(bundled.terminal());
        assertThat(result.theme().editor()).isEqualTo(bundled.editor());
        assertThat(result.theme().syntax()).isEqualTo(bundled.syntax());
    }

    @Test void nativeFixtureMatrixCoversBothAppearancesAndEveryRequiredSourceShape() throws Exception {
        var fixtures = DesktopRenderFixtures.read();
        var gtk = fixtures.stream().filter(f -> f.input().palettes().keySet().stream().anyMatch(source ->
            source == DesktopObservation.Source.GTK3 || source == DesktopObservation.Source.GTK4)).toList();
        assertThat(gtk).hasSize(18);
        assertThat(gtk.stream().map(DesktopRenderFixtures.Fixture::name).distinct()).containsExactly(
            "gtk3", "libadwaita-override", "plain-gtk4", "partial-source", "mismatched-appearance",
            "low-contrast-shared-text", "transparent-input", "white-accent", "yellow-high-contrast");
        for (String source : List.of("qt5", "qt6", "kde5", "kde6")) {
            for (boolean dark : List.of(false, true)) for (boolean high : List.of(false, true)) {
                var fixture = fixtures.stream().filter(f -> f.name().equals(source + (high ? "-high-contrast" : ""))
                    && f.dark() == dark).findFirst();
                assertThat(fixture).as("native %s dark=%s high=%s must reach the actual paint inventory", source, dark, high).isPresent();
                assertThat(fixture.orElseThrow().high()).isEqualTo(high);
                var palette = fixture.orElseThrow().input().palettes().values().iterator().next();
                assertThat(palette.source().name().toLowerCase(Locale.ROOT)).isEqualTo(source);
                assertThat(palette.appearance()).isEqualTo(dark ? DesktopObservation.Polarity.DARK : DesktopObservation.Polarity.LIGHT);
                var ground = dark ? new DesktopObservation.Rgba(.08,.09,.1,1) : new DesktopObservation.Rgba(.96,.97,.98,1);
                var ink = dark ? new DesktopObservation.Rgba(1,1,1,1) : new DesktopObservation.Rgba(0,0,0,1);
                assertThat(palette.colors().get(DesktopObservation.Pair.WINDOW)).isEqualTo(new DesktopObservation.Colors(ground, ink));
                assertThat(palette.colors().get(DesktopObservation.Pair.DIALOG)).isEqualTo(new DesktopObservation.Colors(ground, ink));
                assertThat(palette.colors().get(DesktopObservation.Pair.VIEW)).isEqualTo(new DesktopObservation.Colors(ground, ink));
                assertThat(palette.colors().get(DesktopObservation.Pair.POPOVER)).isEqualTo(new DesktopObservation.Colors(ground, ink));
                var selectionGround = dark ? new DesktopObservation.Rgba(.8,.85,.9,1) : new DesktopObservation.Rgba(.1,.15,.2,1);
                var selectionInk = dark ? new DesktopObservation.Rgba(0,0,0,1) : new DesktopObservation.Rgba(1,1,1,1);
                assertThat(palette.colors().get(DesktopObservation.Pair.SELECTION)).isEqualTo(new DesktopObservation.Colors(selectionGround, selectionInk));
                assertThat(palette.colors()).doesNotContainKey(DesktopObservation.Pair.SIDEBAR);
                if (source.startsWith("qt")) assertThat(palette.colors()).doesNotContainKey(DesktopObservation.Pair.HEADER);
                else assertThat(palette.colors().get(DesktopObservation.Pair.HEADER)).isEqualTo(new DesktopObservation.Colors(ground, ink));
                var resolved = DesktopPalette.resolve(dark, DesktopPalette.SourceMode.AUTO,
                    DesktopEnvironment.from(Map.of("XDG_CURRENT_DESKTOP", "KDE")), fixture.orElseThrow().input());
                assertThat(resolved.source()).contains(palette.source());
                assertThat(SemanticColor.SURFACE_APP.color(resolved.theme())).isEqualTo(
                    dark ? new Color(20,23,26) : new Color(245,247,250));
            }
        }
        for (String shape : List.of("qt-incomplete-optionals", "qt-translucent-ink", "kde-shared-role-conflict", "all-sources-unavailable"))
            for (boolean dark : List.of(false, true)) assertThat(fixtures.stream().filter(f -> f.name().equals(shape) && f.dark() == dark))
                .as("%s dark=%s actual paint input", shape, dark).hasSize(1);
        assertThat(fixtures.stream().map(f -> f.name()+"/"+f.dark())).doesNotHaveDuplicates();
    }

    @Test void qtOptionalPairsAndTranslucentInkNormalizeBeforePainting() throws Exception {
        for (var fixture : DesktopRenderFixtures.read()) {
            var result = DesktopPalette.resolve(fixture.dark(), DesktopPalette.SourceMode.AUTO,
                DesktopEnvironment.from(Map.of("XDG_CURRENT_DESKTOP", "KDE")), fixture.input());
            var theme = result.theme();
            if (fixture.name().equals("qt-incomplete-optionals")) {
                var palette = fixture.input().palettes().get(DesktopObservation.Source.QT6);
                assertThat(palette.colors()).containsOnlyKeys(DesktopObservation.Pair.WINDOW, DesktopObservation.Pair.DIALOG);
                assertThat(result.source()).contains(DesktopObservation.Source.QT6);
                assertThat(SemanticColor.SURFACE_INPUT.color(theme)).isEqualTo(SemanticColor.SURFACE_APP.color(theme));
                assertThat(SemanticColor.SURFACE_MENU.color(theme)).isEqualTo(SemanticColor.SURFACE_APP.color(theme));
            }
            if (fixture.name().equals("qt-translucent-ink")) {
                var palette = fixture.input().palettes().get(DesktopObservation.Source.QT5);
                assertThat(palette.colors().get(DesktopObservation.Pair.WINDOW).foreground().alpha()).isEqualTo(.75);
                assertThat(result.source()).contains(DesktopObservation.Source.QT5);
                assertThat(SemanticColor.TEXT_PRIMARY.color(theme).getAlpha()).isEqualTo(255);
                assertThat(ColorMath.contrast(SemanticColor.TEXT_PRIMARY.color(theme), SemanticColor.SURFACE_APP.color(theme))).isGreaterThanOrEqualTo(4.5);
            }
            if (fixture.name().equals("all-sources-unavailable")) {
                assertThat(fixture.input().palettes()).isEmpty();
                assertThat(result.source()).isEmpty();
                assertThat(result.status()).contains("unavailable", "Slate Cobalt");
            }
        }
    }

    @Test void nativePalettesPaintFormsMenusTablesTreesTabsAndDialogsInBothKits() throws Exception {
        var previous = UIManager.getLookAndFeel();
        var output = Files.createDirectories(Path.of("build/desktop-colors"));
        var report = new ArrayList<String>();
        report.add("fixture\tdark\thigh\tpair\tink\tbackground\tratio\tminimum");
        var catalog = KitGallery.fixtures().stream().filter(f -> Set.of(
            "add-host", "picker-menu", "tables-0-18", "tabs", "sessions", "window-form", "notice-severities", "properties").contains(f.name())).toList();
        assertThat(catalog).hasSize(8);
        try {
            for (var fixture : DesktopRenderFixtures.read()) {
                var resolved = DesktopPalette.resolve(fixture.dark(), DesktopPalette.SourceMode.AUTO, DesktopEnvironment.from(Map.of("XDG_CURRENT_DESKTOP", "KDE")), fixture.input());
                if (fixture.name().equals("mismatched-appearance") || fixture.name().equals("all-sources-unavailable")) assertThat(resolved.source()).isEmpty();
                else assertThat(resolved.source()).contains(fixture.input().palettes().keySet().iterator().next());
                var theme = resolved.theme();
                assertThat(DesktopPaintPairs.validate(theme, fixture.high())).as(fixture.name()).isEmpty();
                var modeled = new LinkedHashSet<String>();
                for (var pair : DesktopPaintPairs.all()) {
                    double ratio = ColorMath.contrast(pair.foreground(theme), pair.background(theme));
                    assertThat(ratio).as(fixture.name()+" "+pair.name()).isGreaterThanOrEqualTo(pair.minimum(fixture.high()));
                    modeled.add(pair.name());
                    report.add(String.format(Locale.ROOT, "%s\t%s\t%s\t%s\t%08x\t%08x\t%.6f\t%.1f",
                        fixture.name(), fixture.dark(), fixture.high(), "constraint/"+pair.name(), pair.foreground(theme).getRGB(),
                        pair.background(theme).getRGB(), ratio, pair.minimum(fixture.high())));
                }
                var inventory = resource("desktop-paint-pairs.tsv").stream().skip(1).map(row -> row.split("\t")[0]).toList();
                assertThat(modeled).as("resolver constraint catalog; rendered coverage is checked separately").containsExactlyElementsOf(inventory);
                var roles = new LinkedHashSet<String>();
                for (var role : SemanticColor.values()) { assertThat(role.color(theme)).isNotNull(); roles.add(role.key()); }
                for (var role : DerivedColor.values()) if (role.overridable()) { assertThat(role.color(theme)).isNotNull(); roles.add(role.key()); }
                assertThat(roles).containsExactlyElementsOf(resource("desktop-role-map.tsv").stream().skip(1).map(row -> row.split("\t")[0]).toList());
                for (var kit : List.of(dev.jasper.ui.kit.Kit.DEFAULT, dev.jasper.ui.kit.Kit.CLASSIC)) {
                    FlatLafInstaller.install(theme, kit);
                    var context = KitFixtures.context(theme, kit);
                    String stem = fixture.name()+"-"+(fixture.dark()?"dark":"light")+"-"+kit.id();
                    try {
                        var ownerSamples=DesktopOwnerPaints.measure(context,fixture.high(),stem,output);
                        DesktopPaintCoverage.verify(kit.id(),fixture.high(),ownerSamples,output.resolve(stem+"-coverage.tsv"));
                        for (var scene : catalog) try (var view = new KitView(context, scene.spec(), ignored -> {})) {
                            view.paintsSurface(true); // The actual workspace-content host paints the route panel.
                            var image = KitFixtures.render(view, scene.width(), scene.height(), 1);
                            int labels=paintedLabels(view,fixture.high(),stem+"/"+scene.name(),report);
                            long custom=ownerSamples.stream().filter(s->scene.name().equals("picker-menu")?s.owner().equals("picker-row"):scene.name().equals("tabs")&&s.owner().equals("tabs")).count();
                            assertThat(labels+custom).as(stem+" "+scene.name()+" actual measured owner states").isPositive();
                            ImageIO.write(image, "png", output.resolve(stem+"-"+scene.name()+".png").toFile());
                            assertThat(Arrays.stream(image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth())).distinct().count())
                                .as(stem+" "+scene.name()+" paints visible content").isGreaterThan(12);
                        }
                        settings(context, stem, output, fixture.high(), report);
                        paintControls(context, theme, fixture.high(), stem, output, report);
                    } finally { context.icons().close(); context.binding().close(); }
                }
                var bundled = JasperTheme.bundled().generated(fixture.dark());
                assertThat(theme.terminal()).isEqualTo(bundled.terminal());
                assertThat(theme.editor()).isEqualTo(bundled.editor());
                assertThat(theme.syntax()).isEqualTo(bundled.syntax());
            }
        } finally {
            UIManager.setLookAndFeel(previous);
            Files.write(output.resolve("measured-pairs.tsv"), report);
        }
    }

    @Test void coverageCannotPassWithoutTheRenderedOwnersEvenWhenTheResolverIsValid() throws Exception {
        assertThat(DesktopPaintCoverage.entries()).hasSize(307);
        org.assertj.core.api.Assertions.assertThatThrownBy(()->DesktopPaintCoverage.verify("default",false,List.of(),Path.of("build/desktop-colors/missing-owner.tsv")))
            .isInstanceOf(AssertionError.class).hasMessageContaining("requires actual default pixels");
    }

    @Test void hoveredNoticeLinksRetainHighContrastOnTheirPaintedTint() throws Exception {
        var fixture=DesktopRenderFixtures.read().stream().filter(f->f.name().equals("yellow-high-contrast")&&!f.dark()).findFirst().orElseThrow();
        var theme=DesktopPalette.resolve(false,DesktopPalette.SourceMode.AUTO,DesktopEnvironment.from(Map.of("XDG_CURRENT_DESKTOP", "GNOME")),fixture.input()).theme();
        var previous=UIManager.getLookAndFeel();
        try {
            for(var kit:List.of(dev.jasper.ui.kit.Kit.DEFAULT,dev.jasper.ui.kit.Kit.CLASSIC)) {
                FlatLafInstaller.install(theme,kit);var context=KitFixtures.context(theme,kit);
                try {
                    var samples=new ArrayList<DesktopOwnerPaints.Sample>();DesktopRolePaints.measure(context,true,samples);
                    for(var sample:samples)if(sample.owner().equals("notice")&&sample.state().endsWith("-hover"))
                        assertThat(sample.ratio()).as(kit.id()+" "+sample.key()+" ink="+Integer.toHexString(sample.ink())+" background="+Integer.toHexString(sample.background())).isGreaterThanOrEqualTo(7);
                } finally {context.icons().close();context.binding().close();}
            }
        } finally {UIManager.setLookAndFeel(previous);}
    }

    @Test void lowContrastImportedSurfaceWithSharedTextIsRejectedBeforePainting() throws Exception {
        for (var fixture : DesktopRenderFixtures.read()) if (fixture.name().equals("low-contrast-shared-text") || fixture.name().equals("kde-shared-role-conflict")) {
            var result = DesktopPalette.resolve(fixture.dark(), DesktopPalette.SourceMode.AUTO,
                DesktopEnvironment.from(Map.of("XDG_CURRENT_DESKTOP", "KDE")), fixture.input());
            assertThat(result.source()).contains(fixture.input().palettes().keySet().iterator().next());
            var theme = result.theme();
            assertThat(ColorMath.contrast(SemanticColor.TEXT_PRIMARY.color(theme),
                SemanticColor.SURFACE_DIALOG.color(theme))).isGreaterThanOrEqualTo(4.5);
            assertThat(SemanticColor.SURFACE_DIALOG.color(theme)).isNotEqualTo(fixture.dark() ? new Color(230,230,230) : new Color(26,26,26));
        }
    }

    @Test void transparentNativeInputResolvesToAnOpaqueReadablePaintSurface() throws Exception {
        for (var fixture : DesktopRenderFixtures.read()) if (fixture.name().equals("transparent-input")) {
            var theme = DesktopPalette.resolve(fixture.dark(), DesktopPalette.SourceMode.AUTO, DesktopEnvironment.from(Map.of()), fixture.input()).theme();
            var surface = SemanticColor.SURFACE_INPUT.color(theme);
            assertThat(surface.getAlpha()).isEqualTo(255);
            assertThat(ColorMath.contrast(SemanticColor.TEXT_PRIMARY.color(theme), surface)).isGreaterThanOrEqualTo(4.5);
        }
    }

    @Test void whiteAndYellowPortalAccentsKeepEnabledTextReadableAfterPaintGuards() throws Exception {
        for (var fixture : DesktopRenderFixtures.read()) if (fixture.name().contains("accent") || fixture.high()) {
            var theme = DesktopPalette.resolve(fixture.dark(), DesktopPalette.SourceMode.AUTO, DesktopEnvironment.from(Map.of()), fixture.input()).theme();
            assertThat(DesktopPaintPairs.validate(theme, fixture.high())).isEmpty();
            assertThat(ColorMath.contrast(SemanticColor.TEXT_ONACCENT.color(theme),
                SemanticColor.ACCENT.color(theme))).isGreaterThanOrEqualTo(fixture.high()?7:4.5);
        }
    }

    private static List<String> resource(String name) throws Exception {
        try (var stream = DesktopColorsRenderTest.class.getResourceAsStream(name)) {
            return new String(Objects.requireNonNull(stream).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines().toList();
        }
    }

    private static void paintControls(KitContext context, GeneratedTheme theme,
            boolean high, String name, Path output, List<String> report) throws Exception {
        var ready = ControlState.READY;
        var nodes = new ArrayList<Nodes.Node>();
        for (var kind : List.of(Nodes.ButtonKind.PRIMARY, Nodes.ButtonKind.SECONDARY))
            nodes.add(new Nodes.Button(kind.name(), ready, "MMMM", "", kind, "go", ""));
        nodes.add(new Nodes.Button("disabled", new ControlState(true,false,false,""), "Disabled", "",
            Nodes.ButtonKind.SECONDARY, "go", ""));
        nodes.add(new Nodes.PickerMenu("picker", ready, "MMMM", "Search", List.of(), "", "", "No matches", "Create", "create", "cancel"));
        try (var view = new KitView(context, new ViewSpec(new Nodes.Stack("controls", ready,
                Nodes.LayoutRole.VERTICAL, nodes)), ignored -> {})) {
            KitFixtures.render(view, 400, 300, 1);
            for (String id : List.of("PRIMARY","SECONDARY")) {
                var button = KitTestSupport.require(view,id,JButton.class);
                button.setSize(180,32);
                for (boolean hover : List.of(false,true)) {
                    button.getModel().setRollover(hover);
                    var image = paint(button); button.setText(""); var blank = paint(button); button.setText("MMMM");
                    var measured = glyphContrast(image,blank);
                    double ratio = measured.ratio();
                    assertThat(ratio).as(name+" "+id+" hover="+hover).isGreaterThanOrEqualTo(high?7:4.5);
                    report.add(name+"\t"+theme.dark()+"\t"+high+"\tpixel/"+id+"/hover="+hover+"\t"+Integer.toHexString(measured.ink())+"\t"+Integer.toHexString(measured.background())+"\t"+ratio+"\t"+(high?7:4.5));
                    ImageIO.write(image,"png",output.resolve(name+"-"+id+"-"+hover+".png").toFile());
                }
            }
            var disabled = KitTestSupport.require(view,"disabled",JButton.class);
            assertThat(disabled.isEnabled()).isFalse();
            report.add(name+"\t"+theme.dark()+"\t"+high+"\tdisabled/button\texcluded\texcluded\tNA\tdisabled-text");
            var search = KitTestSupport.require(view,"picker.search",JTextField.class);
            search.setSize(220,32); search.selectAll(); search.getCaret().setSelectionVisible(true);
            var selected = paint(search);
            var pixels = selected.getRGB(0,0,selected.getWidth(),selected.getHeight(),null,0,selected.getWidth());
            for (var color : List.of(search.getSelectedTextColor(),search.getSelectionColor()))
                assertThat(Arrays.stream(pixels).filter(p -> p == color.getRGB()).count()).as(name+" selected text paint").isGreaterThan(8);
            assertThat(ColorMath.contrast(search.getSelectedTextColor(),search.getSelectionColor())).isGreaterThanOrEqualTo(high?7:4.5);
            ImageIO.write(selected,"png",output.resolve(name+"-selection.png").toFile());
        }
    }

    private static BufferedImage paint(JComponent component) {
        var image = new BufferedImage(component.getWidth()*4,component.getHeight()*4,BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try { g.setColor(component.getBackground()); g.fillRect(0,0,image.getWidth(),image.getHeight()); g.scale(4,4); component.paint(g); }
        finally { g.dispose(); }
        return image;
    }
    private record PaintedPair(int ink, int background, double ratio) { }
    private static PaintedPair glyphContrast(BufferedImage image, BufferedImage blank) {
        var counts = new HashMap<Integer,Integer>();
        for(int y=8;y<image.getHeight()-8;y++) for(int x=8;x<image.getWidth()-8;x++)
            if(image.getRGB(x,y)!=blank.getRGB(x,y)) counts.merge(image.getRGB(x,y),1,Integer::sum);
        var ink = counts.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow();
        assertThat(ink.getValue()).isGreaterThan(8);
        double minimum = 21; int ground = 0;
        for(int y=8;y<image.getHeight()-8;y++) for(int x=8;x<image.getWidth()-8;x++)
            if(image.getRGB(x,y)==ink.getKey() && image.getRGB(x,y)!=blank.getRGB(x,y)) {
                double ratio=ColorMath.contrast(new Color(ink.getKey()),new Color(blank.getRGB(x,y)));
                if(ratio<minimum){minimum=ratio;ground=blank.getRGB(x,y);}
            }
        return new PaintedPair(ink.getKey(),ground,minimum);
    }
    /** Paint the owner's whole tree clipped to a label, so backgrounds include real containers and selection. */
    private static BufferedImage labelPaint(JComponent root, JLabel label) {
        var origin = SwingUtilities.convertPoint(label,0,0,root);
        var image = new BufferedImage(label.getWidth()*4,label.getHeight()*4,BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();
        try {g.setColor(root.getBackground());g.fillRect(0,0,image.getWidth(),image.getHeight());g.scale(4,4);g.translate(-origin.x,-origin.y);root.paint(g);}
        finally {g.dispose();} return image;
    }
    private static void labels(Component component, List<JLabel> labels) {
        if(component instanceof JLabel label) labels.add(label);
        if(component instanceof Container container) for(var child:container.getComponents()) labels(child,labels);
    }
    private static int paintedLabels(JComponent root, boolean high, String name, List<String> report) {
        var labels=new ArrayList<JLabel>(); labels(root,labels);
        int measured=0;
        for(var label:labels) {
            if(label.getText()==null || label.getText().isBlank() || label.getWidth()<5 || label.getHeight()<5 || !label.isVisible()) continue;
            boolean enabled=true;
            for(Component parent=label;parent!=null;parent=parent.getParent()) enabled &= parent.isEnabled();
            if(!enabled) {report.add(name+"\tNA\t"+high+"\tpixel/"+label.getName()+"\texcluded\texcluded\tNA\tdisabled-text");continue;}
            var image=labelPaint(root,label);String text=label.getText();label.setText("");var blank=labelPaint(root,label);label.setText(text);
            if(Arrays.equals(image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth()),
                blank.getRGB(0,0,blank.getWidth(),blank.getHeight(),null,0,blank.getWidth()))) continue; // clipped/offscreen row
            var pair=glyphContrast(image,blank);double ratio=pair.ratio();
            assertThat(ratio).as(name+" actual label "+label.getName()+" ["+text+"]").isGreaterThanOrEqualTo(high?7:4.5);
            report.add(name+"\tNA\t"+high+"\tpixel/"+label.getName()+"\t"+Integer.toHexString(pair.ink())+"\t"+Integer.toHexString(pair.background())+"\t"+ratio+"\t"+(high?7:4.5));measured++;
        }
        return measured;
    }

    private static void settings(KitContext context, String name, Path output,
            boolean high, List<String> report) throws Exception {
        var draft=new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"),"[ui.theme]\ndark='slate-cobalt'\nlight='slate-cobalt'\n",true).values());
        var pageContext=new PageContext(draft,id->{},Runnable::run,(title,initial)->Optional.empty(),()->{},()->null,()->context);
        var actions=new CorePages.SystemActions(()->Path.of("config.toml"),()->{},()->{},()->{},()->{},()->true,Optional.empty());
        var page=CorePages.specs(AppearanceFixtures.CHOICES,actions).getFirst().build(pageContext);
        try {
            var component=page.component();
            var empty=new ArrayList<JPanel>();
            for(int i=0;i<5;i++){var panel=new JPanel();panel.setOpaque(false);empty.add(panel);}
            try(var shell=new SettingsShell(context,empty.get(0),empty.get(1),empty.get(2),empty.get(3),component,empty.get(4))) {
                shell.setSize(1000,1100);KitTestSupport.layout(shell);
                var image=new BufferedImage(1000,1100,BufferedImage.TYPE_INT_RGB);
                var g=image.createGraphics();try{shell.paint(g);}finally{g.dispose();}
                ImageIO.write(image,"png",output.resolve(name+"-settings.png").toFile());
                assertThat(paintedLabels(shell,high,name+"/settings",report)).as("Settings actual labels").isPositive();
            }
        } finally {page.dispose();}
    }
}
