package dev.jasper.app.settingsui;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.presentation.Composition;
import dev.jasper.app.presentation.SettingsChrome;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Spec 12.1: without an inline Reset, each row's own menu offers Reset to Default while the value differs from its default. */
class ResetMenuTest {
    static PageContext context(SettingsChrome chrome, String config) {
        var parsed = ConfigLoader.parse(Path.of("/fixture/config.toml"), config, true);
        return new PageContext(new SettingsDraft(parsed.values()), id -> { }, run -> run.run(), (title, initial) -> Optional.empty(), () -> { },
            () -> null, KitTestSupport::context, chrome, Composition.ROWS);
    }

    static SchemaPage page(String id, PageContext context, NotificationGroupChoices groups) {
        var spec = CorePages.specs(AppearanceFixtures.CHOICES, new CorePages.SystemActions(() -> Path.of("/fixture/config.toml"),
            () -> { }, () -> { }, () -> { }, () -> { }, () -> false, Optional.empty()), ShellChoices.NONE, page -> { }, true, groups)
            .stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElseThrow();
        var page = spec.build(context);
        page.component();
        return page;
    }

    static Nodes.Node find(Nodes.Node node, String id) {
        if (node.id().equals(id)) return node;
        var children = switch (node) {
            case Nodes.Stack stack -> stack.children();
            case Nodes.Card card -> card.children();
            case Nodes.Section section -> List.of(section.body());
            case Nodes.SettingRow row -> List.of(row.control());
            default -> List.<Nodes.Node>of();
        };
        for (var child : children) { var found = find(child, id); if (found != null) return found; }
        return null;
    }

    static Nodes.SettingRow row(SchemaPage page, String id) { return (Nodes.SettingRow) find(page.viewSpec().root(), id); }

    @Test void aClassicRowHasNoResetButtonAndItsMenuResetsIt() throws Exception {
        edt(() -> {
            var context = context(SettingsChromeTest.CLASSIC, "[palette]\nvisible_rows = 9\n");
            var page = page("command-palette", context, NotificationGroupChoices.NONE);
            try {
                var row = row(page, "row:palette.visible_rows");
                assertThat(((Nodes.Stack) row.control()).children()).as("no Reset button").hasSize(1);
                assertThat(row.contextActions()).containsExactly(new MenuItem("palette.visible_rows:reset", "Reset to Default", "",
                    "palette.visible_rows:reset", true, false));
                page.event(new UiEvent.Action("palette.visible_rows:reset"));
                assertThat(context.draft().value(CoreSettings.PALETTE_VISIBLE_ROWS)).isEqualTo(context.draft().defaultValue(CoreSettings.PALETTE_VISIBLE_ROWS));
                assertThat(row(page, "row:palette.visible_rows").contextActions().getFirst().enabled()).as("at its default").isFalse();
            } finally { page.dispose(); }
        });
    }

    @Test void theDefaultLookKeepsItsResetButtonAndNoMenu() throws Exception {
        edt(() -> {
            var page = page("command-palette", context(LookRowsTest.LINUX_DEFAULT, "[palette]\nvisible_rows = 9\n"), NotificationGroupChoices.NONE);
            try {
                var row = row(page, "row:palette.visible_rows");
                assertThat(((Nodes.Stack) row.control()).children()).hasSize(2);
                assertThat(row.contextActions()).isEmpty();
            } finally { page.dispose(); }
        });
    }

