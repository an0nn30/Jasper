package dev.jasper.app.config;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.KeyContext;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Optional;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class KeyBindingsContextTest {
    private static KeyStroke mac(String binding) { return KeyBindings.parse(binding, true).orElseThrow(); }

    private static KeyBindings.Extension extension(String id, String binding, KeyContext context) {
        return new KeyBindings.Extension(id, Optional.of(binding), Optional.empty(), context);
    }

    @Test void builtInsCarryTheirContexts() {
        assertThat(ActionId.CLEAR_SCROLLBACK.context()).isEqualTo(KeyContext.TERMINAL);
        assertThat(ActionId.CLEAR_TERMINAL.context()).isEqualTo(KeyContext.TERMINAL);
        assertThat(ActionId.PREVIOUS_PROMPT.context()).isEqualTo(KeyContext.TERMINAL);
        assertThat(ActionId.NEW_TAB.context()).isEqualTo(KeyContext.GLOBAL);
        KeyBindings bindings = KeyBindings.defaults(true);
        assertThat(bindings.contextFor("next_prompt")).isEqualTo(KeyContext.TERMINAL);
        assertThat(bindings.contextFor("dev.unknown.action")).isEqualTo(KeyContext.GLOBAL);
    }

    @Test void ownedContextsMayReuseAGlobalStrokeAndTheMostSpecificApplicableOneWins() {
        KeyBindings.Resolved resolved = KeyBindings.defaults(true).withExtensions(List.of(
            extension("dev.x.hosts.new", "cmd+n", KeyContext.panel("dev.x.hosts")),
            extension("dev.x.save", "cmd+n", KeyContext.content("dev.x"))));
        assertThat(resolved.problems()).isEmpty();
        KeyBindings bindings = resolved.bindings();
        assertThat(bindings.idsFor(mac("cmd+n"))).containsExactly("new_terminal_window", "dev.x.hosts.new", "dev.x.save");
        assertThat(bindings.idFor(mac("cmd+n"))).as("the Global or Terminal holder").hasValue("new_terminal_window");
        assertThat(bindings.resolve(mac("cmd+n"), context -> context.level() == 0)).hasValue("new_terminal_window");
        assertThat(bindings.resolve(mac("cmd+n"), context -> context.level() == 0
            || context.equals(KeyContext.panel("dev.x.hosts")))).hasValue("dev.x.hosts.new");
        assertThat(bindings.resolve(mac("cmd+n"), context -> context.kind() != KeyContext.Kind.PANEL)).hasValue("dev.x.save");
        assertThat(bindings.resolve(mac("cmd+n"), context -> false)).isEmpty();
        assertThat(bindings.resolve(mac("cmd+alt+shift+f9"), context -> true)).isEmpty();
        assertThat(bindings.contextFor("dev.x.save")).isEqualTo(KeyContext.content("dev.x"));
    }

    @Test void conflictingContextsDropALaterPluginDefault() {
        KeyBindings.Resolved resolved = KeyBindings.defaults(true).withExtensions(List.of(
            extension("dev.x.term", "cmd+up", KeyContext.TERMINAL),
            extension("dev.x.global", "cmd+down", KeyContext.GLOBAL),
            extension("dev.a.edit", "cmd+alt+j", KeyContext.content("dev.a")),
            extension("dev.b.edit", "cmd+alt+j", KeyContext.content("dev.b")),
            extension("dev.b.again", "cmd+alt+j", KeyContext.content("dev.b"))));
        assertThat(resolved.problems()).extracting(KeyBindings.Problem::actionId)
            .containsExactly("dev.x.term", "dev.x.global", "dev.b.again");
        assertThat(resolved.problems().get(0).message()).contains("previous_prompt");
        assertThat(resolved.problems().get(1).message()).as("Global and Terminal overlap").contains("next_prompt");
        assertThat(resolved.problems().get(2).message()).contains("dev.b.edit");
        assertThat(resolved.bindings().idsFor(mac("cmd+alt+j"))).containsExactly("dev.a.edit", "dev.b.edit");
    }

    @Test void pluginDefaultsGetTheAltRuleOrTheirExplicitOtherDefault() {
        var shifted = new KeyBindings.Extension("dev.x.a", Optional.of("cmd+shift+o"));
        var paired = new KeyBindings.Extension("dev.x.b", Optional.of("cmd+alt+1"), Optional.of("alt+1"), KeyContext.GLOBAL);
        var macOnly = new KeyBindings.Extension("dev.x.c", Optional.of("cmd+m"), Optional.of("none"), KeyContext.GLOBAL);
        var plain = new KeyBindings.Extension("dev.x.d", Optional.of("cmd+j"));
        assertThat(shifted.defaultFor(true)).hasValue("cmd+shift+o");
        assertThat(shifted.defaultFor(false)).hasValue("alt+cmd+shift+o");
        assertThat(paired.defaultFor(false)).hasValue("alt+1");
        assertThat(plain.defaultFor(false)).as("no shift, no alt").hasValue("cmd+j");
        assertThat(new KeyBindings.Extension("dev.x.e", Optional.empty()).defaultFor(false)).isEmpty();

        KeyBindings.Resolved linux = KeyBindings.defaults(false).withExtensions(List.of(shifted, paired, macOnly, plain));
        assertThat(linux.problems()).isEmpty();
        assertThat(linux.bindings().strokeFor("dev.x.a")).contains(KeyStroke.getKeyStroke(KeyEvent.VK_O,
            InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        assertThat(linux.bindings().strokeFor("dev.x.b")).contains(KeyStroke.getKeyStroke(KeyEvent.VK_1, InputEvent.ALT_DOWN_MASK));
        assertThat(linux.bindings().strokeFor("dev.x.c")).as("explicitly none off macOS").isEmpty();
        assertThat(linux.bindings().strokeFor("dev.x.d")).contains(KeyStroke.getKeyStroke(KeyEvent.VK_J,
            InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        KeyBindings.Resolved macOs = KeyBindings.defaults(true).withExtensions(List.of(shifted, paired, macOnly, plain));
        assertThat(macOs.bindings().strokeFor("dev.x.c")).contains(mac("cmd+m"));
        assertThat(macOs.bindings().strokeFor("dev.x.b")).contains(mac("cmd+alt+1"));
    }

    @Test void everyBuiltInHasAnExplicitDefaultForEachPlatform() {
        for (ActionId action : ActionId.values()) for (boolean mac : new boolean[]{true, false}) {
            String binding = action.defaultBinding(mac);
            assertThat(binding).as("%s on %s", action, mac).isNotBlank();
            if (!mac) assertThat(binding).as("%s names its modifiers literally off macOS", action).doesNotContain("cmd");
        }
    }
}
