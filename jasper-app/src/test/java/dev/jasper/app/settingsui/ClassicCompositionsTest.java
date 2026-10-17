package dev.jasper.app.settingsui;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeFolder;
import dev.jasper.app.appearance.ThemeLibrary;
import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.presentation.Composition;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/** Spec 12.2: the Appearance theme menus under each composition. */
class ClassicCompositionsTest {
    static PageContext context(Composition composition) {
        var parsed = ConfigLoader.parse(Path.of("/fixture/config.toml"), "", true);
        return new PageContext(new SettingsDraft(parsed.values()), id -> { }, run -> run.run(), (title, initial) -> Optional.empty(), () -> { },
            () -> null, KitTestSupport::context, composition == Composition.CLASSIC ? SettingsChromeTest.CLASSIC : LookRowsTest.LINUX_DEFAULT, composition);
    }

    static AppearanceChoices withImports() {
        var imports = new ThemeImports(new ThemeFolder(Path.of("themes"), ThemeLibrary::bundledOnly), surface -> Optional.empty(),
            (surface, question) -> { }, (surface, notice) -> { }, edit -> CompletableFuture.completedFuture(null),
            () -> CompletableFuture.completedFuture(null), Runnable::run, Runnable::run);
        var base = AppearanceFixtures.CHOICES;
        return new AppearanceChoices(base.opacity(), base.blur(), base.systemDark(), ignored -> new Subscription(() -> { }), base.themes(), Optional.of(imports));
    }

    static Nodes.Node find(Nodes.Node node, String id) { return ResetMenuTest.find(node, id); }

    @Test void theThemeMenusAreEllipsesWithTheGearsItems() throws Exception {
        edt(() -> {
            for (var composition : Composition.values()) {
                var editor = new AppearanceSelection(withImports(), context(composition));
                var root = editor.node("ui.appearance", dev.jasper.ui.model.ControlState.READY);
                for (var id : List.of("ui.appearance:dark-gear", "ui.appearance:light-gear")) {
                    var menu = (Nodes.MenuButton) find(root, id);
                    assertThat(menu.icon()).as(composition + " " + id).isEqualTo(composition == Composition.CLASSIC ? "more" : "settings");
                    assertThat(menu.items()).extracting(MenuItem::label).containsExactly("Import Theme…", "", "Remove Theme");
                    assertThat(menu.items().get(0).enabled()).as("Import Theme… is enabled").isTrue();
                    assertThat(menu.items().get(2).enabled()).as("only built-in themes: nothing to remove").isFalse();
                }
                editor.dispose();
                var scheme = new SchemePick("Terminal scheme", AppearanceFixtures.CHOICES.themes(), withImports().imports(), context(composition),
                    Editors.menuIcon(context(composition)));
                var gear = (Nodes.MenuButton) find(scheme.node("terminal.scheme", dev.jasper.ui.model.ControlState.READY), "terminal.scheme:gear");
                assertThat(gear.icon()).isEqualTo(composition == Composition.CLASSIC ? "more" : "settings");
                scheme.dispose();
            }
        });
    }
}
