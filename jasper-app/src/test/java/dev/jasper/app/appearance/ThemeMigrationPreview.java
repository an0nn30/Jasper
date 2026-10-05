package dev.jasper.app.appearance;

import java.nio.file.Path;

/** Opt-in real app baseline families. Every destination is a mutable build output, never a golden. */
public final class ThemeMigrationPreview {
    private ThemeMigrationPreview() { }
    public static void main(String[] args) throws Exception {
        Path output = Path.of("build/theme-migration-evidence");
        Path home = Path.of("build/theme-migration-home").toAbsolutePath();
        if (!Path.of(System.getProperty("user.home")).equals(home)
            || !Path.of(System.getenv("HISTFILE")).equals(home.resolve(".zsh_history"))
            || !Path.of(System.getenv("XDG_CONFIG_HOME")).equals(home.resolve(".config"))
            || !Path.of(System.getenv("APPDATA")).equals(home.resolve("AppData/Roaming")))
            throw new IllegalStateException("Synthetic history/config roots required before plugin startup");
        java.nio.file.Files.createDirectories(home);
        java.nio.file.Files.writeString(home.resolve(".zsh_history"), ": 1700000000:0;printf 'fixture history'\n");
        dev.jasper.app.settingsui.SettingsDialogPreview.main(new String[]{output.resolve("settings").toString(), "--migration"});
        dev.jasper.app.workspace.ThemeMigrationWorkspacePreview.main(new String[0]);
        dev.jasper.app.workspace.HostedContentPreview.main(new String[]{output.resolve("hosted-content").toString()});
        dev.jasper.app.workspace.TitleBarPreview.main(new String[0]);
        dev.jasper.app.shortcuthelp.ShortcutHelpPreview.main(new String[]{output.resolve("shortcut-help").toString()});
    }
}
