package dev.jasper.app.workspace;

import dev.jasper.app.appearance.*;
import dev.jasper.app.config.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;
import javax.swing.event.MenuEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class WindowThemeMenuTest {
    @TempDir Path home;
    @AfterEach void close() throws Exception {closeOwners();}
    private static JMenu menu(JMenuBar bar,String text){for(int i=0;i<bar.getMenuCount();i++)if(bar.getMenu(i).getText().equals(text))return bar.getMenu(i);throw new AssertionError(text);}
    private static JMenu child(JMenu parent,String text){for(var c:parent.getMenuComponents())if(c instanceof JMenu m&&m.getText().equals(text))return m;throw new AssertionError(text);}
    private static void open(JMenu menu){for(var listener:menu.getMenuListeners())listener.menuSelected(new MenuEvent(menu));}
    static List<JMenuItem> leaves(JMenu menu){var out=new ArrayList<JMenuItem>();for(var c:menu.getMenuComponents()){if(c instanceof JMenu sub){assertThat(sub.getItemCount()).isLessThanOrEqualTo(30);out.addAll(leaves(sub));}else if(c instanceof JRadioButtonMenuItem item)out.add(item);}return out;}
    static JMenu workspace(WindowContent owner) { var view=menu(owner.menuBar(),"View"); open(view); return child(child(view,"Appearance"),"Workspace Scheme"); }
    static JMenuItem item(JMenu menu,String name) { open(menu);return leaves(menu).stream().filter(i->i.getText().equals(name)).findFirst().orElseThrow(); }
    @Test void catalogsAreCompleteGroupedAndLiteralNamesBypassLegacyAliases() throws Exception {
        Path directory=Files.createDirectories(home.resolve("themes"));var catalog=ThemeCatalog.beside(home.resolve("config.toml"));
        var owners=new WindowContent[1];var requests=new ArrayList<SettingsEdit>();
        edt(()->{owners[0]=content(launcher(new ArrayDeque<>()),new ThemeController(t->true));owners[0].connectThemeCatalog(catalog::latest);owners[0].setActive(true);
            owners[0].connectSettings(edit->{requests.add(edit);return CompletableFuture.completedFuture(null);},s->{});
            var parsed=ConfigLoader.parse(home.resolve("config.toml"),"",true);owners[0].setConfigurationState(new ConfigService.State(parsed.snapshot(),List.of(),home.resolve("config.toml"),true,parsed.legacyKeys(),parsed.deadTables(),parsed.values(),false));
            var view=menu(owners[0].menuBar(),"View");open(view);
            assertThat(leaves(child(child(view,"Appearance"),"UI Theme"))).extracting(JMenuItem::getText).containsExactlyInAnyOrderElementsOf(catalog.latest().names());
            assertThat(leaves(child(child(view,"Appearance"),"Workspace Scheme"))).hasSize(catalog.latest().themes().size()+1);
        });
        Path file=directory.resolve("light.toml");Files.writeString(file,"name='Light'\nextends='Jasper Dark'\n");catalog.scan();
        edt(()->{var view=menu(owners[0].menuBar(),"View");open(view);leaves(child(child(view,"Appearance"),"Workspace Scheme")).stream().filter(i->i.getText().equals("Light")).findFirst().orElseThrow().doClick();
            assertThat(requests).hasSize(1);assertThat(requests.getFirst().changes().get(CoreSettings.UI_THEME_WORKSPACE)).isEqualTo(Optional.of("Light"));});
        Files.delete(file);catalog.scan();edt(()->{var view=menu(owners[0].menuBar(),"View");open(view);assertThat(leaves(child(child(view,"Appearance"),"Workspace Scheme"))).extracting(JMenuItem::getText).doesNotContain("Light");});
        Files.writeString(file,"name='Light'\nextends='Jasper Dark'\n");catalog.scan();
        edt(()->{owners[0].chooseUiTheme("Light");assertThat(requests.getLast().changes().get(CoreSettings.UI_THEME_NAME)).isEqualTo(Optional.of("Light"));owners[0].disconnectConfiguration();assertThat(owners[0].availableThemes()).isEqualTo(ThemeCatalog.builtIn().latest());});
    }
}
