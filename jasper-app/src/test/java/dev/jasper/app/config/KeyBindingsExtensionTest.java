package dev.jasper.app.config;

import dev.jasper.app.commands.ActionId;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class KeyBindingsExtensionTest {
    private static KeyBindings.Extension extension(String id, String binding) {
        return new KeyBindings.Extension(id, Optional.ofNullable(binding));
    }

    private static KeyStroke stroke(String binding) { return KeyBindings.parse(binding, true).orElseThrow(); }

    @Test void stringAndEnumLookupsAgree() {
        KeyBindings bindings = KeyBindings.defaults(true);
        KeyStroke newTab = stroke("cmd+t");
        assertThat(bindings.idFor(newTab)).hasValue("new_tab");
        assertThat(bindings.actionFor(newTab)).hasValue(ActionId.NEW_TAB);
        assertThat(bindings.strokeFor("new_tab")).isEqualTo(bindings.strokeFor(ActionId.NEW_TAB)).hasValue(newTab);
        assertThat(bindings.strokes()).hasSize(ActionId.values().length).containsEntry("new_tab", newTab);
        assertThat(ActionId.forId("new_tab")).hasValue(ActionId.NEW_TAB);
        assertThat(ActionId.forId("dev.x.tool.run")).isEmpty();
        assertThat(KeyBindings.extensionId("dev.x.tool.run")).isTrue();
        for (String id : new String[]{"new_tab", "", null, "Dev.x", "dev..x", ".dev", "dev.x."})
            assertThat(KeyBindings.extensionId(id)).as(String.valueOf(id)).isFalse();
    }

    @Test void userBindingsForExtensionIdsAreCollisionCheckedButNotBoundUntilTheActionExists() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            KeyBindings.withOverrides(true, Map.of("dev.x.tool.run", "cmd+t"))).withMessageContaining("collision");
        assertThatIllegalArgumentException().isThrownBy(() ->
            KeyBindings.withOverrides(true, Map.of("unknown_builtin", "cmd+y"))).withMessageContaining("unknown action");
        var overrides = new LinkedHashMap<String, String>();
        overrides.put("new_tab", "none");
        overrides.put("dev.x.tool.run", "cmd+t");
        KeyBindings base = KeyBindings.withOverrides(true, overrides);
        assertThat(base.idFor(stroke("cmd+t"))).as("not bound before the action is registered").isEmpty();

        KeyBindings.Resolved resolved = base.withExtensions(List.of(extension("dev.x.tool.run", "cmd+alt+j")));
        assertThat(resolved.problems()).isEmpty();
        assertThat(resolved.bindings().idFor(stroke("cmd+t"))).hasValue("dev.x.tool.run");
        assertThat(resolved.bindings().idFor(stroke("cmd+alt+j"))).as("the user's choice replaces the default").isEmpty();
        assertThat(resolved.bindings().actionFor(stroke("cmd+t"))).as("not a built-in").isEmpty();
    }

    @Test void builtInsBeatPluginDefaultsAndEarlierPluginsBeatLaterOnes() {
        KeyBindings.Resolved resolved = KeyBindings.defaults(true).withExtensions(List.of(
            extension("dev.a.one", "cmd+k"),
            extension("dev.a.two", "cmd+alt+j"),
            extension("dev.b.three", "cmd+alt+j"),
            extension("dev.b.four", "not a shortcut"),
            extension("dev.b.five", null)));
        KeyBindings bindings = resolved.bindings();
        assertThat(bindings.idFor(stroke("cmd+k"))).hasValue("command_palette");
        assertThat(bindings.idFor(stroke("cmd+alt+j"))).hasValue("dev.a.two");
        assertThat(bindings.strokeFor("dev.b.three")).isEmpty();
        assertThat(resolved.problems()).extracting(KeyBindings.Problem::kind)
            .containsOnly(KeyBindings.Problem.Kind.DEFAULT_DROPPED);
        assertThat(resolved.problems()).extracting(KeyBindings.Problem::actionId)
            .containsExactly("dev.a.one", "dev.b.three", "dev.b.four");
        assertThat(resolved.problems().get(0).message()).contains("cmd+k", "command_palette");
        assertThat(resolved.problems().get(1).message()).contains("dev.a.two");
    }

    @Test void noneUnbindsAndUnregisteredUserIdsAreReported() {
        KeyBindings base = KeyBindings.withOverrides(true, Map.of("dev.a.one", "none", "dev.gone.action", "cmd+alt+g"));
        KeyBindings.Resolved resolved = base.withExtensions(List.of(extension("dev.a.one", "cmd+alt+j")));
        assertThat(resolved.bindings().strokeFor("dev.a.one")).isEmpty();
        assertThat(resolved.bindings().idFor(stroke("cmd+alt+g"))).isEmpty();
        assertThat(resolved.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.kind()).isEqualTo(KeyBindings.Problem.Kind.UNKNOWN_ACTION);
            assertThat(problem.actionId()).isEqualTo("dev.gone.action");
        });
        assertThat(base.withExtensions(List.of()).bindings().strokes()).isEqualTo(KeyBindings.defaults(true).strokes());
    }

    @Test void theLoaderKeepsExtensionBindingsAndStillRejectsUnknownBuiltIns() {
        var ok = ConfigLoader.parse(Path.of("config.toml"), """
            [keybindings]
            "dev.jasper.sample.demo" = "cmd+alt+j"
            new_tab = "cmd+y"
            """, true);
        assertThat(ok.diagnostics()).isEmpty();
        assertThat(ok.snapshot().keybindings()).containsEntry("dev.jasper.sample.demo", "cmd+alt+j").containsEntry("new_tab", "cmd+y");

        var unknown = ConfigLoader.parse(Path.of("config.toml"), "[keybindings]\nnot_an_action = \"cmd+y\"\n", true);
        assertThat(unknown.diagnostics()).singleElement().satisfies(d -> assertThat(d.message()).contains("Unknown action"));

        var invalid = ConfigLoader.parse(Path.of("config.toml"), "[keybindings]\n\"dev.x.tool.run\" = \"not a shortcut\"\nnew_tab = \"cmd+y\"\n", true);
        assertThat(invalid.rejected()).as("a bad shortcut for a possibly absent plugin's action costs only that entry").isFalse();
        assertThat(invalid.diagnostics()).singleElement().satisfies(d -> assertThat(d.severity()).isEqualTo(ConfigDiagnostic.Severity.WARNING));
        assertThat(invalid.snapshot().keybindings()).containsOnlyKeys("new_tab");

        var colliding = ConfigLoader.parse(Path.of("config.toml"), "[keybindings]\n\"dev.x.tool.run\" = \"cmd+t\"\n", true);
        assertThat(colliding.diagnostics()).singleElement().satisfies(d -> assertThat(d.message()).contains("conflicts"));
        assertThat(colliding.snapshot().keybindings()).isEmpty();
    }
}
