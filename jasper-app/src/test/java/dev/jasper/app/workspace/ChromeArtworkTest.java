package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.presentation.TitleFormat;
import dev.jasper.app.presentation.WorkspaceLook;
import dev.jasper.ui.icons.ChromeArtwork;
import java.util.ArrayDeque;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Plan 12 Rulings 6: the look's icon set reaches each window's header and status bar, and only through WorkspaceChrome. */
class ChromeArtworkTest {
    static WorkspaceLook look(WorkspaceLook.Icons icons) {
        return new WorkspaceLook(WorkspaceLook.Menus.CLASSIC, WorkspaceLook.Header.CLASSIC, WorkspaceLook.Stripes.RAILS,
            WorkspaceLook.Tabs.EQUAL, WorkspaceLook.Status.RULED, false, WorkspaceLook.FrameStyle.STANDARD, icons);
    }

    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    @Test void aClassicLookMarksItsHeaderAndStatusBarForTheClassicSet() throws Exception {
        DesktopTestSupport.edt(() -> {
            for (var icons : WorkspaceLook.Icons.values()) {
                var chrome = WorkspaceChrome.of(look(icons), new TitleFormat("{context}"));
                var expected = icons == WorkspaceLook.Icons.CLASSIC ? ChromeArtwork.CLASSIC : ChromeArtwork.NEW_UI;
                assertThat(chrome.chromeArtwork()).isEqualTo(expected);
                var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), ThemeTestSupport.controller(true), chrome);
                assertThat(AppIcons.chromeArtwork(owner.chrome().header())).contains(expected);
                assertThat(AppIcons.chromeArtwork(owner.chrome().status())).contains(expected);
                assertThat(AppIcons.chromeArtwork(owner.chrome().windowToolbar())).as("inside the header").contains(expected);
            }
            assertThat(WorkspaceChrome.of(look(WorkspaceLook.Icons.CLASSIC), new TitleFormat("{context}")))
                .isNotEqualTo(WorkspaceChrome.of(look(WorkspaceLook.Icons.NEW_UI), new TitleFormat("{context}")));
        });
    }
}
