package dev.jasper.history;

import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Shell History's declared settings. They apply at once and are safe to read from any thread. */
final class HistorySettings {
    private final Setting<List<String>> trivialCommands;
    private final Setting<Boolean> deprioritizeTrivial;

    HistorySettings(PluginSettings settings) {
        trivialCommands = settings.declare(SettingSpec.of("trivial_commands",
                SettingKind.stringList(word -> word.matches("\\S+")), HistoryPlugin.DEFAULT_TRIVIAL)
            .label("Trivial commands")
            .description("Single words compared with a command's first word; a command of at most two words that starts with one ranks below other commands on an empty History query.")
            .keywords("history", "rank"));
        deprioritizeTrivial = settings.declare(SettingSpec.of("deprioritize_trivial", SettingKind.bool(), true)
            .label("Rank trivial commands last")
            .description("Off: an empty History query lists commands by recency alone.")
            .keywords("history", "rank"));
    }

    /** The lower-cased trivial commands; none when ranking them last is off or the list is empty. */
    Set<String> trivial() {
        if (!deprioritizeTrivial.get()) return Set.of();
        var set = new HashSet<String>();
        for (String command : trivialCommands.get()) set.add(command.toLowerCase(Locale.ROOT));
        return Set.copyOf(set);
    }
}
