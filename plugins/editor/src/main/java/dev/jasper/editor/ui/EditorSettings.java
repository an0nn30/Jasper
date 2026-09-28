package dev.jasper.editor.ui;

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
        private final Setting<Integer> fontSize, tabSize, maxFileMib;
        private final Setting<Boolean> softWrap, vim;

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
        }

        /** @return the current values */
        public EditorSettings current() {
            return new EditorSettings(fontFamily.get(), fontSize.get(), tabSize.get(), softWrap.get(), maxFileMib.get(), vim.get());
        }

        /**
         * Saves Vim mode to the settings file.
         *
         * @param on the new value
         * @return completes when the file is written, exceptionally when it could not be
         */
        public CompletableFuture<Void> saveVim(boolean on) { return vim.set(on); }
    }
}
