package dev.jasper.app.pluginmanager;

import dev.jasper.app.plugins.PluginRuntime;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Builds {@link PluginRuntime.Row}s for view tests: by default an active, enabled, user-installed plugin. */
final class TestRows {
    private String id;
    private String name;
    private String origin = "Installed";
    private String state = "ACTIVE";
    private String reason = "";
    private String pending = "";
    private String homepage = "";
    private String vendor = "Example";
    private String description = "Does things";
    private boolean enabled = true, needsConsent, canToggle = true, canRemove = true, pendingRemoval, pendingInstall;
    private int errors;
    private List<String> capabilities = List.of(), unconsented = List.of(), requires = List.of(), exports = List.of();
    private Optional<PluginRuntime.PluginIcon> icon = Optional.empty();

    static TestRows plugin(String id, String name) {
        var rows = new TestRows();
        rows.id = id;
        rows.name = name;
        return rows;
    }

    /** "Bundled" and "Development" plugins cannot be removed. */
    TestRows origin(String value) { origin = value; canRemove = value.equals("Installed"); return this; }
    TestRows state(String value, String why) { state = value; reason = why; return this; }
    TestRows disabled() { enabled = false; return this; }
    TestRows needsReview(String... asked) {
        needsConsent = true; canToggle = false; state = "NEEDS_CONSENT";
        capabilities = List.of(asked); unconsented = List.of(asked);
        return this;
    }
    TestRows removing() { pendingRemoval = true; canToggle = false; enabled = false; pending = "Will be removed at restart"; return this; }
    TestRows installing() { pendingInstall = true; state = "NOT_LOADED"; pending = "Version 1.0.0 will be installed at restart"; return this; }
    TestRows pending(String text) { pending = text; return this; }
    TestRows vendor(String value) { vendor = value; return this; }
    TestRows description(String value) { description = value; return this; }
    TestRows homepage(String value) { homepage = value; return this; }
    TestRows errors(int value) { errors = value; return this; }
    TestRows capabilities(List<String> value) { capabilities = value; return this; }
    TestRows requires(List<String> value) { requires = value; return this; }
    TestRows exports(List<String> value) { exports = value; return this; }
    TestRows icon(PluginRuntime.PluginIcon value) { icon = Optional.of(value); return this; }

    PluginRuntime.Row row() {
        return new PluginRuntime.Row(id, name, "1.0.0", description, vendor, origin, state, reason, capabilities, unconsented,
            requires, errors, enabled, needsConsent, canToggle, canRemove, pendingRemoval, pendingInstall, pending,
            Path.of("/plugins/" + id), Path.of("/plugins/" + id + "/" + id + ".toml"), Path.of("/plugins/" + id + "/data"),
            homepage, exports, icon);
    }
}
