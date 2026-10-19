package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.presentation.WorkspaceLook;
import dev.jasper.app.testsupport.EdtTestExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Spec 3.3 and 10.7: the workspace factory reads the dock's two points once, into one value. */
@ExtendWith(EdtTestExtension.class)
class DockChromeTest {
    /** The default window chrome with the classic dock: the combination the dock tests build windows with. */
    static final WorkspaceLook CLASSIC_DOCK = new WorkspaceLook(WorkspaceLook.Menus.STANDARD, WorkspaceLook.Header.UNIFIED,
        WorkspaceLook.Stripes.LABELLED, WorkspaceLook.Tabs.EQUAL, WorkspaceLook.Status.SLASH, false, WorkspaceLook.FrameStyle.FLUSH,
        WorkspaceLook.Icons.NEW_UI);

    /** A window chrome with the classic dock and the default window chrome. */
    static WorkspaceChrome classicDock() { return WorkspaceChrome.of(CLASSIC_DOCK, new dev.jasper.app.presentation.TitleFormat("{context}")); }

    @Test void theWorkspaceFactoryReadsTheDockGroupOnce() {
        assertThat(WorkspaceChrome.STANDARD.dock()).isEqualTo(DockChrome.STANDARD);
        assertThat(DockChrome.STANDARD).isEqualTo(new DockChrome(false, false));
        assertThat(WorkspaceChrome.of(CLASSIC_DOCK, new dev.jasper.app.presentation.TitleFormat("{context}")).dock()).isEqualTo(new DockChrome(true, true));
    }
}
