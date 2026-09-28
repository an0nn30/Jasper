package dev.jasper.sample;

import dev.jasper.sdk.settings.Applies;
import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;

/** The sample's declared settings. The sample reads them only when it starts, so each applies after a restart. */
final class SampleSettings {
    final Setting<Boolean> activity;
    final Setting<Boolean> ui;
    final Setting<Boolean> terminal;
    final Setting<Boolean> session;
    final Setting<Boolean> scope;
    final Setting<Integer> stepMillis;

    SampleSettings(PluginSettings settings) {
        activity = demo(settings, "demo_activity", "Run the Buddy activity at start", "A short demonstration activity on Buddy.");
        ui = demo(settings, "demo_ui", "Show the sample action", "On the toolbar, in the menus and in the status bar.");
        terminal = demo(settings, "demo_terminal", "Terminal demo", "\"Insert Sample Greeting\" and the last command's exit status.");
        session = demo(settings, "demo_session", "Echo session", "\"Open Sample Echo Session\", a pane whose session the plugin provides.");
        scope = demo(settings, "demo_scope", "Greetings in the command palette", "A \"Greetings\" scope in the command palette.");
        stepMillis = settings.declare(SettingSpec.of("demo_step_millis", SettingKind.integer(0, 5000), 300)
            .label("Step delay").description("Milliseconds between the demo's steps.").section("Timing").applies(Applies.RESTART));
    }

    private static Setting<Boolean> demo(PluginSettings settings, String key, String label, String description) {
        return settings.declare(SettingSpec.of(key, SettingKind.bool(), false).label(label).description(description)
            .section("Demos").applies(Applies.RESTART));
    }
}
