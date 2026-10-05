package dev.jasper.app.workspace;

import dev.jasper.app.appearance.*;
import dev.jasper.ui.theme.*;
import dev.jasper.ui.themeio.*;
import dev.jasper.app.config.*;
import dev.jasper.app.config.TerminalColors;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class WindowThemeSelectionTest {
    @TempDir Path home;
    @AfterEach void close() throws Exception {closeOwners();}
    @Test void literalThemeSelectionsSaveAndUseTheConnectedCatalog() throws Exception {
        Path directory=Files.createDirectories(home.resolve("themes"));var catalog=ThemeCatalog.beside(home.resolve("config.toml"));
        var owners=new WindowContent[1];var requests=new ArrayList<SettingsEdit>();
        edt(()->{owners[0]=content(launcher(new ArrayDeque<>()),new ThemeController(t->true));owners[0].connectThemeCatalog(catalog::latest);owners[0].setActive(true);
            owners[0].connectSettings(edit->{requests.add(edit);return CompletableFuture.completedFuture(null);},s->{});
            var parsed=ConfigLoader.parse(home.resolve("config.toml"),"",true);owners[0].setConfigurationState(new ConfigService.State(parsed.snapshot(),List.of(),home.resolve("config.toml"),true,parsed.legacyKeys(),parsed.deadTables(),parsed.values(),false));
        });
        Path file=directory.resolve("light.toml");Files.writeString(file,"name='Light'\nextends='Jasper Dark'\n");catalog.scan();
        edt(()->{owners[0].chooseWorkspaceTheme("Light");
            assertThat(requests).hasSize(1);assertThat(requests.getFirst().changes().get(CoreSettings.UI_THEME_WORKSPACE)).isEqualTo(Optional.of("Light"));});
        Files.delete(file);catalog.scan();
        edt(()->assertThat(owners[0].availableThemes().find("Light")).isEmpty());
        Files.writeString(file,"name='Light'\nextends='Jasper Dark'\n");catalog.scan();
        edt(()->{owners[0].chooseUiTheme("Light");assertThat(requests.getLast().changes().get(CoreSettings.UI_THEME_NAME)).isEqualTo(Optional.of("Light"));owners[0].disconnectConfiguration();assertThat(owners[0].availableThemes()).isEqualTo(ThemeCatalog.builtIn().latest());});
    }
}
