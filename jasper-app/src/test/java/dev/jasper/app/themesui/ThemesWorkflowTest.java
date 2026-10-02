package dev.jasper.app.themesui;

import dev.jasper.app.appearance.*;
import dev.jasper.app.config.*;
import dev.jasper.app.settingsui.*;
import dev.jasper.app.themeimport.ThemeFiles;
import dev.jasper.app.testsupport.ConfigPollWorker;
import java.awt.Color;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.*;

class ThemesWorkflowTest {
    @TempDir Path home;
    @Test void inheritedCopyReloadsPreviewAndLiveValuesAcrossMalformedRepairWithoutRewritingChoices() throws Exception {
        var effective=new GeneratedTheme[1];edt(()->effective[0]=ThemeManager.generated(Theme.DARK));
        Path copy=new ThemeFiles(home).duplicate(Theme.DARK,effective[0],"Mine");
        assertThat(Files.readString(copy)).contains("# [tokens]");
        Path config=home.resolve("config.toml");String saved="ui.theme.workspace='Mine'\n";Files.writeString(config,saved);
        var catalog=ThemeCatalog.beside(config);catalog.scan();var worker=new ConfigPollWorker();
        var service=new ConfigService(config,false,worker,SwingUtilities::invokeLater);
        service.watchChanges(()->{var before=catalog.latest();return !catalog.scan().equals(before);}).get(5,TimeUnit.SECONDS);
        var page=new ThemesPage[1];var controller=new ThemeController[1];
        var draft=new SettingsDraft(service.initialState().values());
        try {
            edt(()->{
                controller[0]=new ThemeController(t->true);
                var context=new PageContext(draft,s->{},Runnable::run,(s,p)->Optional.empty(),()->{});
                page[0]=new ThemesPage(context,catalog::latest,()->ConfigSnapshot.defaults().viewOptions(12,Theme.DARK.palette()),ThemePageFixture.actions(context));page[0].component();
                draft.stage(CoreSettings.UI_THEME_NAME,Optional.of("Jasper Light"));
            });
            service.start(state->{controller[0].configure(Theme.DARK,new WorkspaceThemeChoice("Mine"),catalog.latest().find("Mine").orElse(null),UiFontConfig.defaults());page[0].refresh();});
            String original=Files.readString(copy);Files.writeString(copy,original+"\n[tokens]\neditor_background='#123456'\n");worker.poll();edt(()->{});
            edt(()->{assertThat(page[0].preview.themes.workspace().tokens().get(Token.EDITOR_BACKGROUND)).isEqualTo(new Color(0x123456));assertThat(controller[0].current().workspace().tokens().get(Token.EDITOR_BACKGROUND)).isEqualTo(new Color(0x123456));});
            Files.writeString(copy,"broken = [");worker.poll();edt(()->{});
            edt(()->{assertThat(page[0].diagnostics.getText()).contains("mine.toml");assertThat(page[0].preview.themes.matching()).isTrue();assertThat(draft.value(CoreSettings.UI_THEME_WORKSPACE)).isEqualTo("Mine");});
            Files.writeString(copy,original);worker.poll();edt(()->{});
            edt(()->{assertThat(page[0].diagnostics.getText()).isEmpty();assertThat(draft.value(CoreSettings.UI_THEME_NAME)).isEqualTo("Jasper Light");page[0].dispose();});
            assertThat(Files.readString(config)).isEqualTo(saved);assertThat(copy).exists();
        } finally {service.close();edt(()->{if(page[0]!=null)page[0].dispose();});}
    }
}
