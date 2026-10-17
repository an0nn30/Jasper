package dev.jasper.app.settingsui;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.presentation.Composition;
import dev.jasper.app.presentation.SettingsChrome;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.testsupport.KitFixtures;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Spec 12.1 and 12.2: the rows the look implies are not built, and the interface font row speaks for the look. */
class LookRowsTest {
    static final SettingsChrome LINUX_DEFAULT = new SettingsChrome(true, false, true, Set.of(), "Use platform font size");

    static PageContext context(SettingsChrome chrome, java.util.function.Supplier<dev.jasper.ui.swing.KitContext> kit) {
        var parsed = ConfigLoader.parse(Path.of("/fixture/config.toml"), "", true);
        return new PageContext(new SettingsDraft(parsed.values()), id -> { }, run -> run.run(), (title, initial) -> Optional.empty(), () -> { },
            () -> null, kit, chrome, Composition.ROWS);
    }

    /** The settings a core page builds rows for, by name. */
    static List<String> rows(String pageId, PageContext context) {
        var spec = CorePages.specs(AppearanceFixtures.CHOICES, new CorePages.SystemActions(() -> Path.of("/fixture/config.toml"),
            () -> { }, () -> { }, () -> { }, () -> { }, () -> false, Optional.empty()), ShellChoices.NONE, id -> { }, true,
            NotificationGroupChoices.NONE).stream().filter(page -> page.id().equals(pageId)).findFirst().orElseThrow();
        var page = spec.build(context);
        page.component();
        try { return page.rows().stream().map(row -> row.setting().name()).toList(); } finally { page.dispose(); }
    }

    /** Every section title a core page shows. */
    static List<String> sections(String pageId, PageContext context) {
        var spec = CorePages.specs(AppearanceFixtures.CHOICES, new CorePages.SystemActions(() -> Path.of("/fixture/config.toml"),
            () -> { }, () -> { }, () -> { }, () -> { }, () -> false, Optional.empty()), ShellChoices.NONE, id -> { }, true,
            NotificationGroupChoices.NONE).stream().filter(page -> page.id().equals(pageId)).findFirst().orElseThrow();
        var page = spec.build(context);
        page.component();
        var titles = new ArrayList<String>();
        try { collect(page.viewSpec().root(), titles); return titles; } finally { page.dispose(); }
    }

    static void collect(Nodes.Node node, List<String> titles) {
        switch (node) {
            case Nodes.Section section -> { titles.add(section.title()); collect(section.body(), titles); }
            case Nodes.Stack stack -> stack.children().forEach(child -> collect(child, titles));
            case Nodes.Card card -> card.children().forEach(child -> collect(child, titles));
            default -> { }
        }
    }

    @Test void theLooksImpliedSettingsAreNotBuiltAndLeaveNoEmptySection() throws Exception {
        edt(() -> {
            var classic = context(SettingsChromeTest.CLASSIC, KitTestSupport::context);
            assertThat(rows("window", classic)).doesNotContain("window.native_decorations").contains("window.toolbar");
            assertThat(rows("appearance", classic)).doesNotContain("ui.animations.enabled").contains("window.view_mode");
            assertThat(sections("appearance", classic)).doesNotContain("Animations").contains("View mode");
            var linux = context(LINUX_DEFAULT, KitTestSupport::context);
            assertThat(rows("window", linux)).as("the default look off macOS").contains("window.native_decorations");
            assertThat(rows("appearance", linux)).contains("ui.animations.enabled");
            assertThat(sections("appearance", linux)).contains("Animations");
        });
    }

    @Test void theFontRowSpeaksForTheLookAndStartsAtItsStandardSize() throws Exception {
        edt(() -> {
            // Plan 4a's Enterprise fixture kit (12-point body) until plan 5b's shipped kit (Execution notes E2, E6).
            var enterprise = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            var standard = KitFixtures.context(KitFixtures.light());
            try {
                var classic = context(SettingsChromeTest.CLASSIC, () -> enterprise);
                assertThat(PlatformFontSize.standardSize(classic)).as("the kit's body size").isEqualTo(12f);
                assertThat(PlatformFontSize.standardSize(context(LINUX_DEFAULT, () -> standard))).as("the example").isEqualTo(14f);
                var editor = new PlatformFontSize(classic);
                var node = (Nodes.Stack) editor.node(CoreSettings.UI_FONT_SIZE.name(), dev.jasper.ui.model.ControlState.READY);
                var row = (Nodes.SettingRow) node.children().getFirst();
                assertThat(row.title()).isEqualTo("Use the default font size");
                var toggle = (Nodes.Toggle) ((Nodes.Stack) row.control()).children().getFirst();
                assertThat(toggle.label()).isEqualTo("Use the default size");
                editor.show(CoreSettings.UI_FONT_SIZE.defaultValue());
                assertThat(editor.event(CoreSettings.UI_FONT_SIZE.name(), new UiEvent.Toggled(CoreSettings.UI_FONT_SIZE.name() + ":default", false))).isTrue();
                assertThat(editor.value()).as("the first untick starts at the look's size").isEqualTo(12f);
                var platform = new PlatformFontSize(context(LINUX_DEFAULT, () -> standard));
                var plain = (Nodes.SettingRow) ((Nodes.Stack) platform.node(CoreSettings.UI_FONT_SIZE.name(), dev.jasper.ui.model.ControlState.READY)).children().getFirst();
                assertThat(plain.title()).isEqualTo("Use platform font size");
                platform.show(CoreSettings.UI_FONT_SIZE.defaultValue());
                platform.event(CoreSettings.UI_FONT_SIZE.name(), new UiEvent.Toggled(CoreSettings.UI_FONT_SIZE.name() + ":default", false));
                assertThat(platform.value()).isEqualTo(14f);
            } finally {
                for (var context : List.of(enterprise, standard)) { context.binding().close(); context.icons().close(); }
            }
        });
    }
}