    @Test void theFullSpanEditorsOfferTheMenuOnTheirOwnRows() throws Exception {
        edt(() -> {
            var context = context(SettingsChromeTest.CLASSIC, "[ui]\nappearance = \"light\"\n[ui.theme]\ndark = \"catppuccin-mocha\"\n"
                + "light = \"catppuccin-latte\"\n[ui.font]\nsize = 15\n");
            var page = page("appearance", context, NotificationGroupChoices.NONE);
            try {
                for (var id : List.of("row:ui.appearance", "row:ui.appearance:light")) {
                    var row = row(page, id);
                    assertThat(row.contextActions()).as(id).extracting(MenuItem::action).containsExactly("ui.appearance:reset");
                    assertThat(row.contextActions().getFirst().enabled()).as(id).isTrue();
                }
                assertThat(find(page.viewSpec().root(), "ui.appearance:reset")).as("no Reset button").isNull();
                assertThat(find(page.viewSpec().root(), "ui.appearance:light-reset")).as("no reserved lane").isNull();
                assertThat(row(page, "row:ui.font.size").contextActions()).extracting(MenuItem::action).containsExactly("ui.font.size:reset");
                assertThat(row(page, "row:ui.font.size").contextActions().getFirst().enabled()).as("the font row while modified").isTrue();
                assertThat(find(page.viewSpec().root(), "ui.font.size:reset")).isNull();
                page.event(new UiEvent.Action("ui.appearance:reset"));
                // The appearance's reset takes its companions, the two themes, with it.
                for (var setting : List.<dev.jasper.app.settings.Setting<?>>of(CoreSettings.UI_APPEARANCE, CoreSettings.UI_THEME_DARK, CoreSettings.UI_THEME_LIGHT))
                    assertThat((Object) context.draft().value(setting)).as(setting.name()).isEqualTo(context.draft().defaultValue(setting));
                page.event(new UiEvent.Action("ui.font.size:reset"));
                assertThat(context.draft().value(CoreSettings.UI_FONT_SIZE)).isEqualTo(context.draft().defaultValue(CoreSettings.UI_FONT_SIZE));
                assertThat(row(page, "row:ui.font.size").contextActions().getFirst().enabled()).as("the font row at its default").isFalse();
            } finally { page.dispose(); }
        });
    }

    @Test void anExternalRowsMenuResetsItsPendingValue() throws Exception {
        edt(() -> {
            var context = context(SettingsChromeTest.CLASSIC, "");
            var page = SchemaPage.page("system", "System Settings").section("Java Virtual Machine")
                .external(new SchemaPage.External("vm.heap", "Maximum heap size", "MiB", List.of(), new Nodes.Numeric(512, 1_048_576, 256, 0),
                    () -> 4096, () -> 2048, value -> "", value -> "", value -> java.util.concurrent.CompletableFuture.completedFuture(null), () -> true))
                .build(context);
            page.component();
            try {
                var row = row(page, "row:vm.heap");
                assertThat(find(page.viewSpec().root(), "vm.heap:reset")).as("no Reset button").isNull();
                assertThat(row.contextActions()).containsExactly(new MenuItem("vm.heap:reset", "Reset to Default", "", "vm.heap:reset", true, false));
                page.event(new UiEvent.Action("vm.heap:reset"));
                assertThat(page.externals().getFirst().pending()).as("the default, pending until Apply").isEqualTo(2048);
                assertThat(row(page, "row:vm.heap").contextActions().getFirst().enabled()).as("at its default").isFalse();
            } finally { page.dispose(); }
        });
    }

    @Test void aNotificationGroupsRowsOfferTheMenu() throws Exception {
        edt(() -> {
            var groups = new NotificationGroupChoices(() -> List.of(new NotificationGroupChoices.Group("configuration", "Configuration", "Jasper", "sticky")));
            var page = page("notifications", context(SettingsChromeTest.CLASSIC, "[notifications.groups]\nconfiguration = \"card\"\n"), groups);
            try {
                var row = row(page, "notifications.groups:row-0");
                assertThat(row.contextActions()).extracting(MenuItem::action).containsExactly(NotificationGroupsEditor.resetId("notifications.groups", 0));
                assertThat(row.contextActions().getFirst().enabled()).isTrue();
                assertThat(find(page.viewSpec().root(), NotificationGroupsEditor.resetId("notifications.groups", 0))).isNull();
            } finally { page.dispose(); }
        });
    }
}
