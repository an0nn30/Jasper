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

    /** Plan 3b: SDK 0.18's new fields have real fixtures too, not only its new nodes and constants. */
    @Test void zeroEighteenFieldsHaveARealFixture() {
        var records = new ArrayList<Record>();
        KitGallery.fixtures().forEach(f -> records(f.spec().root(), records));
        assertThat(records).as("a read-only field").anyMatch(r -> r instanceof Nodes.Field field && field.readOnly());
        assertThat(records).as("a technical caption").anyMatch(r -> r instanceof Nodes.PickerFace face && face.technicalCaption());
        assertThat(records).as("a footnote").anyMatch(r -> r instanceof Nodes.Form form && !form.footnote().isBlank());
        assertThat(records).as("a technical property").anyMatch(r -> r instanceof Nodes.PropertyRow row && row.technical());
        assertThat(records).as("a property with a severity").anyMatch(r -> r instanceof Nodes.PropertyRow row && row.severity().isPresent());
        assertThat(records).as("a notice with an action").anyMatch(r -> r instanceof Nodes.Notice notice && !notice.actions().isEmpty());
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

    /** Every record reachable from {@code value}: nodes and their parts (faces, rows, items). */
    static void records(Object value, List<Record> out) {
        if (value instanceof Optional<?> o) { o.ifPresent(v -> records(v, out)); return; }
        if (value instanceof Iterable<?> items) { items.forEach(v -> records(v, out)); return; }
        if (value instanceof Map<?, ?> map) { map.values().forEach(v -> records(v, out)); return; }
        if (value instanceof Record record && !(value instanceof dev.jasper.ui.model.ControlState)) {
            out.add(record);
            try { for (RecordComponent c : record.getClass().getRecordComponents()) records(c.getAccessor().invoke(record), out); }
            catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
    }

    static void collect(Object value, Set<String> values) {
        if (value == null) return;
        if (value instanceof Enum<?> e) { values.add(e.getDeclaringClass().getSimpleName() + "." + e.name()); return; }
        if (value instanceof Optional<?> o) { o.ifPresent(v -> collect(v, values)); return; }
        if (value instanceof Iterable<?> items) { items.forEach(v -> collect(v, values)); return; }
        if (value instanceof Map<?, ?> map) { map.values().forEach(v -> collect(v, values)); return; }
        if (value instanceof Nodes.Node n) values.add(n.getClass().getSimpleName());
        if (value instanceof Record record) try {
            for (RecordComponent c : record.getClass().getRecordComponents()) collect(c.getAccessor().invoke(record), values);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    /** Spec 16.1.3 and plan 3c: SDK 0.18's table vocabulary has a real fixture, the in-cell DROPDOWN among it. */
    @Test void everyTableAdditionOfSdkZeroEighteenHasARealFixture() {
        var records = new ArrayList<Record>();
        KitGallery.fixtures().forEach(f -> records(f.spec().root(), records));
        assertThat(records).as("a tree with columns").anyMatch(r -> r instanceof Nodes.Tree tree && tree.columns().size() > 1);
        assertThat(records).as("a heading row with a counted noun").anyMatch(r -> r instanceof Nodes.Table table
            && table.rows().stream().anyMatch(row -> row.heading().filter(h -> h.count().isPresent() && !h.countNoun().isBlank()).isPresent()));
        assertThat(records).as("an in-cell DROPDOWN").anyMatch(r -> r instanceof Nodes.Table table && table.rows().stream()
            .anyMatch(row -> row.cellNodes().values().stream().anyMatch(c -> c instanceof Nodes.Choice choice && choice.kind() == Nodes.ChoiceKind.DROPDOWN)));
        assertThat(records).as("an inline cell stack").anyMatch(r -> r instanceof Nodes.Table table && table.rows().stream()
            .anyMatch(row -> row.cellNodes().values().stream().anyMatch(c -> c instanceof Nodes.Stack stack && stack.role() == Nodes.LayoutRole.INLINE)));
        assertThat(records).as("an empty table's state").anyMatch(r -> r instanceof Nodes.Table table && table.rows().isEmpty() && table.empty().isPresent());
        assertThat(records).as("an empty table's line").anyMatch(r -> r instanceof Nodes.Table table && table.rows().isEmpty() && !table.emptyText().isBlank());
        assertThat(records).as("an empty list's line").anyMatch(r -> r instanceof Nodes.ItemList list && list.items().isEmpty() && !list.emptyText().isBlank());
    }

    /** Plan 4c: the Enterprise fixtures hold the classic surfaces, and none shares a name with a gallery fixture. */
    @Test void theEnterpriseFixturesHoldTheClassicSurfaces() {
        var nodes = new ArrayList<Record>();
        EnterpriseFixtures.create().forEach(f -> records(f.spec().root(), nodes));
        assertThat(nodes).as("a route").anyMatch(n -> n instanceof Nodes.Stack s && s.role() == Nodes.LayoutRole.ROUTE);
        assertThat(nodes).as("an ordered list followed by its entry row").anyMatch(n -> n instanceof Nodes.Stack s && s.role() == Nodes.LayoutRole.VERTICAL
            && s.children().size() > 1 && s.children().get(0) instanceof Nodes.ItemList l && l.kind() == Nodes.ListKind.ORDERED
            && s.children().get(1) instanceof Nodes.Stack e && e.role() == Nodes.LayoutRole.INLINE);
        assertThat(nodes).as("an ordered list with an add action").anyMatch(n -> n instanceof Nodes.ItemList l && l.kind() == Nodes.ListKind.ORDERED && l.addAction().isPresent());
        assertThat(nodes).as("a grouped table with cell dropdowns").anyMatch(n -> n instanceof Nodes.Table t
            && t.rows().stream().anyMatch(r -> r.heading().isPresent()) && t.rows().stream().anyMatch(r -> !r.cellNodes().isEmpty()));
        assertThat(nodes).as("a column tree").anyMatch(n -> n instanceof Nodes.Tree t && !t.columns().isEmpty());
        assertThat(nodes).as("a technical picker caption").anyMatch(n -> n instanceof Nodes.Choice c && c.face().filter(Nodes.PickerFace::technicalCaption).isPresent());
        assertThat(nodes).as("hued options").anyMatch(n -> n instanceof Nodes.Choice c && c.kind() == Nodes.ChoiceKind.EXCLUSIVE && c.options().stream().allMatch(o -> o.hue().isPresent()));
        assertThat(nodes).as("a PATH field").anyMatch(n -> n instanceof Nodes.Field f && f.kind() == Nodes.FieldKind.PATH);
        var names = EnterpriseFixtures.create().stream().map(KitGallery.Fixture::name).toList();
        assertThat(names).doesNotHaveDuplicates().noneMatch(name -> KitGallery.fixtures().stream().anyMatch(f -> f.name().equals(name)));
    }
}
