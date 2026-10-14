package dev.jasper.editor.ui;

import dev.jasper.editor.external.KnownEditors;
import dev.jasper.sdk.settings.EditorHint;
import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import java.awt.Font;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The Editor's settings as its window uses them (spec 7).
 *
 * @param fontFamily the editor font's family
 * @param fontSize points, 8 to 48
 * @param tabSize columns per tab stop, 1 to 16
 * @param softWrap whether new tabs wrap long lines
 * @param maxFileMib the largest file opened, 1 to 256 MiB
 * @param vim whether Vim mode is on (Vim spec 9)
 */
public record EditorSettings(String fontFamily, int fontSize, int tabSize, boolean softWrap, int maxFileMib, boolean vim) {
    /** Tried in order for the default font. */
    public static final List<String> PREFERRED_FONTS = List.of("JetBrains Mono", "Menlo", "Consolas", "DejaVu Sans Mono");

    /** @return {@link #maxFileMib()} in bytes */
    public long maxFileBytes() { return maxFileMib * 1024L * 1024L; }

    /** @return the plain editor font */
    public Font font() { return new Font(fontFamily, Font.PLAIN, fontSize); }

    /** The Editor's declared settings; Settings checks their values and reports bad ones. */
    public static final class Declared {
        private final Setting<String> fontFamily;
        private final Setting<Integer> fontSize, tabSize, maxFileMib, recentFilesDays;
        private final Setting<Boolean> softWrap, vim;
        final Setting<String> defaultEditor, customCommand;
        final Setting<Boolean> customInTerminal, keepEnvironmentEditor;

        /** {@code default_editor}'s default: the Jasper Editor. */
        static final String JASPER = "jasper";
        /** The {@code default_editor} values besides a known editor's id. */
        static final Set<String> CHOICES = Set.of(JASPER, "system", "custom");
        static final String DEFAULT_EDITOR_LABEL = "Default editor";
        static final String DEFAULT_EDITOR_DESCRIPTION = "Opens the files Jasper opens, such as config.toml, and is EDITOR and VISUAL "
            + "in Jasper's shells, so git commit uses it.";
        static final String COMMAND_LABEL = "Custom command";
        static final String COMMAND_DESCRIPTION = "For Custom command: the command line, run by the shell; the file's path is added at the end.";

        /**
         * Declares the settings; call while the plugin starts.
         *
         * @param settings the plugin's settings
         * @param installed the installed font families; the default font is the first of {@link #PREFERRED_FONTS} installed, else Monospaced
         */
        public Declared(PluginSettings settings, Set<String> installed) {
            String fallback = PREFERRED_FONTS.stream().filter(installed::contains).findFirst().orElse("Monospaced");
            fontFamily = settings.declare(SettingSpec.of("font_family",
                    SettingKind.text(family -> family.equals(fallback) || installed.contains(family), "No installed font family has that name."), fallback)
                .label("Font").description("An installed font family.").editor(EditorHint.font(true)).section("Font").keywords("editor", "font"));
            fontSize = settings.declare(SettingSpec.of("font_size", SettingKind.integer(8, 48), 13)
                .label("Font size").description("Points.").section("Font").keywords("editor", "font"));
            tabSize = settings.declare(SettingSpec.of("tab_size", SettingKind.integer(1, 16), 4)
                .label("Tab size").description("Columns per tab stop.").section("Editing").keywords("editor", "indent"));
            softWrap = settings.declare(SettingSpec.of("soft_wrap", SettingKind.bool(), false)
                .label("Soft wrap").description("Wrap long lines in tabs opened from now on.").section("Editing").keywords("editor", "wrap"));
            vim = settings.declare(SettingSpec.of("vim", SettingKind.bool(), false)
                .label("Vim mode").description("Modal editing as in IdeaVim; the Vim status item and Toggle Vim Mode change it. "
                    + "Its vimrc is the file vimrc in the Editor's data folder (plugins/dev.jasper.editor/data).")
                .section("Editing").keywords("editor", "vim", "modal"));
            maxFileMib = settings.declare(SettingSpec.of("max_file_mib", SettingKind.integer(1, 256), 10)
                .label("Largest file (MiB)").description("Larger files are refused.").section("Files").keywords("editor", "size"));
            recentFilesDays = settings.declare(SettingSpec.of("recent_files_days", SettingKind.integer(0, 365), 30)
                .label("Keep recent files for (days)").description("Recent files not opened for this many days are forgotten; 0 keeps no recent files.")
                .section("Files").keywords("editor", "recent", "history"));
            defaultEditor = settings.declare(SettingSpec.of("default_editor",
                    SettingKind.text(value -> CHOICES.contains(value) || KnownEditors.byId(value).isPresent(),
                        "Use jasper, system, custom or an editor id such as nvim or code."), JASPER)
                .label(DEFAULT_EDITOR_LABEL).description(DEFAULT_EDITOR_DESCRIPTION).section("Default editor")
                .keywords("editor", "default", "EDITOR", "VISUAL", "git"));
            customCommand = settings.declare(SettingSpec.of("custom_command", SettingKind.text(), "")
                .label(COMMAND_LABEL).description(COMMAND_DESCRIPTION).section("Default editor").keywords("editor", "custom", "command"));
            customInTerminal = settings.declare(SettingSpec.of("custom_in_terminal", SettingKind.bool(), true)
                .label("Run in a terminal tab").description("For Custom command: run it in a new terminal tab; off starts it on its own.")
                .section("Default editor").keywords("editor", "custom", "terminal"));
            keepEnvironmentEditor = settings.declare(SettingSpec.of("keep_environment_editor", SettingKind.bool(), true)
                .label("Keep EDITOR and VISUAL from Jasper's environment")
                .description("When Jasper was started with EDITOR or VISUAL set, its shells keep both and the default editor sets neither.")
                .section("Default editor").keywords("editor", "EDITOR", "VISUAL", "environment"));
        }

        /** @return the current values */
        public EditorSettings current() {
            return new EditorSettings(fontFamily.get(), fontSize.get(), tabSize.get(), softWrap.get(), maxFileMib.get(), vim.get());
        }

        /** @return how many days the Recent files list keeps a file it has not seen opened, 0 to 365; 0 keeps none */
        public int recentFilesDays() { return recentFilesDays.get(); }

        /** @return {@code default_editor}: jasper, system, custom or a known editor's id */
        public String defaultEditor() { return defaultEditor.get(); }

        /** @return {@code custom_command}, as written */
        public String customCommand() { return customCommand.get(); }

        /** @return {@code custom_in_terminal}: whether the custom command runs in a terminal tab */
        public boolean customInTerminal() { return customInTerminal.get(); }

        /** @return {@code keep_environment_editor}: whether an EDITOR or VISUAL in Jasper's environment keeps both */
        public boolean keepEnvironmentEditor() { return keepEnvironmentEditor.get(); }

        /**
         * Saves Vim mode to the settings file.
         *
         * @param on the new value
         * @return completes when the file is written, exceptionally when it could not be
         */
        public CompletableFuture<Void> saveVim(boolean on) { return vim.set(on); }
    }
}
