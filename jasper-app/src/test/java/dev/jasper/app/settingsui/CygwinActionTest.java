package dev.jasper.app.settingsui;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CygwinActionTest {
    @Test void windowsOnlyActionHasNoDefaultShortcut() {
        var action = ActionId.MANAGE_CYGWIN_PACKAGES;
        assertThat(action.id()).isEqualTo("manage_cygwin_packages");
        assertThat(action.label()).isEqualTo("Manage Cygwin packages…");
        assertThat(action.defaultBinding(false)).isEqualTo("none");
        assertThat(action.availableOn("Windows 11")).isTrue();
        assertThat(action.availableOn("Mac OS X")).isFalse();
        assertThat(action.availableOn("Linux")).isFalse();
    }
    @Test void settingsAffordanceUsesSuppliedManagementCallback() {
        var calls = new AtomicInteger();
        var choices = new ShellChoices(List::of, () -> {}, listener -> new Subscription(() -> {}), Optional.of(calls::incrementAndGet));
        var draft = new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values());
        var editor = new Editors.Profile("Shell", choices, draft);
        assertThat(((Nodes.Stack)editor.node("profile", ControlState.READY)).children()).hasSize(3);
        editor.event("profile", new UiEvent.Action("profile:cygwin"));
        assertThat(calls).hasValue(1);
        assertThat(ShellChoices.NONE.manageCygwin()).isEmpty();
    }
}
