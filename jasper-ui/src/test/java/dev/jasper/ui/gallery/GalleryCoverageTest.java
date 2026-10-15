package dev.jasper.ui.gallery;

import dev.jasper.ui.model.Nodes;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GalleryCoverageTest {
    @Test void everyNodeAndEnumVariantHasARealFixture() {
        var values = new HashSet<String>();
        KitGallery.fixtures().forEach(f -> collect(f.spec().root(), values));
        for (Class<?> type : Nodes.Node.class.getPermittedSubclasses())
            assertThat(values).as("Missing node: %s", type.getSimpleName()).contains(type.getSimpleName());
        for (Class<?> type : Nodes.class.getDeclaredClasses()) if (type.isEnum())
            for (Object value : type.getEnumConstants())
                assertThat(values).as("Missing variant: %s.%s", type.getSimpleName(), value).contains(type.getSimpleName() + "." + value);
    }

    @Test void allNineOriginalReferencesAndFourPanelsAreRepresented() {
        assertThat(KitGallery.fixtures().stream().map(KitGallery.Fixture::reference).filter(s -> !s.isEmpty()).distinct())
            .containsExactlyInAnyOrder("add-host.png", "unlock-vault.png", "new-file-server.png", "create-vault.png", "credential-vault.png", "vault-add-menu.png", "add-login-sheet.png", "add-ssh-key-sheet.png", "plugin-panels.png");
        assertThat(KitGallery.fixtures().stream().map(KitGallery.Fixture::name))
            .contains("sessions", "tunnels", "sftp", "fileservers", "sftp-connected", "sessions-filtered-empty", "fileservers-pending", "fileservers-failed", "fileservers-confirmed");
        assertThat(KitGallery.fixtures().stream().map(KitGallery.Fixture::state))
            .contains("normal", "hover", "focus", "pressed", "selected", "disabled", "error", "busy", "conditional", "empty", "filtered-empty", "motion-disabled", "stored-secret-masked", "stored-secret-revealed", "dropdown-open");
    }

    @Test void openedDropdownCaptureLaysOutRealListRows() {
        dev.jasper.ui.testsupport.Edt.run(() -> {
            var context=dev.jasper.ui.testsupport.KitFixtures.context();
            var fixture=KitGallery.fixtures().stream().filter(f -> f.name().equals("dropdown-open")).findFirst().orElseThrow();
            try(var view=new dev.jasper.ui.swing.KitView(context,fixture.spec(),e -> {})) {
                var pending=new java.util.ArrayDeque<java.awt.Component>();pending.add(view);javax.swing.JComboBox<?> combo=null;
                while(!pending.isEmpty()){var c=pending.removeFirst();if(c instanceof javax.swing.JComboBox<?> found){combo=found;break;}if(c instanceof java.awt.Container parent)Collections.addAll(pending,parent.getComponents());}
                assertThat(combo).isNotNull();var popup=(javax.swing.plaf.basic.BasicComboPopup)combo.getUI().getAccessibleChild(combo,0);
                KitGallery.paint(popup,240,100);assertThat(popup.getList().getWidth()).isPositive();assertThat(popup.getList().getHeight()).isGreaterThanOrEqualTo(84);
            }finally{context.icons().close();context.binding().close();}
        });
    }
    @Test void discreetFixturesExerciseEmptyPopulatedAndFocusedRevealPolicy() {
        dev.jasper.ui.testsupport.Edt.run(() -> {
            var prior = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager();
            var focus = new KitGallery.GalleryFocus();
            var context = dev.jasper.ui.testsupport.KitFixtures.context();
            java.awt.KeyboardFocusManager.setCurrentKeyboardFocusManager(focus);
            try {
                for (var fixture : KitGallery.fixtures().stream().filter(f -> f.name().startsWith("discreet-secret-")).toList()) {
                    try (var view = new dev.jasper.ui.swing.KitView(context, fixture.spec(), e -> {})) {
                        KitGallery.applyState(view, fixture, focus);
                        assertThat(KitGallery.find(view, "discreet-secret.reveal").isVisible())
                            .as(fixture.name()).isEqualTo(!fixture.state().equals("empty"));
                    } finally { focus.target = null; }
                }
            } finally {
                java.awt.KeyboardFocusManager.setCurrentKeyboardFocusManager(prior);
                context.icons().close(); context.binding().close();
            }
        });
    }

    @Test void missingCoverageDiagnosticNamesTheNode() {
        var values = new HashSet<String>();
        KitGallery.fixtures().forEach(f -> collect(f.spec().root(), values));
        values.remove("Secret");
        assertThatThrownBy(() -> assertThat(values).as("Missing node: Secret").contains("Secret"))
            .isInstanceOf(AssertionError.class).hasMessageContaining("Missing node: Secret");
    }

    @Test void shippedJarExcludesGalleryAndTestSupport() throws Exception {
        Path jar = Path.of(System.getProperty("jasper.uiJar", "build/libs/jasper-ui.jar"));
        assertThat(jar).as("production jar").exists();
        try (var archive = new JarFile(jar.toFile())) {
            assertThat(archive.stream().map(e -> e.getName()).toList())
                .noneMatch(n -> n.contains("/gallery/") || n.contains("/testsupport/"))
                .contains("dev/jasper/ui/swing/KitView.class");
        }
    }

    @Test void addMenuIncludesSeparatorAndAllFourActions() {
        var root = (Nodes.Stack)FormFixtures.create("vault-add-menu").root();
        var menu = (Nodes.MenuButton)root.children().getLast();
        assertThat(menu.items().stream().filter(i -> !i.separator()).map(i -> i.action())).containsExactly("login", "key", "note", "generate");
        assertThat(menu.items().stream().filter(i -> i.separator())).hasSize(1);
    }

    @Test void pickerHasAnExplicitNamedCueWithoutInferringDomainFromLabels() {
        var ready = dev.jasper.ui.model.ControlState.READY;
        assertThat(new Nodes.Choice("cue", ready, Nodes.ChoiceKind.PICKER, List.of(), "", "Choose", "open", "lock").icon()).isEqualTo("lock");
        assertThat(new Nodes.Choice("plain", ready, Nodes.ChoiceKind.PICKER, List.of(), "", "Credential", "open", "").icon()).isEmpty();
        assertThat(new Nodes.Choice("legacy", ready, Nodes.ChoiceKind.PICKER, List.of(), "", "Choose", "open").icon()).isEqualTo("key");
        assertThatThrownBy(() -> new Nodes.Choice("invalid", ready, Nodes.ChoiceKind.DROPDOWN, List.of(), "", "Choose", "open", "lock")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void actualFlatLafRendersRouteColorsAndCapturesTypedStatesWithoutValues() {
        dev.jasper.ui.testsupport.Edt.run(() -> {
            var chrome = dev.jasper.ui.testsupport.KitFixtures.light();
            var workspace = dev.jasper.ui.testsupport.KitFixtures.custom();
            var prior = javax.swing.UIManager.getLookAndFeel();
            try {
                dev.jasper.ui.swing.FlatLafInstaller.install(workspace, dev.jasper.ui.kit.Kit.DEFAULT);
                try (var binding = new dev.jasper.ui.theme.ThemeBinding(new dev.jasper.ui.theme.ThemePair(chrome, workspace), failure -> { throw new AssertionError(failure); });
                     var icons = KitGallery.icons()) {
                    for (var route : dev.jasper.ui.theme.ThemeRoute.values()) {
                        var context = dev.jasper.ui.swing.KitContext.of(dev.jasper.ui.kit.Kit.DEFAULT, binding, route, icons, () -> false, action -> "");
                        var spec = new dev.jasper.ui.model.ViewSpec(new Nodes.Button("route-button", dev.jasper.ui.model.ControlState.READY, "Continue", "", Nodes.ButtonKind.PRIMARY, "continue", ""));
                        try (var view = new dev.jasper.ui.swing.KitView(context, spec, event -> {})) {
                            int w = com.formdev.flatlaf.util.UIScale.scale(200), h = com.formdev.flatlaf.util.UIScale.scale(28);
                            var image = dev.jasper.ui.testsupport.KitFixtures.render(view, w, h, 1);
                            assertThat(image.getRGB(w / 2, com.formdev.flatlaf.util.UIScale.scale(3))).isEqualTo(context.theme().semantic().color(dev.jasper.ui.theme.SemanticColor.ACCENT).getRGB());
                            var fixture = new KitGallery.Fixture("route", "", "normal", 200, 28, spec);
                            assertThat(KitGallery.metadata(fixture, view, context, com.formdev.flatlaf.util.UIScale.getUserScaleFactor(), image)).contains("\"route\":\"" + route + "\"", "\"bounds\":[", "\"descriptorStates\":[", "\"kind\":\"Button\"").doesNotContain("Continue");
                        }
                    }
                }
            } finally {
                try { javax.swing.UIManager.setLookAndFeel(prior); } catch (javax.swing.UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
            }
        });
    }

    @Test void popupMetadataDescribesTheActualPaintedRootIncludingUnnamedSeparator() {
        dev.jasper.ui.testsupport.Edt.run(() -> {
            var base = dev.jasper.ui.testsupport.KitFixtures.context();
            var context = dev.jasper.ui.swing.KitContext.of(base.kit(), base.binding(), base.route(), KitGallery.icons(), () -> false, action -> "");
            try (var view = new dev.jasper.ui.swing.KitView(context, FormFixtures.create("vault-add-menu"), e -> {})) {
                var popup = KitGallery.find(view, "add-menu").getComponentPopupMenu();
                var image = new java.awt.image.BufferedImage(200, 140, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                popup.setSize(200, 140); popup.doLayout();
                var fixture = new KitGallery.Fixture("menu", "vault-add-menu.png", "normal", 200, 140, view.spec());
                assertThat(KitGallery.metadata(fixture, view, context, 1, image, popup)).contains("\"bounds\":[0,0,200,140]", "\"id\":null", "Menus$Popup");
            } finally { context.icons().close(); base.icons().close(); base.binding().close(); }
        });
    }

    static void collect(Object value, Set<String> values) {
        if (value == null) return;
        if (value instanceof Enum<?> e) { values.add(e.getDeclaringClass().getSimpleName() + "." + e.name()); return; }
        if (value instanceof Optional<?> o) { o.ifPresent(v -> collect(v, values)); return; }
        if (value instanceof Iterable<?> items) { items.forEach(v -> collect(v, values)); return; }
        if (value instanceof Nodes.Node n) values.add(n.getClass().getSimpleName());
        if (value instanceof Record record) try {
            for (RecordComponent c : record.getClass().getRecordComponents()) collect(c.getAccessor().invoke(record), values);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
