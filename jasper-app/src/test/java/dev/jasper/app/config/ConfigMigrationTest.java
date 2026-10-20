package dev.jasper.app.config;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigMigrationTest {
    private static final Path FILE = Path.of("/fixture/config.toml");
    private static ConfigLoader.Result parse(String text) { return ConfigLoader.parse(FILE, text, true); }
    private static ConfigMigration.Outcome migrate(String text) {
        return ConfigMigration.plan(text, parse(text), id -> id.equals("dev.jasper.history")).orElseThrow();
    }

    static final String VERSION_ONE = """
        # My Jasper settings
        [window]
        tab_height = 40 # roomy

        [font]
        # The font I like
        family = "Iosevka"
        size = 15.0 # points

        [terminal]
        scrollback = 5000

        [background]
        enabled = true

        [palette.scopes.history]
        enabled = false

        [plugins."dev.jasper.history"]
        trivial_commands = ["ls"]

        [plugins."dev.jasper.gone"]
        x = 1
        """;

    static final String VERSION_TWO = """
        # My Jasper settings
        version = 2
        [window]
        tab_height = 40 # roomy

        [terminal.font]
        # The font I like
        family = "Iosevka"
        size = 15.0 # points

        [terminal]
        scrollback = 5000

        [plugins."dev.jasper.gone"]
        x = 1

        [system]
        run_in_background = true
        """;

    @Test void aVersionOneFileBecomesTheGoldenVersionTwoFile() {
        var outcome = migrate(VERSION_ONE);
        assertThat(outcome.text()).isEqualTo(VERSION_TWO);
        assertThat(outcome.changes()).isEqualTo(5);
        var migrated = parse(outcome.text());
        assertThat(migrated.legacyKeys()).isEmpty();
        assertThat(migrated.snapshot().font().family()).isEqualTo("Iosevka");
        assertThat(migrated.snapshot().fontSize()).isEqualTo(15f);
        assertThat(migrated.snapshot().backgroundEnabled()).isTrue();
        assertThat(migrated.deadTables()).containsExactly(List.of("plugins", "dev.jasper.gone"));
        assertThat(ConfigMigration.plan(outcome.text(), migrated, id -> id.equals("dev.jasper.history"))).as("idempotent").isEmpty();
    }

    @Test void keysMoveOneByOneWhenTheirTableCannotBeRenamed() {
        var outcome = migrate("[terminal.font]\nfamily = 'A'\n[font]\nsize = 14\nfamily = 'B'\n");
        assertThat(outcome.text()).isEqualTo("version = 2\n[terminal.font]\nfamily = 'A'\nsize = 14\n");
        assertThat(outcome.changes()).isEqualTo(2);
        assertThat(migrate("font.size = 14\n").text()).isEqualTo("version = 2\n[terminal.font]\nsize = 14\n");
    }

    @Test void nothingToMigrateWritesNothingAndARejectedFileIsRefused() {
        String current = "[window]\ntab_height = 40\n";
        assertThat(ConfigMigration.plan(current, parse(current), id -> true)).isEmpty();
        String keptPlugin = "[plugins.\"dev.jasper.gone\"]\nx = 1\n";
        assertThat(ConfigMigration.plan(keptPlugin, parse(keptPlugin), id -> false)).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> ConfigMigration.plan("[font]\nsize = 'x'\n", parse("[font]\nsize = 'x'\n"), id -> true))
            .withMessage("Fix config.toml's errors before migrating.");
    }
}
